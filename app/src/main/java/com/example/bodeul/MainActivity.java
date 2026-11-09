package com.example.bodeul;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.home.ClientHomeCoordinator;
import com.example.bodeul.ui.home.ClientHomeDashboard;
import com.example.bodeul.ui.home.ClientHomeDashboardBinder;
import com.example.bodeul.ui.home.ClientHomeInsets;
import com.example.bodeul.ui.home.ClientHomeNoticeProvider;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.ui.navigation.ClientBottomNavigationVisibility;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 환자와 보호자가 서비스 신청, 진행 현황, 안내 정보를 한곳에서 보는 메인 홈 화면이다.
 */
public class MainActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ClientHomeCoordinator clientHomeCoordinator;

    private View homeStatePanel;
    private View homeContentContainer;
    private ProgressBar progressHome;
    private TextView textHomeMode;
    private BottomNavigationView bottomNavigation;
    private ClientHomeDashboard currentDashboard;
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_client_home_figma);

        authRepository = provideAuthRepository();
        clientHomeCoordinator = new ClientHomeCoordinator(
                this,
                ServiceLocator.provideBookingRepository(this),
                ServiceLocator.provideGuardianReportRepository(this),
                ServiceLocator.provideClientSupportRepository(this),
                new ClientHomeNoticeProvider(),
                new AppointmentProgressComposer(this)
        );

        homeStatePanel = findViewById(R.id.homeStatePanel);
        homeContentContainer = findViewById(R.id.homeContentContainer);
        progressHome = findViewById(R.id.progressHome);
        textHomeMode = findViewById(R.id.textHomeMode);

        ClientHomeDashboardBinder dashboardBinder = new ClientHomeDashboardBinder(this, homeContentContainer);
        findViewById(R.id.buttonHomeHeroPrimary).setOnClickListener(view -> openBooking());
        findViewById(R.id.buttonHomePickup).setOnClickListener(view ->
                startActivity(BookingActivity.createHomeEntryIntent(this, BookingActivity.HomeEntry.LOCATION)));
        findViewById(R.id.buttonHomeDate).setOnClickListener(view ->
                startActivity(BookingActivity.createHomeEntryIntent(this, BookingActivity.HomeEntry.APPOINTMENT)));
        findViewById(R.id.buttonHomeTime).setOnClickListener(view -> openBooking());
        findViewById(R.id.buttonHomeProgressDetail).setOnClickListener(view -> openProgressAction());
        findViewById(R.id.cardActionSecondary).setOnClickListener(view -> openHealthInfo());
        findViewById(R.id.cardActionSupport).setOnClickListener(view -> openSupport());
        findViewById(R.id.buttonHomeSupport).setOnClickListener(view -> openSupport());
        findViewById(R.id.textHomeNoticeMore).setOnClickListener(view -> showServiceIntroduction());
        findViewById(R.id.buttonHomeSignOut).setOnClickListener(view -> signOut());
        dashboardBinder.setOnSupportBannerClickListener(view -> openSupport());
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        ClientHomeInsets.apply(
                findViewById(R.id.scrollClientHome),
                findViewById(R.id.layoutClientHomeTopBar),
                bottomNavigation
        );
        bottomNavigation.setVisibility(View.GONE);
        ClientBottomNavigationBinder.bind(
                bottomNavigation,
                ClientBottomNavigationTab.HOME,
                tab -> ClientBottomNavigationRouter.open(
                        this,
                        ClientBottomNavigationTab.HOME,
                        tab
                )
        );

        EnvironmentModeBadgeHelper.bind(
                textHomeMode,
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(
                        this,
                        clientHomeCoordinator.isFirebaseBacked()
                )
        );
        homeContentContainer.setTag(dashboardBinder);
        homeContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        reloadHome();
    }

    protected AuthRepository provideAuthRepository() {
        return ServiceLocator.provideAuthRepository(this);
    }

    protected void loadHomeDashboard(User user, RepositoryCallback<ClientHomeDashboard> callback) {
        clientHomeCoordinator.loadDashboard(user, callback);
    }

    @Override
    protected void onStop() {
        loadGeneration++;
        super.onStop();
    }

    private void reloadHome() {
        int generation = ++loadGeneration;
        currentDashboard = null;
        findViewById(R.id.textHomeSupportHeaderBadge).setVisibility(View.GONE);
        findViewById(R.id.buttonHomeSupport).setContentDescription(getString(R.string.final_home_notifications));
        setLoading(true);
        bottomNavigation.setVisibility(View.GONE);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (generation != loadGeneration) return;
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() == UserRole.MANAGER || result.getRole() == UserRole.ADMIN) {
                    redirectToRoleHome(result);
                    return;
                }
                if (!ClientBottomNavigationVisibility.isVisibleFor(result.getRole())) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                bottomNavigation.setVisibility(View.VISIBLE);
                loadDashboard(result, generation);
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadDashboard(User currentUser, int generation) {
        loadHomeDashboard(currentUser, new RepositoryCallback<ClientHomeDashboard>() {
            @Override
            public void onSuccess(ClientHomeDashboard result) {
                if (generation != loadGeneration) return;
                currentDashboard = result;
                setLoading(false);
                hideBlockingState();
                getDashboardBinder().bindDashboard(result);
                homeContentContainer.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                currentDashboard = null;
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private ClientHomeDashboardBinder getDashboardBinder() {
        return (ClientHomeDashboardBinder) homeContentContainer.getTag();
    }

    private void openPrimaryAction() {
        if (currentDashboard == null || !currentDashboard.hasRequests()) {
            openBooking();
            return;
        }
        openPrimaryRequestDetail();
    }

    private void openProgressAction() {
        if (currentDashboard != null && currentDashboard.isGuardianUser()) {
            openGuardianReport();
            return;
        }
        openPrimaryAction();
    }

    private void openBooking() {
        startActivity(new Intent(this, BookingActivity.class));
    }

    private void openPrimaryRequestDetail() {
        if (currentDashboard == null || currentDashboard.getPrimaryRequest() == null) {
            openBooking();
            return;
        }
        startActivity(BookingStatusActivity.createIntent(
                this,
                currentDashboard.getPrimaryRequest().getId()
        ));
    }

    private void openGuardianReport() {
        startActivity(new Intent(this, GuardianReportActivity.class));
    }

    private void openHealthInfo() {
        if (currentDashboard != null && currentDashboard.getPrimaryRequest() != null) {
            startActivity(HealthInfoActivity.createIntent(
                    this,
                    currentDashboard.getPrimaryRequest().getId()
            ));
            return;
        }
        startActivity(HealthInfoActivity.createIntent(this));
    }

    private void openSupport() {
        String requestId = null;
        if (currentDashboard != null && currentDashboard.getPrimaryRequest() != null) {
            requestId = currentDashboard.getPrimaryRequest().getId();
        }
        startActivity(ClientSupportActivity.createIntent(this, requestId));
    }

    private void showServiceIntroduction() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.client_home_notice_title)
                .setMessage(R.string.client_home_service_intro_dialog_body)
                .setPositiveButton(R.string.permission_guide_confirm, null)
                .show();
    }

    private void signOut() {
        authRepository.signOut();
        openRoleSelection();
    }

    private void redirectToRoleHome(User user) {
        Intent intent = AuthFlowRouter.createPostAuthIntent(this, user);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

    private void setLoading(boolean loading) {
        progressHome.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            homeContentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.home_title)),
                getString(R.string.state_permission_body),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection(),
                null,
                null
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
        if (message != null && !message.trim().isEmpty()) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.home_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reloadHome(),
                getString(R.string.state_action_open_login),
                view -> openRoleSelection()
        );
    }

    private void showBlockingState(
            StatePanelHelper.Tone tone,
            CharSequence badge,
            CharSequence title,
            CharSequence body,
            CharSequence primaryText,
            View.OnClickListener primaryListener,
            CharSequence secondaryText,
            View.OnClickListener secondaryListener
    ) {
        StatePanelHelper.show(
                homeStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        homeContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(homeStatePanel);
    }
}
