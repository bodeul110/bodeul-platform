package com.example.bodeul.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 상세 화면의 인증·수명주기 경계만 공유한다. 화면을 벗어나면 개인정보와 지연 응답을 비운다. */
public abstract class SettingsDetailActivity extends AppCompatActivity {
    protected AuthRepository auth;
    protected LinearLayout content;
    protected User user;
    protected AlertDialog dialog;
    private int generation;

    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected abstract int titleResource();
    protected abstract void showUser(User user, int attempt);
    protected void onPrivateStateCleared() {}

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings_detail);
        auth = provideAuthRepository();
        content = findViewById(R.id.settingsDetailContent);
        ((TextView) findViewById(R.id.settingsDetailTitle)).setText(titleResource());
        findViewById(R.id.buttonSettingsDetailBack).setOnClickListener(view -> finish());
        View root = findViewById(R.id.settingsDetailRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
    @Override protected void onStart() { super.onStart(); reload(); }
    @Override protected void onStop() {
        generation++;
        clearPrivateState();
        super.onStop();
    }
    protected final boolean isCurrent(int attempt) { return generation == attempt && !isFinishing() && !isDestroyed(); }
    protected final int currentAttempt() { return generation; }
    protected final void ready() { findViewById(R.id.settingsDetailProgress).setVisibility(View.GONE); }
    protected final void reload() {
        int attempt = ++generation;
        clearPrivateState();
        StatePanelHelper.hide(findViewById(R.id.settingsDetailState));
        findViewById(R.id.settingsDetailProgress).setVisibility(View.VISIBLE);
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (!isCurrent(attempt)) return;
                if (result == null || result.getId() == null || result.getId().trim().isEmpty()
                        || !ProfileAccessPolicy.canOpenSettings(result.getRole())) { showError(); return; }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    startActivity(ProfileCompletionActivity.createIntent(SettingsDetailActivity.this)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                    return;
                }
                user = result;
                showUser(result, attempt);
            }
            @Override public void onError(String message) { if (isCurrent(attempt)) showError(); }
        });
    }
    protected final void showError() {
        clearPrivateState();
        StatePanelHelper.show(findViewById(R.id.settingsDetailState), StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth), getString(R.string.final_settings_account_read_error),
                getString(R.string.state_auth_body), getString(R.string.final_profile_retry), view -> reload(),
                getString(R.string.state_action_open_login), view -> {
                    startActivity(new Intent(this, RoleSelectionActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                });
    }
    protected final void dismissDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }
    protected final void message(int title, int body) {
        dismissDialog();
        dialog = new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(body)
                .setPositiveButton(R.string.permission_guide_confirm, null).show();
    }
    private void clearPrivateState() {
        dismissDialog();
        user = null;
        content.removeAllViews();
        ready();
        onPrivateStateCleared();
    }
}
