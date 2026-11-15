package com.example.bodeul.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
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

/** 설정 화면의 인증과 로그아웃을 연결한다. 역할·알림 구독을 임의로 변경하지 않는다. */
public class ProfileSettingsActivity extends AppCompatActivity {
    private AuthRepository auth;
    private ProfileActionRouter actions;
    private User user;
    private int generation;
    private LinearLayout menu;

    public static Intent createIntent(Context context) { return new Intent(context, ProfileSettingsActivity.class); }
    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_settings);
        auth = provideAuthRepository();
        actions = new ProfileActionRouter(this);
        menu = findViewById(R.id.profileSettingsMenu);
        View root = findViewById(R.id.profileSettingsRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
        findViewById(R.id.buttonProfileSettingsBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonProfileSettingsSignOut).setOnClickListener(view -> {
            if (user != null) actions.confirmSignOut(() -> {
                generation++;
                clearPrivateState();
                auth.signOut();
                openLogin();
            });
        });
        clearPrivateState();
    }
    @Override protected void onStart() { super.onStart(); reload(); }
    @Override protected void onStop() {
        generation++;
        actions.dismiss();
        clearPrivateState();
        super.onStop();
    }
    private void reload() {
        int attempt = ++generation;
        clearPrivateState();
        StatePanelHelper.hide(findViewById(R.id.profileSettingsState));
        findViewById(R.id.progressProfileSettings).setVisibility(View.VISIBLE);
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (attempt != generation) return;
                findViewById(R.id.progressProfileSettings).setVisibility(View.GONE);
                if (result == null || !ProfileAccessPolicy.canOpenSettings(result.getRole())) { showAuth(); return; }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    startActivity(ProfileCompletionActivity.createIntent(ProfileSettingsActivity.this)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish(); return;
                }
                user = result;
                for (ProfileMenuItem item : new ProfileMenuItem[]{ProfileMenuItem.PAYMENT, ProfileMenuItem.ACCOUNT, ProfileMenuItem.NOTIFICATIONS}) {
                    ProfileMenuBinder.add(menu, item, selected -> actions.open(selected, user));
                }
                findViewById(R.id.buttonProfileSettingsSignOut).setVisibility(View.VISIBLE);
            }
            @Override public void onError(String message) { if (attempt == generation) showAuth(); }
        });
    }
    private void clearPrivateState() {
        user = null;
        menu.removeAllViews();
        findViewById(R.id.progressProfileSettings).setVisibility(View.GONE);
        findViewById(R.id.buttonProfileSettingsSignOut).setVisibility(View.GONE);
    }
    private void showAuth() {
        findViewById(R.id.progressProfileSettings).setVisibility(View.GONE);
        StatePanelHelper.show(findViewById(R.id.profileSettingsState), StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth), getString(R.string.state_auth_title), getString(R.string.state_auth_body),
                getString(R.string.final_profile_retry), view -> reload(),
                getString(R.string.state_action_open_login), view -> openLogin());
    }
    private void openLogin() {
        startActivity(new Intent(this, RoleSelectionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
