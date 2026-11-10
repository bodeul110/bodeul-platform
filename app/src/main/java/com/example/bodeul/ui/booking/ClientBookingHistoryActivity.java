package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.CalendarView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

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

import java.util.List;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * 사용자 예약 이력을 읽기 전용으로 보여준다.
 */
public class ClientBookingHistoryActivity extends AppCompatActivity {
    private static final String STATE_CALENDAR_YEAR = "calendarYear";
    private static final String STATE_CALENDAR_MONTH = "calendarMonth";
    private static final String STATE_CALENDAR_DAY = "calendarDay";
    private static final String STATE_SELECTED_HISTORY_FILTER = "selectedHistoryFilter";
    private static final String STATE_HISTORY_FILTER_CHOSEN = "historyFilterChosen";

    private AuthRepository authRepository;
    private BookingRepository bookingRepository;
    private ClientBookingHistoryCoordinator coordinator;
    private ClientBookingHistoryBinder binder;
    private View statePanel;
    private View contentContainer;
    private ProgressBar progressBar;
    private BottomNavigationView bottomNavigation;
    private View historyFilterContainer;
    private TextView historyFilterEmptyView;
    private TextView filterMatchingView;
    private TextView filterProgressView;
    private TextView filterCompletedView;
    private TextView filterCanceledView;
    private User loadedUser;
    private List<AppointmentRequest> loadedRequests = new ArrayList<>();
    private ClientBookingHistoryFilter selectedHistoryFilter = ClientBookingHistoryFilter.MATCHING;
    private boolean historyFilterChosen;
    private int calendarYear, calendarMonth, calendarDay;
    private int loadGeneration;

