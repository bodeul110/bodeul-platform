package com.example.bodeul.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 보호자 진행 화면의 인증, 로딩, 상세 이동만 담당한다.
 */
public class GuardianReportActivity extends AppCompatActivity implements GuardianReportEntryCardBinder.Listener {
    private AuthRepository authRepository;
    private GuardianReportRepository guardianReportRepository;
    private GuardianReportCoordinator guardianReportCoordinator;
    private GuardianReportDashboardBinder guardianReportDashboardBinder;

    private User currentUser;
    private int loadGeneration;

    private View guardianReportStatePanel;
    private View guardianReportContentContainer;
    private View reportMenuButton;
    private ProgressBar progressGuardianReport;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_guardian_report);

        authRepository = provideAuthRepository();
        guardianReportRepository = provideGuardianReportRepository();
        guardianReportCoordinator = new GuardianReportCoordinator(
                this,
                new GuardianReportPresentationFormatter(this)
        );

        guardianReportStatePanel = findViewById(R.id.guardianReportStatePanel);
        guardianReportContentContainer = findViewById(R.id.guardianReportContentContainer);
        progressGuardianReport = findViewById(R.id.progressGuardianReport);

        guardianReportDashboardBinder = new GuardianReportDashboardBinder(
                this,
                getLayoutInflater(),
                new GuardianReportEntryCardBinder(this, getLayoutInflater(), this),
                findViewById(R.id.textGuardianReportMode),
                findViewById(R.id.guardianReportListContainer)
        );

        findViewById(R.id.buttonBackGuardianReport).setOnClickListener(view -> finish());
        reportMenuButton = findViewById(R.id.buttonGuardianReportRefresh);
        reportMenuButton.setOnClickListener(this::showReportMenu);
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        GuardianReportInsets.apply(
                findViewById(R.id.guardianReportInsetContent),
                findViewById(R.id.guardianReportTopBar),
                bottomNavigation
        );
        bottomNavigation.setVisibility(View.GONE);
        ClientBottomNavigationBinder.bind(
                bottomNavigation,
                ClientBottomNavigationTab.SCHEDULE_HISTORY,
                tab -> ClientBottomNavigationRouter.open(
                        this,
                        ClientBottomNavigationTab.SCHEDULE_HISTORY,
                        tab
                )
        );
        bindEmptyState();
        guardianReportContentContainer.setVisibility(View.GONE);
    }

    /**
     * 디버그 전용 화면이 로컬 인증 저장소를 주입할 수 있는 얇은 경계다.
     */
    protected AuthRepository provideAuthRepository() {
        return ServiceLocator.provideAuthRepository(this);
    }

    /**
     * 디버그 전용 화면이 로컬 리포트 저장소를 주입할 수 있는 얇은 경계다.
     */
    protected GuardianReportRepository provideGuardianReportRepository() {
        return ServiceLocator.provideGuardianReportRepository(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        int generation = ++loadGeneration;
        currentUser = null;
        setLoading(true);
        hideAllStates();
        bottomNavigation.setVisibility(View.GONE);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.GUARDIAN) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                currentUser = result;
                bottomNavigation.setVisibility(View.VISIBLE);
                loadDashboard(generation, result);
            }

            @Override
            public void onError(String message) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                setLoading(false);
                showAuthState();
            }
        });
    }

    @Override
    protected void onStop() {
        loadGeneration++;
        super.onStop();
    }

    private void loadDashboard(int generation, User user) {
        guardianReportRepository.getGuardianDashboard(user, new RepositoryCallback<GuardianReportDashboard>() {
            @Override
            public void onSuccess(GuardianReportDashboard result) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                setLoading(false);
                hideBlockingState();
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                if (!isActiveGeneration(generation)) {
                    return;
                }
                setLoading(false);
                bindEmptyState();
                showLoadErrorState(message);
            }
        });
    }

    private void refreshDashboard() {
        if (currentUser == null) {
            showAuthState();
            return;
        }
        int generation = ++loadGeneration;
        User user = currentUser;
        setLoading(true);
        hideAllStates();
        loadDashboard(generation, user);
    }

    private boolean isActiveGeneration(int generation) {
        return generation == loadGeneration && !isFinishing() && !isDestroyed();
    }

    private void bindDashboard(@Nullable GuardianReportDashboard dashboard) {
        guardianReportDashboardBinder.bindScreen(
                dashboard == null
                        ? guardianReportCoordinator.createEmptyScreenModel(guardianReportRepository.isFirebaseBacked())
                        : guardianReportCoordinator.createScreenModel(
                                dashboard,
                                guardianReportRepository.isFirebaseBacked()
                )
        );
    }

    private void bindEmptyState() {
        bindDashboard(null);
    }

    private void showReportMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(R.string.guardian_final_report_refresh_action);
        popupMenu.setOnMenuItemClickListener(item -> {
            refreshDashboard();
            return true;
        });
        popupMenu.show();
    }

    @Override
    public void onOpenRequestDetail(String requestId) {
        if (TextUtils.isEmpty(requestId)) {
            return;
        }
        startActivity(BookingStatusActivity.createIntent(this, requestId));
    }

    private void setLoading(boolean loading) {
        progressGuardianReport.setVisibility(loading ? View.VISIBLE : View.GONE);
        reportMenuButton.setEnabled(!loading);
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_guardian_report_title)),
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

    private void showLoadErrorState(String message) {
        String body = getString(R.string.state_load_error_body);
        if (!TextUtils.isEmpty(message)) {
            body = body + "\n\n" + message;
        }
        showBlockingState(
                StatePanelHelper.Tone.ERROR,
                getString(R.string.state_badge_error),
                getString(R.string.state_load_error_title, getString(R.string.feature_guardian_report_title)),
                body,
                getString(R.string.state_action_retry),
                view -> refreshDashboard(),
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
                guardianReportStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        guardianReportContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(guardianReportStatePanel);
        guardianReportContentContainer.setVisibility(View.VISIBLE);
    }

    private void hideAllStates() {
        StatePanelHelper.hide(guardianReportStatePanel);
        guardianReportContentContainer.setVisibility(View.GONE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
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
}
