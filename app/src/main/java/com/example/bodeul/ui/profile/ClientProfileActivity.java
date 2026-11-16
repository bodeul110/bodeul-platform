package com.example.bodeul.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.home.ClientHomeInsets;
import com.example.bodeul.ui.manager.ManagerActivity;
import com.example.bodeul.ui.navigation.*;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 인증된 역할별로 완성본 마이페이지를 표시하고 기존 상세 화면으로 연결한다. */
public class ClientProfileActivity extends AppCompatActivity {
    private AuthRepository auth;
    private ClientProfileBinder binder;
    private ClientProfileCoordinator coordinator;
    private ProfileActionRouter actions;
    private User user;
    private int generation;
    private BottomNavigationView navigation;

    protected boolean isManagerPage() { return false; }
    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }

    @Override protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_client_profile);
        auth = provideAuthRepository();
        binder = new ClientProfileBinder(findViewById(android.R.id.content));
        coordinator = new ClientProfileCoordinator(this);
        actions = new ProfileActionRouter(this);
        navigation = findViewById(isManagerPage() ? R.id.profileManagerBottomNavigation : R.id.clientBottomNavigation);
        findViewById(isManagerPage() ? R.id.clientBottomNavigation : R.id.profileManagerBottomNavigation).setVisibility(View.GONE);
        ClientHomeInsets.apply(findViewById(R.id.scrollClientProfile), findViewById(R.id.profileTopBar), navigation);
        if (isManagerPage()) {
            navigation.setSelectedItemId(R.id.navManagerProfile);
            navigation.setOnItemSelectedListener(item -> {
                if (user == null || item.getItemId() == R.id.navManagerProfile) return false;
                if (item.getItemId() == R.id.navManagerChat) startActivity(CompanionChatActivity.createIntent(this));
                else {
                    startActivity(ManagerActivity.createTabIntent(this, item.getItemId()));
                    finish();
                }
                return false;
            });
        } else {
            ClientBottomNavigationBinder.bind(navigation, ClientBottomNavigationTab.PROFILE,
                    tab -> { if (user != null) ClientBottomNavigationRouter.open(this, ClientBottomNavigationTab.PROFILE, tab); });
        }
        findViewById(R.id.buttonProfileSettings).setOnClickListener(view -> {
            if (user != null) startActivity(ProfileSettingsActivity.createIntent(this));
        });
        findViewById(R.id.buttonProfileNotifications).setOnClickListener(view -> actions.open(ProfileMenuItem.NOTIFICATIONS, user));
        findViewById(R.id.buttonProfileEdit).setOnClickListener(view -> actions.showAccount(user, true));
        findViewById(R.id.buttonProfileView).setOnClickListener(view -> actions.showAccount(user, false));
        findViewById(R.id.buttonProfileWithdraw).setOnClickListener(view -> actions.open(ProfileMenuItem.SETTLEMENT, user));
        findViewById(R.id.buttonProfileGeneral).setOnClickListener(view -> { if (isManagerPage()) actions.explainRole(user); });
        findViewById(R.id.buttonProfileManager).setOnClickListener(view -> { if (!isManagerPage()) actions.explainRole(user); });
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
        StatePanelHelper.hide(findViewById(R.id.clientProfileStatePanel));
        findViewById(R.id.progressClientProfile).setVisibility(View.VISIBLE);
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (attempt != generation) return;
                findViewById(R.id.progressClientProfile).setVisibility(View.GONE);
                if (result == null) { showAuth(); return; }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    startActivity(ProfileCompletionActivity.createIntent(ClientProfileActivity.this)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish(); return;
                }
                boolean allowed = ProfileAccessPolicy.canOpenHome(result.getRole(), isManagerPage());
                if (!allowed) { showPermission(); return; }
                user = result;
                binder.bind(coordinator.createScreenModel(result), isManagerPage(), item -> actions.open(item, user));
                findViewById(R.id.clientProfileContentContainer).setVisibility(View.VISIBLE);
                navigation.setVisibility(View.VISIBLE);
                setHeaderEnabled(true);
            }
            @Override public void onError(String message) {
                if (attempt != generation) return;
                findViewById(R.id.progressClientProfile).setVisibility(View.GONE);
                showAuth();
            }
        });
    }
    private void clearPrivateState() {
        user = null;
        binder.clear();
        findViewById(R.id.clientProfileContentContainer).setVisibility(View.GONE);
        navigation.setVisibility(View.GONE);
        setHeaderEnabled(false);
    }
    private void setHeaderEnabled(boolean enabled) {
        for (int id : new int[]{R.id.buttonProfileSettings, R.id.buttonProfileNotifications,
                R.id.buttonProfileGeneral, R.id.buttonProfileManager}) findViewById(id).setEnabled(enabled);
    }
    private void showAuth() {
        StatePanelHelper.show(findViewById(R.id.clientProfileStatePanel), StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth), getString(R.string.state_auth_title), getString(R.string.state_auth_body),
                getString(R.string.final_profile_retry), view -> reload(),
                getString(R.string.state_action_open_login), view -> openLogin());
    }
    private void showPermission() {
        StatePanelHelper.show(findViewById(R.id.clientProfileStatePanel), StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission), getString(R.string.state_permission_title, getString(R.string.client_profile_title)),
                getString(R.string.state_permission_body), getString(R.string.state_action_open_login), view -> openLogin(), null, null);
    }
    private void openLogin() {
        startActivity(new Intent(this, RoleSelectionActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }
}