    protected boolean isCalendarScreen() { return false; }
    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected BookingRepository provideBookingRepository() { return ServiceLocator.provideBookingRepository(this); }
    private ClientBottomNavigationTab selectedTab() {
        return isCalendarScreen() ? ClientBottomNavigationTab.CALENDAR : ClientBottomNavigationTab.SCHEDULE_HISTORY;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bodeul_FinalHome);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client_booking_history);

        authRepository = provideAuthRepository();
        bookingRepository = provideBookingRepository();
        coordinator = new ClientBookingHistoryCoordinator(this, new BookingPresentationFormatter(this));
        statePanel = findViewById(R.id.clientBookingHistoryStatePanel);
        contentContainer = findViewById(R.id.clientBookingHistoryContentContainer);
        progressBar = findViewById(R.id.progressClientBookingHistory);
        historyFilterContainer = findViewById(R.id.layoutClientBookingHistoryFilters);
        historyFilterEmptyView = findViewById(R.id.textClientBookingHistoryFilterEmpty);
        filterMatchingView = findViewById(R.id.buttonHistoryFilterMatching);
        filterProgressView = findViewById(R.id.buttonHistoryFilterProgress);
        filterCompletedView = findViewById(R.id.buttonHistoryFilterCompleted);
        filterCanceledView = findViewById(R.id.buttonHistoryFilterCanceled);
        restoreHistoryFilter(savedInstanceState);
        bindHistoryFilter(filterMatchingView, ClientBookingHistoryFilter.MATCHING);
        bindHistoryFilter(filterProgressView, ClientBookingHistoryFilter.PROGRESS);
        bindHistoryFilter(filterCompletedView, ClientBookingHistoryFilter.COMPLETED);
        bindHistoryFilter(filterCanceledView, ClientBookingHistoryFilter.CANCELED);
        historyFilterContainer.setVisibility(isCalendarScreen() ? View.GONE : View.VISIBLE);
        ((TextView) findViewById(R.id.textClientBookingHistoryTitle)).setText(
                isCalendarScreen() ? R.string.final_home_calendar : R.string.final_history_title
        );
        updateHistoryFilterViews();
        Calendar today = BookingAppointmentDateTime.newCalendar();
        calendarYear = savedInstanceState == null ? today.get(Calendar.YEAR)
                : savedInstanceState.getInt(STATE_CALENDAR_YEAR);
        calendarMonth = savedInstanceState == null ? today.get(Calendar.MONTH)
                : savedInstanceState.getInt(STATE_CALENDAR_MONTH);
        calendarDay = savedInstanceState == null ? today.get(Calendar.DAY_OF_MONTH)
                : savedInstanceState.getInt(STATE_CALENDAR_DAY);
        Calendar displayed = Calendar.getInstance();
        displayed.set(calendarYear, calendarMonth, calendarDay, 12, 0);
        CalendarView calendar = findViewById(R.id.clientCalendar);
        calendar.setDate(displayed.getTimeInMillis(), false, true);
        calendar.setOnDateChangeListener((view, year, month, day) -> onCalendarDateSelected(year, month, day));

        binder = new ClientBookingHistoryBinder(
                this,
                getLayoutInflater(),
                (LinearLayout) findViewById(R.id.layoutClientBookingHistoryEntries)
        );

        findViewById(R.id.buttonOpenGuardianBookingInbox).setOnClickListener(view -> {
            if (authRepository.isFirebaseBacked()
                    && getResources().getBoolean(R.bool.bodeul_guardian_booking_inbox_enabled)) {
                startActivity(new Intent(this, GuardianBookingInboxActivity.class));
            }
        });
        bottomNavigation = findViewById(R.id.clientBottomNavigation);
        ClientBookingHistoryInsets.apply(
                findViewById(R.id.clientBookingHistoryScrollContent),
                bottomNavigation
        );
        bottomNavigation.setVisibility(View.GONE);
        ClientBottomNavigationBinder.bind(
                bottomNavigation,
                selectedTab(),
                tab -> ClientBottomNavigationRouter.open(
                        this,
                        selectedTab(),
                        tab
                )
        );
        contentContainer.setVisibility(View.GONE);
    }

    private void restoreHistoryFilter(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        String savedFilter = savedInstanceState.getString(STATE_SELECTED_HISTORY_FILTER);
        if (savedFilter == null) {
            return;
        }
        try {
            selectedHistoryFilter = ClientBookingHistoryFilter.valueOf(savedFilter);
            historyFilterChosen = savedInstanceState.getBoolean(
                    STATE_HISTORY_FILTER_CHOSEN,
                    false
            );
        } catch (IllegalArgumentException ignored) {
            selectedHistoryFilter = ClientBookingHistoryFilter.MATCHING;
            historyFilterChosen = false;
        }
    }

    private void bindHistoryFilter(TextView view, ClientBookingHistoryFilter filter) {
        view.setOnClickListener(clickedView -> {
            // 기본으로 보이는 탭을 다시 누른 경우도 사용자가 직접 고른 상태로 보존한다.
            historyFilterChosen = true;
            if (selectedHistoryFilter == filter) {
                return;
            }
            selectedHistoryFilter = filter;
            updateHistoryFilterViews();
            if (loadedUser != null) {
                renderRequests();
            }
        });
    }

    private void updateHistoryFilterViews() {
        applyHistoryFilterState(filterMatchingView, ClientBookingHistoryFilter.MATCHING);
        applyHistoryFilterState(filterProgressView, ClientBookingHistoryFilter.PROGRESS);
        applyHistoryFilterState(filterCompletedView, ClientBookingHistoryFilter.COMPLETED);
        applyHistoryFilterState(filterCanceledView, ClientBookingHistoryFilter.CANCELED);
    }

    private void applyHistoryFilterState(TextView view, ClientBookingHistoryFilter filter) {
        boolean selected = selectedHistoryFilter == filter;
        view.setSelected(selected);
        view.setBackgroundResource(selected ? R.drawable.bg_final_history_filter_selected : 0);
        view.setTextColor(ContextCompat.getColor(
                this,
                selected ? R.color.final_text_on_primary : R.color.final_text_secondary
        ));
    }

    private int selectedHistoryEmptyMessage() {
        switch (selectedHistoryFilter) {
            case PROGRESS:
                return R.string.final_history_empty_progress;
            case COMPLETED:
                return R.string.final_history_empty_completed;
            case CANCELED:
                return R.string.final_history_empty_canceled;
            case MATCHING:
            default:
                return R.string.final_history_empty_matching;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        reload();
    }

    private void reload() {
        int generation = ++loadGeneration;
        loadedUser = null;
        loadedRequests.clear();
        findViewById(R.id.clientCalendarContainer).setVisibility(View.GONE);
        setLoading(true);
        findViewById(R.id.buttonOpenGuardianBookingInbox).setVisibility(View.GONE);
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
                if (!ClientBottomNavigationVisibility.isVisibleFor(result.getRole())) {
                    setLoading(false);
                    showPermissionState();
                    return;
                }
                bottomNavigation.setVisibility(View.VISIBLE);
                findViewById(R.id.buttonOpenGuardianBookingInbox).setVisibility(
                        authRepository.isFirebaseBacked()
                                && getResources().getBoolean(R.bool.bodeul_guardian_booking_inbox_enabled)
                                ? View.VISIBLE : View.GONE);
                loadRequests(result, generation);
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                setLoading(false);
                showAuthState();
            }
        });
    }

    private void loadRequests(User currentUser, int generation) {
        bookingRepository.getMyAppointmentRequests(currentUser, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                if (generation != loadGeneration) return;
                setLoading(false);
                loadedUser = currentUser;
                loadedRequests = result == null ? new ArrayList<>() : new ArrayList<>(result);
                selectInitialHistoryFilterIfNeeded();
                renderRequests();
            }

            @Override
            public void onError(String message) {
                if (generation != loadGeneration) return;
                setLoading(false);
                showLoadErrorState(message);
            }
        });
    }

    private void selectInitialHistoryFilterIfNeeded() {
        if (isCalendarScreen() || historyFilterChosen || loadedRequests.isEmpty()) {
            return;
        }
        ClientBookingHistoryFilter[] filters = {
                ClientBookingHistoryFilter.MATCHING,
                ClientBookingHistoryFilter.PROGRESS,
                ClientBookingHistoryFilter.COMPLETED,
                ClientBookingHistoryFilter.CANCELED
        };
        for (ClientBookingHistoryFilter filter : filters) {
            if (!filter.select(loadedRequests).isEmpty()) {
                selectedHistoryFilter = filter;
                historyFilterChosen = true;
                updateHistoryFilterViews();
                return;
            }
        }
    }

    protected void onCalendarDateSelected(int year, int month, int day) {
        calendarYear = year;
        calendarMonth = month;
        calendarDay = day;
        if (loadedUser != null) renderRequests();
    }

    private void renderRequests() {
        boolean calendarScreen = isCalendarScreen();
        List<AppointmentRequest> visible = calendarScreen
                ? ClientCalendarFilter.select(loadedRequests, calendarYear, calendarMonth, calendarDay)
                : selectedHistoryFilter.select(loadedRequests);
        hideBlockingState();
        findViewById(R.id.clientCalendarContainer).setVisibility(calendarScreen ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.textClientCalendarDate)).setText(getString(
                R.string.final_calendar_date, calendarYear, calendarMonth + 1, calendarDay));
        findViewById(R.id.textClientCalendarEmpty).setVisibility(
                calendarScreen && visible.isEmpty() ? View.VISIBLE : View.GONE
        );

        if (!calendarScreen && loadedRequests.isEmpty()) {
            contentContainer.setVisibility(View.GONE);
            showEmptyState();
            return;
        }
        if (calendarScreen && visible.isEmpty()) {
            contentContainer.setVisibility(View.GONE);
            return;
        }

        contentContainer.setVisibility(View.VISIBLE);
        historyFilterEmptyView.setText(selectedHistoryEmptyMessage());
        historyFilterEmptyView.setVisibility(
                !calendarScreen && visible.isEmpty() ? View.VISIBLE : View.GONE
        );
        binder.bindScreen(
                coordinator.createScreenModel(
                        loadedUser,
                        visible,
                        bookingRepository.isFirebaseBacked()
                ),
                new ClientBookingHistoryBinder.EntryActionListener() {
                    @Override
                    public void onOpenRequest(String requestId) {
                        startActivity(BookingStatusActivity.createIntent(
                                ClientBookingHistoryActivity.this,
                                requestId
                        ));
                    }

                    @Override
                    public void onOpenReview(String requestId) {
                        startActivity(BookingFollowUpActivity.createIntent(
                                ClientBookingHistoryActivity.this,
                                requestId
                        ));
                    }

                    @Override
                    public void onOpenReport(String requestId) {
                        startActivity(BookingStatusActivity.createIntent(
                                ClientBookingHistoryActivity.this,
                                requestId
                        ));
                    }
                }
        );
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_CALENDAR_YEAR, calendarYear);
        outState.putInt(STATE_CALENDAR_MONTH, calendarMonth);
        outState.putInt(STATE_CALENDAR_DAY, calendarDay);
        outState.putString(STATE_SELECTED_HISTORY_FILTER, selectedHistoryFilter.name());
        outState.putBoolean(STATE_HISTORY_FILTER_CHOSEN, historyFilterChosen);
        super.onSaveInstanceState(outState);
    }

    @Override protected void onStop() {
        loadGeneration++;
        super.onStop();
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
