package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.navigation.ClientBottomNavigationBinder;
import com.example.bodeul.ui.navigation.ClientBottomNavigationRouter;
import com.example.bodeul.ui.navigation.ClientBottomNavigationTab;
import com.example.bodeul.ui.navigation.ClientBottomNavigationVisibility;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 사용자 예약 이력을 읽기 전용으로 보여준다.
 */
public class ClientBookingHistoryActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private ClientBookingHistoryCoordinator coordinator;
    private ClientBookingHistoryBinder binder;
    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_booking_history);

        authRepository = ServiceLocator.provideAuthRepository(this);
        bookingRepository = ServiceLocator.provideBookingRepository(this);
        coordinator = new ClientBookingHistoryCoordinator(this, new BookingPresentationFormatter(this));
        statePanel = findViewById(R.id.clientBookingHistoryStatePanel);
        contentContainer = findViewById(R.id.clientBookingHistoryContentContainer);
        progressBar = findViewById(R.id.progressClientBookingHistory);

        binder = new ClientBookingHistoryBinder(
                this,
                getLayoutInflater(),
                findViewById(R.id.textClientBookingHistoryMode),
                findViewById(R.id.textClientBookingHistoryTitle),
                findViewById(R.id.textClientBookingHistorySubtitle),
                findViewById(R.id.textClientBookingHistoryHeroBadge),
                findViewById(R.id.textClientBookingHistoryHeroTitle),
                findViewById(R.id.textClientBookingHistoryHeroBody),
                findViewById(R.id.textClientBookingHistoryListTitle),
                findViewById(R.id.textClientBookingHistoryListHelper),
                (LinearLayout) findViewById(R.id.layoutClientBookingHistoryEntries),
                (MaterialButton) findViewById(R.id.buttonClientBookingHistoryManage)
        );

        findViewById(R.id.buttonBackClientBookingHistory).setOnClickListener(view -> finish());
        findViewById(R.id.buttonClientBookingHistoryManage).setOnClickListener(view ->
                startActivity(new Intent(this, BookingActivity.class))
        );
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        ClientBookingHistoryInsets.apply(
                findViewById(R.id.clientBookingHistoryScrollContent),
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
        hideBlockingState();
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
                loadRequests(result);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadRequests(User currentUser) {
        bookingRepository.getMyAppointmentRequests(currentUser, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                setLoading(false);
                if (result.isEmpty()) {
                    showEmptyState();
                    return;
                }
                ClientBookingHistoryScreenModel screenModel = coordinator.createScreenModel(
                        currentUser,
                        result,
                        bookingRepository.isFirebaseBacked()
                );
                hideBlockingState();
                contentContainer.setVisibility(View.VISIBLE);
                binder.bindScreen(screenModel, requestId ->
                        startActivity(BookingStatusActivity.createIntent(ClientBookingHistoryActivity.this, requestId))
                );
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            contentContainer.setVisibility(View.GONE);
        }
    }

    private void showPermissionState() {
        showBlockingState(
                StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_permission),
                getString(R.string.state_permission_title, getString(R.string.client_booking_history_title)),
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

    private void showEmptyState() {
        showBlockingState(
                StatePanelHelper.Tone.INFO,
                getString(R.string.state_badge_notice),
                getString(R.string.client_booking_history_empty_title),
                getString(R.string.client_booking_history_empty_body),
                getString(R.string.client_booking_history_action_manage),
                view -> startActivity(new Intent(this, BookingActivity.class)),
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
                getString(R.string.state_load_error_title, getString(R.string.client_booking_history_title)),
                body,
                getString(R.string.state_action_retry),
                view -> reload(),
                getString(R.string.client_booking_history_action_manage),
                view -> startActivity(new Intent(this, BookingActivity.class))
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

    private void hideBlockingState() {
        StatePanelHelper.hide(statePanel);
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
