package com.example.bodeul.ui.support;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.firebase.ClientSupportPushContract;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 역할별 진입점은 유지하고 입력·탐색과 공통 화면 연결만 담당한다. */
public abstract class SupportScreenActivity extends AppCompatActivity {
    private SupportViewModel model;
    private SupportScreenBinder binder;
    private SupportSource source;
    private AlertDialog dialog;
    private Toast noticeToast;
    private boolean receiverRegistered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            model.onPush(intent == null ? "" : intent.getStringExtra(ClientSupportPushContract.EXTRA_SUPPORT_REQUEST_ID));
        }
    };
    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected abstract SupportSource provideSupportSource();

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_support);
        source = provideSupportSource();
        model = new ViewModelProvider(this, new SupportViewModel.Factory(this, provideAuthRepository(), source,
                getIntent().getStringExtra("requestId"), getIntent().getStringExtra("supportRequestId"))).get(SupportViewModel.class);
        binder = new SupportScreenBinder(this, model);
        View root = findViewById(R.id.supportRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        findViewById(R.id.supportScroll).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> binder.resizeDetail());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { navigateBack(); }
        });
        findViewById(R.id.supportBack).setOnClickListener(view -> navigateBack());
        findViewById(R.id.supportFilterAll).setOnClickListener(view -> model.filter(SupportViewModel.Filter.ALL));
        findViewById(R.id.supportFilterWaiting).setOnClickListener(view -> model.filter(SupportViewModel.Filter.WAITING));
        findViewById(R.id.supportFilterAnswered).setOnClickListener(view -> model.filter(SupportViewModel.Filter.ANSWERED));
        findViewById(R.id.supportCategory).setOnClickListener(view -> chooseCategory());
        findViewById(R.id.supportAttachment).setOnClickListener(view -> showAttachmentNotice());
        findViewById(R.id.supportSubmit).setOnClickListener(view -> {
            hideKeyboard();
            if (model.mode() == SupportViewModel.Mode.LIST) model.compose(); else model.submit();
        });
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binder.isBinding()) return;
                model.setDraft(((EditText) findViewById(R.id.supportTitle)).getText().toString(),
                        ((EditText) findViewById(R.id.supportBody)).getText().toString());
                binder.bindButton();
            }
            @Override public void afterTextChanged(Editable value) { }
        };
        ((EditText) findViewById(R.id.supportTitle)).addTextChangedListener(watcher);
        ((EditText) findViewById(R.id.supportBody)).addTextChangedListener(watcher);
        model.changes.observe(this, ignored -> render());
    }
    @Override protected void onStart() {
        super.onStart();
        if (!source.isManager()) {
            ContextCompat.registerReceiver(this, receiver, new IntentFilter(ClientSupportPushContract.ACTION_CLIENT_SUPPORT_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        model.start();
    }
    @Override protected void onStop() {
        if (receiverRegistered) { unregisterReceiver(receiver); receiverRegistered = false; }
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (noticeToast != null) { noticeToast.cancel(); noticeToast = null; }
        model.stop();
        binder.clearPrivateViews();
        super.onStop();
    }
    private void render() {
        binder.bind();
        SupportViewModel.Gate gate = model.gate();
        if (gate != SupportViewModel.Gate.NONE) {
            boolean error = gate == SupportViewModel.Gate.ERROR;
            boolean permission = gate == SupportViewModel.Gate.PERMISSION;
            boolean profile = gate == SupportViewModel.Gate.PROFILE;
            StatePanelHelper.show(findViewById(R.id.supportState), StatePanelHelper.Tone.WARNING,
                    getString(error ? R.string.final_support_title : R.string.state_badge_auth),
                    getString(error ? R.string.final_support_read_error : profile ? R.string.profile_completion_title : R.string.state_auth_title),
                    getString(error ? R.string.final_support_read_error_body : profile ? R.string.profile_completion_subtitle : permission ? R.string.state_permission_body : R.string.state_auth_body),
                    getString(error ? R.string.final_support_retry : profile ? R.string.final_support_complete_profile : R.string.state_action_open_login),
                    view -> {
                        if (error) model.refresh();
                        else {
                            startActivity((gate == SupportViewModel.Gate.PROFILE ? ProfileCompletionActivity.createIntent(this)
                                    : new Intent(this, RoleSelectionActivity.class))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                            finish();
                        }
                    }, null, null);
        }
        if (model.isReady()) {
            int notice = model.consumeNotice();
            if (notice != 0) showNotice(notice);
        }
    }
    private void chooseCategory() {
        if (!model.isReady() || model.isSubmitting()) return;
        String[] keys = source.categories();
        String[] labels = new String[keys.length];
        int checked = -1;
        for (int i = 0; i < keys.length; i++) {
            labels[i] = getString(source.categoryLabel(keys[i]));
            if (keys[i].equals(model.category())) checked = i;
        }
        dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_support_category)
                .setSingleChoiceItems(labels, checked, (selected, index) -> {
                    model.selectCategory(keys[index]); selected.dismiss();
                }).setNegativeButton(R.string.final_support_cancel, null).show();
    }
    private void showAttachmentNotice() {
        dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_support_attachment)
                .setMessage(R.string.final_support_attachment_unavailable).setPositiveButton(R.string.final_support_confirm, null).show();
    }
    private void navigateBack() {
        if (model.isSubmitting()) { showNotice(R.string.final_support_sending); return; }
        if (model.mode() == SupportViewModel.Mode.LIST) { finish(); return; }
        if (model.mode() == SupportViewModel.Mode.COMPOSE && model.hasDraft()) {
            dialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_support_discard_title)
                    .setMessage(R.string.final_support_discard_body)
                    .setNegativeButton(R.string.final_support_continue, null)
                    .setPositiveButton(R.string.final_support_discard, (selected, which) -> { hideKeyboard(); model.list(true); }).show();
        } else { hideKeyboard(); model.list(false); }
    }
    private void hideKeyboard() {
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(findViewById(R.id.supportRoot));
        if (controller != null) controller.hide(WindowInsetsCompat.Type.ime());
        View focus = getCurrentFocus();
        if (focus != null) focus.clearFocus();
    }
    private void showNotice(int text) {
        if (noticeToast != null) noticeToast.cancel();
        noticeToast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        noticeToast.show();
    }
}
