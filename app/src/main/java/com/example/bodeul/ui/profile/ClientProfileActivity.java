package com.example.bodeul.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

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
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationInsets;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.ui.navigation.ClientBottomNavigationVisibility;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 환자와 보호자의 로그인 계정 정보를 표시하는 공통 내 정보 화면이다.
 */
public final class ClientProfileActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ClientProfileCoordinator coordinator;
    private ClientProfileBinder binder;
    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_profile);
        ClientBottomNavigationInsets.applyScrollableContent(findViewById(R.id.scrollClientProfile));

        authRepository = ServiceLocator.provideAuthRepository(this);
        coordinator = new ClientProfileCoordinator(this);
        statePanel = findViewById(R.id.clientProfileStatePanel);
        contentContainer = findViewById(R.id.clientProfileContentContainer);
        progressBar = findViewById(R.id.progressClientProfile);
        binder = new ClientProfileBinder(
                findViewById(R.id.textClientProfileTitle),
                findViewById(R.id.textClientProfileSubtitle),
                findViewById(R.id.textClientProfileHeroTitle),
                findViewById(R.id.textClientProfileHeroBody),
                findViewById(R.id.textClientProfileRole),
                findViewById(R.id.textClientProfileName),
                findViewById(R.id.textClientProfileEmail),
                findViewById(R.id.textClientProfilePhone)
        );

        findViewById(R.id.buttonClientProfileHealth).setOnClickListener(view ->
                startActivity(HealthInfoActivity.createIntent(this))
        );
        findViewById(R.id.buttonClientProfileSignOut).setOnClickListener(view -> signOut());
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        bottomNavigation.setVisibility(View.GONE);
        ClientBottomNavigationBinder.bind(
                bottomNavigation,
                ClientBottomNavigationTab.PROFILE,
                tab -> ClientBottomNavigationRouter.open(
                        this,
                        ClientBottomNavigationTab.PROFILE,
                        tab
                )
        );
        contentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reload();
    }

    private void reload() {
        setLoading(true);
        bottomNavigation.setVisibility(View.GONE);
        StatePanelHelper.hide(statePanel);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (!ClientBottomNavigationVisibility.isVisibleFor(result.getRole())) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                bottomNavigation.setVisibility(View.VISIBLE);
                setLoading(false);
                StatePanelHelper.hide(statePanel);
                binder.bind(coordinator.createScreenModel(result));
                contentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void signOut() {
        authRepository.signOut();
        openRoleSelection();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            contentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        StatePanelHelper.show(
                statePanel,
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.client_profile_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_home),
                view -> ClientBottomNavigationRouter.open(
                        this,
                        ClientBottomNavigationTab.PROFILE,
                        ClientBottomNavigationTab.HOME
                ),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void showAuthState() {
        StatePanelHelper.show(
                statePanel,
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_auth),
                getString(R.string.state_auth_title),
                getString(R.string.state_auth_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
        );
        contentContainer.setVisibility(View.GONE);
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
}
