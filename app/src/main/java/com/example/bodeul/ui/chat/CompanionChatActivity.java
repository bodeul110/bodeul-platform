package com.example.bodeul.ui.chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.CompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.realtime.SupabaseCompanionRealtimeSubscriber;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.firebase.CompanionChatPushContract;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.manager.ManagerGuideActivity;
import com.example.bodeul.util.DocumentPreviewLauncher;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.widget.PopupMenu;

import java.util.ArrayList;
import java.util.List;

public class CompanionChatActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";
    private static final int MAX_PENDING_ATTACHMENTS = 3;

    private CompanionChatViewModel viewModel;
    private CompanionChatBinder binder;
    private CompanionChatAttachmentPreviewResolver attachmentPreviewResolver;
    private CompanionChatAttachmentThumbnailLoader attachmentThumbnailLoader;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private EditText inputMessage;
    private CompanionChatScreenModel currentScreen;
    private String draftOwner = "";
    private Toast currentToast;
    private String requestId;
    private boolean chatRefreshReceiverRegistered;
    private final List<CompanionChatPendingAttachment> pendingAttachments = new ArrayList<>();

    private ActivityResultLauncher<String[]> attachmentPickerLauncher;

    private final BroadcastReceiver chatRefreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!shouldHandleChatRefresh(intent)) {
                return;
            }
            viewModel.reload();
            showChatMessageSnackbar(intent);
        }
    };

    public static Intent createIntent(Context context) {
        return new Intent(context, CompanionChatActivity.class);
    }

    public static Intent createIntent(Context context, String requestId) {
        Intent intent = new Intent(context, CompanionChatActivity.class);
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_companion_chat);
        View root = findViewById(R.id.companionChatRoot);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            // 큰 글꼴과 IME가 함께 켜지면 대화·입력 공간을 우선 확보한다.
            boolean largeText = getResources().getConfiguration().fontScale > 1.3f;
            boolean compact = insets.isVisible(WindowInsetsCompat.Type.ime()) && largeText;
            findViewById(R.id.companionChatActions).setVisibility(compact ? View.GONE : View.VISIBLE);
            TextView title = findViewById(R.id.textCompanionChatTitle);
            title.setTextSize(largeText ? 16 : 22);
            int width = view.getWidth() > 0 ? view.getWidth() : getResources().getDisplayMetrics().widthPixels;
            int reserved = Math.round((compact ? 69 : 187) * getResources().getDisplayMetrics().density);
            int titleWidth = Math.max(1, width - bars.left - bars.right - reserved);
            if (title.getMaxWidth() != titleWidth) title.setMaxWidth(titleWidth);
            findViewById(R.id.textCompanionChatHeroBadge).setVisibility(currentScreen != null && !compact ? View.VISIBLE : View.GONE);
            findViewById(R.id.textCompanionChatAvatar).setVisibility(currentScreen != null && !compact ? View.VISIBLE : View.GONE);
            return insets;
        });
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightNavigationBars(true);
        ViewCompat.requestApplyInsets(root);

        requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);

        AuthRepository authRepository = provideAuthRepository();
        BookingRepository bookingRepository = provideBookingRepository();
        ManagerRepository managerRepository = provideManagerRepository();
        CompanionChatAttachmentUploader attachmentUploader =
                provideAttachmentUploader();
        attachmentPreviewResolver = provideAttachmentPreviewResolver();
        attachmentThumbnailLoader = new CompanionChatAttachmentThumbnailLoader(this);
        CompanionChatCoordinator coordinator = new CompanionChatCoordinator(this);

        CompanionChatViewModel.Factory factory = new CompanionChatViewModel.Factory(
                authRepository,
                bookingRepository,
                managerRepository,
                attachmentUploader,
                coordinator,
                provideRealtimeSubscriber()
        );
        viewModel = new ViewModelProvider(this, factory).get(CompanionChatViewModel.class);

        attachmentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                this::handleAttachmentsPicked
        );

        statePanel = findViewById(R.id.companionChatStatePanel);
        contentContainer = findViewById(R.id.companionChatContentContainer);
        progressBar = findViewById(R.id.progressCompanionChat);
        inputMessage = findViewById(R.id.inputCompanionChatMessage);

        binder = new CompanionChatBinder(
                this,
                getLayoutInflater(),
                root,
                this::bindPendingAttachmentThumbnail,
                this::bindMessageAttachmentThumbnail,
                this::openMessageAttachment
        );

        findViewById(R.id.buttonBackCompanionChat).setOnClickListener(view -> finish());
        findViewById(R.id.buttonCompanionChatSend).setOnClickListener(view -> sendMessage());
        findViewById(R.id.buttonCompanionChatAttach).setOnClickListener(view -> openAttachmentPicker());
        findViewById(R.id.buttonCompanionChatProgress).setOnClickListener(view -> openProgress());
        findViewById(R.id.buttonCompanionChatDetails).setOnClickListener(view -> openDetails());
        findViewById(R.id.buttonCompanionChatMore).setOnClickListener(this::openMenu);
        contentContainer.setVisibility(View.GONE);
        inputMessage.setSaveEnabled(false);
        restoreDraft(savedInstanceState);

        viewModel.getUiState().observe(this, this::handleUiState);
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                if (currentToast != null) currentToast.cancel();
                currentToast = Toast.makeText(this, message, Toast.LENGTH_SHORT);
                currentToast.show();
                viewModel.toastMessageHandled();
            }
        });
        viewModel.getMessageSentEvent().observe(this, ownerKey -> {
            if (ownerKey == null) {
                return;
            }
            if (draftOwner.equals(ownerKey)) {
                inputMessage.setText("");
                clearPendingAttachments();
            }
            viewModel.messageSentHandled();
        });

        refreshPendingAttachmentUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerChatRefreshReceiver();
        viewModel.start(requestId);
    }

    @Override
    protected void onStop() {
        unregisterChatRefreshReceiver();
        if (currentToast != null) currentToast.cancel();
        viewModel.stop();
        super.onStop();
    }

    private void handleUiState(CompanionChatViewModel.UiState state) {
        if (state == null) {
            return;
        }

        progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
        currentScreen = state.screenModel;
        boolean hasScreen = state.screenModel != null && state.statePanelType == CompanionChatViewModel.StatePanelType.NONE;
        findViewById(R.id.scrollCompanionChatState).setVisibility(hasScreen ? View.GONE : View.VISIBLE);
        findViewById(R.id.buttonCompanionChatProgress).setEnabled(hasScreen && !state.isLoading);
        findViewById(R.id.buttonCompanionChatDetails).setEnabled(hasScreen && !state.isLoading);
        boolean writable = hasScreen && state.screenModel.isWritable();
        inputMessage.setEnabled(writable && !state.isLoading);
        findViewById(R.id.buttonCompanionChatSend).setEnabled(writable && !state.isLoading);
        findViewById(R.id.buttonCompanionChatSend).setAlpha(writable && !state.isLoading ? 1f : .35f);
        findViewById(R.id.buttonCompanionChatAttach).setEnabled(writable && !state.isLoading);
        findViewById(R.id.layoutCompanionChatInput).setVisibility(writable ? View.VISIBLE : View.GONE);
        findViewById(R.id.textCompanionChatClosed).setVisibility(hasScreen && !writable ? View.VISIBLE : View.GONE);
        if (!hasScreen) {
            binder.clearScreen();
            inputMessage.clearFocus();
            WindowCompat.getInsetsController(getWindow(), inputMessage).hide(WindowInsetsCompat.Type.ime());
        }

        if (state.requireProfileCompletion) {
            openProfileCompletion();
            return;
        }

        if (state.statePanelType != CompanionChatViewModel.StatePanelType.NONE) {
            contentContainer.setVisibility(View.GONE);
            switch (state.statePanelType) {
                case PERMISSION:
                    showPermissionState();
                    break;
                case AUTH:
                    showAuthState();
                    break;
                case EMPTY:
                    showEmptyState();
                    break;
                case LOAD_ERROR:
                    showLoadErrorState(state.errorMessage);
                    break;
                default:
                    StatePanelHelper.hide(statePanel);
                    break;
            }
        } else {
            StatePanelHelper.hide(statePanel);
            if (state.screenModel != null) {
                if (!draftOwner.isEmpty() && !draftOwner.equals(state.screenModel.getOwnerKey())) {
                    inputMessage.setText("");
                    clearPendingAttachments();
                }
                draftOwner = state.screenModel.getOwnerKey();
                contentContainer.setVisibility(View.VISIBLE);
                ScrollView scroll = findViewById(R.id.scrollCompanionChat);
                boolean atEnd = !scroll.canScrollVertically(1);
                int oldY = scroll.getScrollY();
                binder.bindScreen(state.screenModel);
                ViewCompat.requestApplyInsets(findViewById(R.id.companionChatRoot));
                refreshPendingAttachmentUi();
                scroll.post(() -> { if (atEnd) scroll.fullScroll(View.FOCUS_DOWN); else scroll.scrollTo(0, oldY); });
            } else {
                contentContainer.setVisibility(View.GONE);
            }
        }
    }

    private void sendMessage() {
        String message = inputMessage.getText() == null ? "" : inputMessage.getText().toString();
        viewModel.sendMessage(message, new ArrayList<>(pendingAttachments));
    }

    private void openAttachmentPicker() {
        attachmentPickerLauncher.launch(new String[]{"application/pdf", "image/*"});
    }

    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected BookingRepository provideBookingRepository() { return ServiceLocator.provideBookingRepository(this); }
    protected ManagerRepository provideManagerRepository() { return ServiceLocator.provideManagerRepository(this); }
    protected CompanionChatAttachmentUploader provideAttachmentUploader() { return ServiceLocator.provideCompanionChatAttachmentUploader(this); }
    protected CompanionChatAttachmentPreviewResolver provideAttachmentPreviewResolver() { return ServiceLocator.provideCompanionChatAttachmentPreviewResolver(this); }
    protected CompanionRealtimeSubscriber provideRealtimeSubscriber() { return new SupabaseCompanionRealtimeSubscriber(this); }

    private void openProgress() {
        if (currentScreen == null) return;
        if (currentScreen.isManager()) startActivity(new Intent(this, ManagerGuideActivity.class));
        else startActivity(BookingStatusActivity.createIntent(this, currentScreen.getRequestId()));
    }

    private void openDetails() {
        if (currentScreen == null) return;
        if (currentScreen.isManager()) showSessionInfo();
        else startActivity(BookingStatusActivity.createIntent(this, currentScreen.getRequestId()));
    }

    private void openMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.final_chat_refresh);
        if (currentScreen != null) menu.getMenu().add(0, 2, 1, R.string.final_chat_info);
        menu.getMenu().add(0, 3, 2, R.string.final_chat_home);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) viewModel.reload();
            else if (item.getItemId() == 2) showSessionInfo();
            else openHome();
            return true;
        });
        menu.show();
    }

    private void showSessionInfo() {
        if (currentScreen == null) return;
        new MaterialAlertDialogBuilder(this).setTitle(currentScreen.getHeroTitle())
                .setMessage(currentScreen.getHeroBody()).setPositiveButton(android.R.string.ok, null).show();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("chat.draft", inputMessage.getText().toString());
        state.putString("chat.owner", draftOwner);
        ArrayList<Bundle> attachments = new ArrayList<>();
        for (CompanionChatPendingAttachment item : pendingAttachments) {
            Bundle value = new Bundle();
            value.putString("uri", item.getFileUri().toString());
            value.putString("name", item.getFileName());
            value.putString("type", item.getContentType());
            attachments.add(value);
        }
        state.putParcelableArrayList("chat.attachments", attachments);
    }

    private void restoreDraft(@Nullable Bundle state) {
        if (state == null) return;
        inputMessage.setText(state.getString("chat.draft", ""));
        draftOwner = state.getString("chat.owner", "");
        ArrayList<Bundle> values = state.getParcelableArrayList("chat.attachments");
        if (values != null) for (Bundle value : values) {
            String uri = value.getString("uri", "");
            if (!uri.isEmpty() && pendingAttachments.size() < MAX_PENDING_ATTACHMENTS)
                pendingAttachments.add(new CompanionChatPendingAttachment(Uri.parse(uri), value.getString("name", ""), value.getString("type", "")));
        }
    }

    private void handleAttachmentsPicked(@Nullable List<Uri> fileUris) {
        if (fileUris == null || fileUris.isEmpty()) {
            return;
        }

        int remainingCount = Math.max(MAX_PENDING_ATTACHMENTS - pendingAttachments.size(), 0);
        if (remainingCount <= 0) {
            Toast.makeText(this, R.string.companion_chat_attachment_max_count, Toast.LENGTH_SHORT).show();
            return;
        }

        int addedCount = 0;
        for (Uri fileUri : fileUris) {
            if (fileUri == null) {
                continue;
            }
            if (addedCount >= remainingCount) {
                break;
            }
            if (containsPendingAttachment(fileUri)) {
                continue;
            }

            persistAttachmentReadPermission(fileUri);
            String validationError = CompanionChatAttachmentUploadPolicy.validate(
                    getContentResolver(),
                    fileUri
            );
            if (!TextUtils.isEmpty(validationError)) {
                Toast.makeText(this, validationError, Toast.LENGTH_SHORT).show();
                continue;
            }

            String fileName = CompanionChatAttachmentUploadPolicy.resolveFileName(
                    getContentResolver(),
                    fileUri
            );
            String contentType = CompanionChatAttachmentUploadPolicy.resolveContentType(
                    getContentResolver(),
                    fileUri
            );
            pendingAttachments.add(new CompanionChatPendingAttachment(fileUri, fileName, contentType));
            addedCount += 1;
        }

        if (addedCount < fileUris.size() && pendingAttachments.size() >= MAX_PENDING_ATTACHMENTS) {
            Toast.makeText(this, R.string.companion_chat_attachment_max_count, Toast.LENGTH_SHORT).show();
        }
        refreshPendingAttachmentUi();
    }

    private boolean containsPendingAttachment(Uri fileUri) {
        String target = fileUri.toString();
        for (CompanionChatPendingAttachment pendingAttachment : pendingAttachments) {
            if (TextUtils.equals(target, pendingAttachment.getFileUri().toString())) {
                return true;
            }
        }
        return false;
    }

    private void persistAttachmentReadPermission(@Nullable Uri fileUri) {
        if (fileUri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 현재 세션에서만 읽을 수 있는 공급자 URI는 그대로 사용한다.
        }
    }

    private void refreshPendingAttachmentUi() {
        CompanionChatViewModel.UiState state = viewModel.getUiState().getValue();
        boolean actionsEnabled = state != null && !state.isLoading && state.screenModel != null
                && state.screenModel.isWritable();
        binder.bindPendingAttachments(pendingAttachments, actionsEnabled, new CompanionChatBinder.PendingAttachmentActionListener() {
            @Override
            public void onPreviewPendingAttachment(CompanionChatPendingAttachment pendingAttachment) {
                previewPendingAttachment(pendingAttachment);
            }

            @Override
            public void onRemovePendingAttachment(CompanionChatPendingAttachment pendingAttachment) {
                removePendingAttachment(pendingAttachment);
            }
        });
    }

    private void previewPendingAttachment(CompanionChatPendingAttachment pendingAttachment) {
        if (pendingAttachment == null) {
            return;
        }
        if (!DocumentPreviewLauncher.open(
                this,
                pendingAttachment.getFileUri(),
                pendingAttachment.getContentType()
        )) {
            Toast.makeText(
                    this,
                    R.string.companion_chat_attachment_preview_error,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void removePendingAttachment(CompanionChatPendingAttachment pendingAttachment) {
        if (pendingAttachment == null) {
            return;
        }
        String target = pendingAttachment.getFileUri().toString();
        for (int index = pendingAttachments.size() - 1; index >= 0; index--) {
            if (TextUtils.equals(target, pendingAttachments.get(index).getFileUri().toString())) {
                pendingAttachments.remove(index);
            }
        }
        refreshPendingAttachmentUi();
    }

    private void clearPendingAttachments() {
        pendingAttachments.clear();
        refreshPendingAttachmentUi();
    }

    private void bindPendingAttachmentThumbnail(
            ImageView imageView,
            CompanionChatPendingAttachment attachment
    ) {
        attachmentThumbnailLoader.loadInto(imageView, attachment.getFileUri());
    }

    private void bindMessageAttachmentThumbnail(
            ImageView imageView,
            CompanionChatAttachment attachment
    ) {
        if (!attachment.isImageType()) {
            attachmentThumbnailLoader.clear(imageView);
            return;
        }

        if (!TextUtils.isEmpty(attachment.getPreviewUri())
                && !attachment.getPreviewUri().startsWith("/api/companion-sessions/")) {
            attachmentThumbnailLoader.loadInto(imageView, Uri.parse(attachment.getPreviewUri()));
            return;
        }

        attachmentThumbnailLoader.clear(imageView);
        attachmentPreviewResolver.resolvePreviewUri(attachment, new RepositoryCallback<Uri>() {
            @Override
            public void onSuccess(Uri previewUri) {
                attachmentThumbnailLoader.loadInto(imageView, previewUri);
            }

            @Override
            public void onError(String message) {
                attachmentThumbnailLoader.clear(imageView);
            }
        });
    }

    private void openMessageAttachment(CompanionChatAttachment attachment) {
        attachmentPreviewResolver.resolvePreviewUri(attachment, new RepositoryCallback<Uri>() {
            @Override
            public void onSuccess(Uri previewUri) {
                if (!DocumentPreviewLauncher.open(
                        CompanionChatActivity.this,
                        previewUri,
                        attachment.getContentType()
                )) {
                    Toast.makeText(
                            CompanionChatActivity.this,
                            R.string.companion_chat_attachment_preview_error,
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(CompanionChatActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.companion_chat_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showAuthState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
    }

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.client_companion_room_empty_title),
                getString(R.string.client_companion_room_empty_body),
                getString(R.string.state_action_open_home),
                view -> openHome(),
                null,
                null
        );
    }

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.companion_chat_title)),
                body,
                getString(R.string.state_action_retry),
                view -> viewModel.reload(),
                getString(R.string.state_action_open_home),
                view -> openHome()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            @Nullable CharSequence primaryText,
            @Nullable View.OnClickListener primaryListener,
            @Nullable CharSequence secondaryText,
            @Nullable View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                statePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void openHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openProfileCompletion() {
        Intent intent = ProfileCompletionActivity.createIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void registerChatRefreshReceiver() {
        if (chatRefreshReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(CompanionChatPushContract.ACTION_COMPANION_CHAT_UPDATED);
        ContextCompat.registerReceiver(
                this,
                chatRefreshReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
        chatRefreshReceiverRegistered = true;
    }

    private void unregisterChatRefreshReceiver() {
        if (!chatRefreshReceiverRegistered) {
            return;
        }
        unregisterReceiver(chatRefreshReceiver);
        chatRefreshReceiverRegistered = false;
    }

    private boolean shouldHandleChatRefresh(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        String updatedRequestId = intent.getStringExtra(
                CompanionChatPushContract.EXTRA_APPOINTMENT_REQUEST_ID
        );
        String visibleRequest = currentScreen == null ? requestId : currentScreen.getRequestId();
        return TextUtils.isEmpty(visibleRequest)
                || TextUtils.isEmpty(updatedRequestId)
                || TextUtils.equals(visibleRequest, updatedRequestId);
    }

    private void showChatMessageSnackbar(@Nullable Intent intent) {
        String message = getString(R.string.companion_chat_push_body_fallback);
        // 갱신 후 인가가 실패해도 푸시 본문으로 이전 대화를 다시 노출하지 않는다.
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }
}
