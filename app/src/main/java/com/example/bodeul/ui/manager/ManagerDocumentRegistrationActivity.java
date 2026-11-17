package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerDocumentPreviewResolver;
import com.example.bodeul.data.ManagerDocumentStorageUploader;
import com.example.bodeul.data.ManagerDocumentUploadPolicy;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.DocumentPreviewLauncher;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 매니저 홈의 서류 등록 전용 화면에서 인증 서류 업로드와 검토 요청 흐름을 제어한다.
 */
public class ManagerDocumentRegistrationActivity extends AppCompatActivity
        implements ManagerDocumentRegistrationBinder.Listener {
    private static final String[] QUALIFICATION_IMAGE_MIME_TYPES = new String[]{
            "image/jpeg",
            "image/png",
            "image/webp"
    };
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerDocumentRegistrationCoordinator coordinator;
    private ManagerDocumentRegistrationBinder binder;
    private ManagerDocumentRegistrationViewModel viewModel;
    private ActivityResultLauncher<String[]> documentPickerLauncher;

    @Nullable
    private User currentUser;
    @Nullable
    private ManagerDocumentOverview currentOverview;

    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private View buttonBack;
    private MaterialButton buttonRequest;
    private final ManagerQualificationCompletionDialogGate completionDialogGate =
            new ManagerQualificationCompletionDialogGate();
    private boolean operationInFlight;
    private boolean screenLoading;
    private boolean screenReady;
    private int loadGeneration;
    @Nullable private ManagerDocumentRegistrationScreenModel screenModel;
    @Nullable private androidx.appcompat.app.AlertDialog typeSelector;
    private static final String EXTRA_MANAGEMENT = "manager_document_management";

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerDocumentRegistrationActivity.class);
    }

    public static Intent createManagementIntent(Context context) {
        return createIntent(context).putExtra(EXTRA_MANAGEMENT, true);
    }

    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected ManagerRepository provideManagerRepository() { return ServiceLocator.provideManagerRepository(this); }
    protected ManagerDocumentStorageUploader provideStorageUploader() { return ServiceLocator.provideManagerDocumentStorageUploader(this); }
    protected ManagerDocumentPreviewResolver providePreviewResolver() { return ServiceLocator.provideManagerDocumentPreviewResolver(this); }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manager_document_registration);
        View root = findViewById(R.id.managerDocumentRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        authRepository = provideAuthRepository();
        managerRepository = provideManagerRepository();
        ManagerDocumentStorageUploader storageUploader = provideStorageUploader();
        ManagerDocumentPreviewResolver previewResolver = providePreviewResolver();
        ManagerDocumentRegistrationViewModel.Factory factory =
                new ManagerDocumentRegistrationViewModel.Factory(
                        this,
                        managerRepository,
                        storageUploader,
                        previewResolver
                );
        viewModel = new ViewModelProvider(this, factory).get(
                ManagerDocumentRegistrationViewModel.class
        );
        getSupportFragmentManager().setFragmentResultListener(
                ManagerQualificationCompletionDialog.RESULT_KEY,
                this,
                (requestKey, result) -> {
                    completionDialogGate.clear();
                    viewModel.clearCompletionPending();
                }
        );
        coordinator = new ManagerDocumentRegistrationCoordinator(
                this,
                new ManagerHomePresentationFormatter(this)
        );
        documentPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::handleDocumentPicked
        );

        statePanel = findViewById(R.id.managerDocumentStatePanel);
        contentContainer = findViewById(R.id.managerDocumentContentContainer);
        progressBar = findViewById(R.id.progressManagerDocumentRegistration);

        buttonBack = findViewById(R.id.buttonBackManagerDocument);
        buttonRequest = findViewById(R.id.buttonManagerDocumentRequest);
        binder = new ManagerDocumentRegistrationBinder(
                root, this, getIntent().getBooleanExtra(EXTRA_MANAGEMENT, false)
        );

        buttonBack.setOnClickListener(view -> requestExit());
        findViewById(R.id.buttonManagerDocumentHistory).setOnClickListener(view -> {
            if (screenReady && !operationInFlight) startActivity(ManagerProfileActivity.createIntent(this));
        });
        buttonRequest.setOnClickListener(view -> submitRegistrationRequest());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                requestExit();
            }
        });
        viewModel.getOperationInFlight().observe(
                this,
                inFlight -> setOperationInFlight(Boolean.TRUE.equals(inFlight))
        );
        viewModel.getUiEvent().observe(this, this::handleUiState);
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleUiState(viewModel.getUiEvent().getValue());
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadScreen();
    }

    @Override protected void onStop() {
        if (typeSelector != null) { typeSelector.dismiss(); typeSelector = null; }
        loadGeneration++;
        screenReady = false;
        currentUser = null;
        currentOverview = null;
        screenModel = null;
        contentContainer.setVisibility(View.GONE);
        updateInteractions();
        super.onStop();
    }

    @Override
    public void onDocumentUploadRequested(@Nullable ManagerDocumentFileType fileType) {
        if (operationInFlight || !screenReady) {
            return;
        }
        if (currentUser == null) {
            showAuthState();
            return;
        }
        
        if (!ManagerDocumentUploadPolicy.isCanonicalQualificationType(fileType)) {
            showLicenseTypeSelector();
            return;
        }
        
        viewModel.setPendingDocumentSelection(currentUser.getId(), fileType);
        launchDocumentPicker(QUALIFICATION_IMAGE_MIME_TYPES);
    }

    @Override
    public void onDocumentPreviewRequested(@Nullable ManagerDocumentFileType fileType) {
        if (operationInFlight || !screenReady || currentUser == null) {
            return;
        }
        ManagerDocumentFileMetadata metadata = findDocumentMetadata(fileType);
        if (metadata == null) {
            Toast.makeText(
                    this,
                    R.string.manager_document_preview_missing,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (!viewModel.resolvePreview(currentUser.getId(), metadata)) {
            showOperationInProgressMessage();
        }
    }

    private void showLicenseTypeSelector() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        String managerUserId = currentUser.getId();
        String[] options = new String[]{
                getString(R.string.manager_document_registration_document_nursing_license),
                getString(R.string.manager_document_registration_document_elderly_care_license)
        };
        ManagerDocumentFileType[] types = new ManagerDocumentFileType[]{
                ManagerDocumentFileType.NURSING_LICENSE,
                ManagerDocumentFileType.LICENSE
        };

        if (typeSelector != null) return;
        typeSelector = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manager_document_registration_document_nursing_or_elderly_care_license)
                .setItems(options, (dialog, which) -> {
                    if (screenReady && currentUser != null && managerUserId.equals(currentUser.getId())) {
                        viewModel.setPendingDocumentSelection(managerUserId, types[which]);
                        launchDocumentPicker(QUALIFICATION_IMAGE_MIME_TYPES);
                    }
                })
                .create();
        typeSelector.setOnDismissListener(dialog -> typeSelector = null);
        typeSelector.show();
    }

    protected void launchDocumentPicker(String[] mimeTypes) { documentPickerLauncher.launch(mimeTypes); }

    protected void handleDocumentPicked(@Nullable Uri fileUri) {
        ManagerDocumentRegistrationViewModel.PendingDocumentSelection selection =
                viewModel.consumePendingDocumentSelection();
        if (fileUri == null || selection == null) {
            return;
        }

        String fileTypeError = ManagerDocumentUploadPolicy.validateFileType(
                selection.getFileType()
        );
        if (!TextUtils.isEmpty(fileTypeError)) {
            Toast.makeText(this, fileTypeError, Toast.LENGTH_SHORT).show();
            return;
        }

        // 외부 파일 선택기에서 돌아올 때는 선택 당시 계정이 여전히 매니저인지 확인한다.
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (!isActivityUsable()) return;
                if (user == null || user.getRole() != UserRole.MANAGER || AuthFlowRouter.requiresProfileCompletion(user)
                        || !selection.getManagerUserId().equals(user.getId())) {
                    Toast.makeText(ManagerDocumentRegistrationActivity.this, R.string.final_documents_identity_changed, Toast.LENGTH_SHORT).show();
                    loadScreen();
                    return;
                }
                persistDocumentReadPermission(fileUri);
                if (!viewModel.uploadDocument(selection.getManagerUserId(), selection.getFileType(), fileUri)) showOperationInProgressMessage();
            }
            @Override public void onError(String message) {
                if (isActivityUsable()) { setLoading(false); showAuthState(); }
            }
        });
    }

    @Nullable
    private ManagerDocumentFileMetadata findDocumentMetadata(@Nullable ManagerDocumentFileType fileType) {
        if (currentOverview == null || fileType == null) {
            return null;
        }
        return currentOverview.getProfile().getDocumentFile(fileType);
    }

    private void persistDocumentReadPermission(@Nullable Uri fileUri) {
        if (fileUri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    fileUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
        } catch (SecurityException | IllegalArgumentException ignored) {
            // 일부 공급자는 지속 권한을 주지 않아도 현재 세션 업로드는 가능하다.
        }
    }

    private void submitRegistrationRequest() {
        if (operationInFlight || !screenReady) {
            return;
        }
        if (currentUser == null || currentOverview == null) {
            showAuthState();
            return;
        }

        ManagerHomeProfile profile = currentOverview.getProfile();
        if (!coordinator.canRequestReview(profile)) {
            Toast.makeText(
                    this,
                    R.string.manager_document_registration_request_missing_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String summary = coordinator.buildRequestSummary(profile);
        if (TextUtils.isEmpty(summary)) {
            Toast.makeText(
                    this,
                    R.string.manager_document_registration_request_missing_required,
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!viewModel.submitRegistration(currentUser.getId(), summary)) {
            showOperationInProgressMessage();
        }
    }

    private void loadScreen() {
        int generation = ++loadGeneration;
        screenReady = false;
        currentUser = null;
        currentOverview = null;
        screenModel = null;
        contentContainer.setVisibility(View.GONE);
        setLoading(true);
        StatePanelHelper.hide(statePanel);
        findViewById(R.id.managerDocumentStateScroll).setVisibility(View.GONE);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isActivityUsable() || generation != loadGeneration) {
                    return;
                }
                if (result == null) { setLoading(false); showAuthState(); return; }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.MANAGER) {
                    currentUser = result;
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                currentUser = result;
                if (!viewModel.isOperationOwner(result.getId())) {
                    viewModel.clearCompletionPending();
                    androidx.fragment.app.Fragment completion = getSupportFragmentManager().findFragmentByTag(ManagerQualificationCompletionDialog.TAG);
                    if (completion instanceof ManagerQualificationCompletionDialog) {
                        ((ManagerQualificationCompletionDialog) completion).dismiss();
                        completionDialogGate.clear();
                    }
                }
                loadOverview();
            }

            @Override
            public void onError(String message) {
                if (!isActivityUsable() || generation != loadGeneration) {
                    return;
                }
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadOverview() {
        if (currentUser == null) {
            setLoading(false);
            showAuthState();
            return;
        }
        int generation = ++loadGeneration;
        String userId = currentUser.getId();
        setLoading(true);
        managerRepository.getManagerDocumentOverview(
                userId,
                new RepositoryCallback<ManagerDocumentOverview>() {
                    @Override
                    public void onSuccess(ManagerDocumentOverview result) {
                        if (!isActivityUsable() || generation != loadGeneration || currentUser == null || !userId.equals(currentUser.getId())) {
                            return;
                        }
                        if (result == null) { setLoading(false); showLoadErrorState(""); return; }
                        currentOverview = result;
                        screenReady = true;
                        viewModel.reconcileRecoveredSubmission(
                                currentUser.getId(),
                                result.getProfile()
                        );
                        setLoading(false);
                        hideBlockingState();
                        contentContainer.setVisibility(View.VISIBLE);
                        bindOverview();
                        handleUiState(viewModel.getUiEvent().getValue());
                    }

                    @Override
                    public void onError(String message) {
                        if (!isActivityUsable() || generation != loadGeneration) {
                            return;
                        }
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                }
        );
    }

    private void setLoading(boolean loading) {
        screenLoading = loading;
        updateProgressVisibility();
        updateInteractions();
    }

    private void setOperationInFlight(boolean inFlight) {
        operationInFlight = inFlight;
        updateProgressVisibility();
        buttonBack.setEnabled(!inFlight);
        buttonBack.setAlpha(inFlight ? 0.45f : 1f);
        updateInteractions();
    }

    private void updateInteractions() {
        binder.setInteractionsEnabled(screenReady && !screenLoading && !operationInFlight);
        findViewById(R.id.layoutManagerDocumentBottomAction).setVisibility(screenReady ? View.VISIBLE : View.GONE);
        androidx.fragment.app.Fragment sheet = getSupportFragmentManager().findFragmentByTag(ManagerDocumentDetailSheet.TAG);
        if (sheet instanceof ManagerDocumentDetailSheet) bindDocumentDetails((ManagerDocumentDetailSheet) sheet);
    }
    private void bindOverview() {
        if (currentOverview == null) return;
        screenModel = coordinator.createScreenModel(currentOverview, managerRepository.isFirebaseBacked());
        binder.bindScreen(screenModel);
        updateInteractions();
    }
    @Override public void onDocumentDetailsRequested() {
        if (!screenReady || screenLoading || operationInFlight || getSupportFragmentManager().isStateSaved()
                || getSupportFragmentManager().findFragmentByTag(ManagerDocumentDetailSheet.TAG) != null) return;
        new ManagerDocumentDetailSheet().showNow(getSupportFragmentManager(), ManagerDocumentDetailSheet.TAG);
    }
    void bindDocumentDetails(ManagerDocumentDetailSheet sheet) {
        sheet.bind(screenReady ? screenModel : null, screenReady && !screenLoading && !operationInFlight);
    }

    private void updateProgressVisibility() {
        progressBar.setVisibility(
                screenLoading || operationInFlight ? View.VISIBLE : View.GONE
        );
    }

    private void requestExit() {
        if (operationInFlight) {
            showOperationInProgressMessage();
            return;
        }
        finish();
    }

    private void showOperationInProgressMessage() {
        Toast.makeText(
                this,
                R.string.manager_document_registration_operation_in_progress,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void dispatchOperationEvent(
            @Nullable ManagerDocumentRegistrationViewModel.UiEvent event
    ) {
        if (event == null
                || !screenReady || currentUser == null
                || !isActivityUsable()
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            return;
        }
        if (!viewModel.isOperationOwner(currentUser.getId())) {
            event.consume();
            viewModel.clearCompletionPending();
            return;
        }
        ManagerDocumentRegistrationViewModel.UiEvent consumedEvent = event.consume();
        if (consumedEvent == null) {
            return;
        }

        if (consumedEvent.getType()
                == ManagerDocumentRegistrationViewModel.EventType.UPLOAD_SAVED) {
            applyLatestProfile(consumedEvent.getLatestProfile());
            ManagerDocumentFileType fileType = consumedEvent.getFileType();
            if (fileType != null) {
                Toast.makeText(
                        this,
                        getString(
                                R.string.manager_document_registration_upload_saved,
                                getDocumentTypeLabel(fileType)
                        ),
                        Toast.LENGTH_SHORT
                ).show();
            }
            loadOverview();
            return;
        }

        if (consumedEvent.getType()
                == ManagerDocumentRegistrationViewModel.EventType.PREVIEW_READY) {
            Uri previewUri = consumedEvent.getPreviewUri();
            if (previewUri == null || !openDocumentPreview(previewUri, consumedEvent.getContentType())) {
                Toast.makeText(
                        this,
                        R.string.manager_document_preview_open_failed,
                        Toast.LENGTH_SHORT
                ).show();
            }
            return;
        }

        if (consumedEvent.getType()
                == ManagerDocumentRegistrationViewModel.EventType.SUBMISSION_SUCCEEDED) {
            loadOverview();
            return;
        }

        if (!TextUtils.isEmpty(consumedEvent.getMessage())) {
            Toast.makeText(this, consumedEvent.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleUiState(
            @Nullable ManagerDocumentRegistrationViewModel.UiEvent event
    ) {
        dispatchOperationEvent(event);
        showCompletionDialogIfNeeded();
    }

    protected boolean openDocumentPreview(Uri uri, String contentType) {
        return DocumentPreviewLauncher.open(this, uri, contentType);
    }

    private void applyLatestProfile(@Nullable ManagerHomeProfile latestProfile) {
        if (latestProfile == null || currentOverview == null) {
            return;
        }
        currentOverview = new ManagerDocumentOverview(
                currentOverview.getManager(),
                latestProfile,
                currentOverview.getHistoryEntries()
        );
        bindOverview();
    }

    private void showCompletionDialogIfNeeded() {
        if (!isActivityUsable()
                || !screenReady || currentUser == null || !viewModel.isOperationOwner(currentUser.getId())
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)
                || getSupportFragmentManager().isStateSaved()) {
            return;
        }
        boolean dialogAlreadyAdded = getSupportFragmentManager().findFragmentByTag(
                ManagerQualificationCompletionDialog.TAG
        ) != null;
        if (!completionDialogGate.tryEnqueue(
                viewModel.isCompletionPending(),
                dialogAlreadyAdded
        )) {
            return;
        }
        new ManagerQualificationCompletionDialog().show(
                getSupportFragmentManager(),
                ManagerQualificationCompletionDialog.TAG
        );
    }

    private boolean isActivityUsable() {
        return !isFinishing() && !isDestroyed();
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(
                        R.string.state_permission_title,
                        getString(R.string.manager_document_registration_heading)
                ),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> openGeneralHome(),
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

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(
                        R.string.state_load_error_title,
                        getString(R.string.manager_document_registration_heading)
                ),
                body,
                getString(R.string.state_action_retry),
                view -> loadScreen(),
                getString(R.string.state_action_open_home),
                view -> openManagerHome()
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
        screenReady = false;
        currentOverview = null;
        screenModel = null;
        updateInteractions();
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
        findViewById(R.id.managerDocumentStateScroll).setVisibility(View.VISIBLE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(statePanel);
        findViewById(R.id.managerDocumentStateScroll).setVisibility(View.GONE);
        contentContainer.setVisibility(View.VISIBLE);
    }

    private void openGeneralHome() {
        startActivity(currentUser == null ? new Intent(this, MainActivity.class) : AuthFlowRouter.createPostAuthIntent(this, currentUser));
        finish();
    }

    private void openManagerHome() {
        Intent intent = new Intent(this, ManagerActivity.class);
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

    private String getDocumentTypeLabel(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return getString(R.string.manager_document_registration_document_id_card);
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return getString(R.string.manager_document_registration_document_elderly_care_license);
        }
        if (fileType == ManagerDocumentFileType.NURSING_LICENSE
                || fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return getString(R.string.manager_document_registration_document_nursing_license);
        }
        return getString(R.string.manager_document_registration_document_criminal_record);
    }
}
