package com.example.bodeul.ui.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.util.StatePanelHelper;
import com.example.bodeul.ui.report.FinalReportActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 매니저 과거 동행 이력 화면의 인증과 로딩 흐름을 관리한다.
 */
public class ManagerHistoryActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerHistoryCoordinator managerHistoryCoordinator;
    private ManagerHistoryBinder managerHistoryBinder;
    private final List<AppointmentRequestDetail> historyDetailsSnapshot = new ArrayList<>();
    private ManagerHistoryFilter selectedFilter = ManagerHistoryFilter.ALL;
    @Nullable
    private User currentManager;

    private View managerHistoryStatePanel;
    private View managerHistoryContentContainer;
    private ProgressBar progressManagerHistory;

    public static Intent createIntent(Context context) {
        return new Intent(context, ManagerHistoryActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager_history);

        authRepository = ServiceLocator.provideAuthRepository(this);
        managerRepository = ServiceLocator.provideManagerRepository(this);
        managerHistoryCoordinator = new ManagerHistoryCoordinator(
                this,
                new ManagerHomePresentationFormatter(this)
        );

        managerHistoryStatePanel = findViewById(R.id.managerHistoryStatePanel);
        managerHistoryContentContainer = findViewById(R.id.managerHistoryContentContainer);
        progressManagerHistory = findViewById(R.id.progressManagerHistory);

        managerHistoryBinder = new ManagerHistoryBinder(
                getLayoutInflater(),
                findViewById(R.id.textManagerHistoryMode),
                findViewById(R.id.textManagerHistoryHeroBadge),
                findViewById(R.id.textManagerHistoryHeroTitle),
                findViewById(R.id.textManagerHistoryHeroBody),
                findViewById(R.id.textManagerHistorySummary),
                findViewById(R.id.textManagerHistoryListHelper),
                findViewById(R.id.managerHistoryMetricContainer),
                findViewById(R.id.managerHistoryFilterContainer),
                findViewById(R.id.managerHistoryListContainer),
                findViewById(R.id.textManagerHistoryEmpty)
        );

        findViewById(R.id.buttonBackManagerHistory).setOnClickListener(view -> finish());
        managerHistoryContentContainer.setVisibility(View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadHistory();
    }

    private void loadHistory() {
        setLoading(true);
        hideBlockingState();
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    openProfileCompletion();
                    return;
                }
                if (result.getRole() != UserRole.MANAGER) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }

                managerRepository.getManagerHistoryDetails(result.getId(), new RepositoryCallback<List<AppointmentRequestDetail>>() {
                    @Override
                    public void onSuccess(List<AppointmentRequestDetail> details) {
                        setLoading(false);
                        hideBlockingState();
                        managerHistoryContentContainer.setVisibility(View.VISIBLE);
                        currentManager = result;
                        historyDetailsSnapshot.clear();
                        historyDetailsSnapshot.addAll(details);
                        renderHistory();
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false);
                        showLoadErrorState(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressManagerHistory.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void renderHistory() {
        if (currentManager == null) {
            return;
        }
        managerHistoryBinder.bindScreen(
                managerHistoryCoordinator.createScreenModel(
                        currentManager,
                        historyDetailsSnapshot,
                        managerRepository.isFirebaseBacked(),
                        selectedFilter
                ),
                new ManagerHistoryBinder.Listener() {
                    @Override public void onSelectFilter(ManagerHistoryFilter filter) {
                        selectedFilter = filter;
                        renderHistory();
                    }
                    @Override public void onOpenReport(String requestId) {
                        startActivity(FinalReportActivity.createIntent(ManagerHistoryActivity.this, requestId));
                    }
                }
        );
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.feature_manager_history_title)),
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
                getString(R.string.state_load_error_title, getString(R.string.feature_manager_history_title)),
                body,
                getString(R.string.state_action_retry),
                view -> loadHistory(),
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
        StatePanelHelper.show(
                managerHistoryStatePanel,
                tone,
                badge,
                title,
                body,
                primaryText,
                primaryListener,
                secondaryText,
                secondaryListener
        );
        managerHistoryContentContainer.setVisibility(View.GONE);
    }

    private void hideBlockingState() {
        StatePanelHelper.hide(managerHistoryStatePanel);
        managerHistoryContentContainer.setVisibility(View.VISIBLE);
    }

    private void openGeneralHome() {
        startActivity(new Intent(this, MainActivity.class));
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
}
