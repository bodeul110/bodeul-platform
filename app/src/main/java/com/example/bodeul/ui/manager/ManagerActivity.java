package com.example.bodeul.ui.manager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CalendarView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.auth.ProfileCompletionActivity;
import com.example.bodeul.ui.auth.RoleSelectionActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.home.ClientHomeInsets;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 완성본 홈의 인증·조회·이동을 연결하고 표시와 집계는 전용 객체에 위임한다. */
public class ManagerActivity extends AppCompatActivity {
    private AuthRepository authRepository;
    private ManagerRepository managerRepository;
    private ManagerHomeDashboardBinder binder;
    private ManagerHomeCoordinator coordinator;
    private ManagerQuickNoteDialogController quickNote;
    private BottomNavigationView navigation;
    private OnBackPressedCallback homeBack;
    private User currentUser;
    private ManagerDashboard dashboard;
    private ManagerMatchingOverview overview;
    private ManagerHomeProfile profile;
    private boolean appointmentsFailed;
    private boolean sessionLoaded;
    private boolean sessionFailed;
    private boolean profileFailed;
    private int generation;
    private int selectedTab = R.id.navManagerHome;
    private ManagerMatchingOverview.Filter filter = ManagerMatchingOverview.Filter.ALL;
    private String selectedDate;
    private AlertDialog activeDialog;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_manager_home);
        authRepository = provideAuthRepository();
        managerRepository = provideManagerRepository();
        binder = new ManagerHomeDashboardBinder(this, findViewById(android.R.id.content));
        coordinator = new ManagerHomeCoordinator(this, new ManagerHomePresentationFormatter(this));
        quickNote = new ManagerQuickNoteDialogController(this, getLayoutInflater());
        navigation = findViewById(R.id.layoutManagerHomeBottomNav);
        ClientHomeInsets.apply(findViewById(R.id.scrollManagerHome), findViewById(R.id.layoutManagerHomeTopBar), navigation);
        EnvironmentModeBadgeHelper.bind(findViewById(R.id.textManagerMode),
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(this, managerRepository.isFirebaseBacked()));

        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA);
        selectedDate = dateKey(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH));
        if (state != null) {
            selectedTab = state.getInt("managerTab", R.id.navManagerHome);
            selectedDate = state.getString("managerDate", selectedDate);
            try { filter = ManagerMatchingOverview.Filter.valueOf(state.getString("managerFilter", "ALL")); }
            catch (IllegalArgumentException ignored) { filter = ManagerMatchingOverview.Filter.ALL; }
        }
        CalendarView calendar = findViewById(R.id.calendarManagerAppointments);
        Calendar selected = Calendar.getInstance();
        String[] parts = selectedDate.split("-");
        selected.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]), 12, 0, 0);
        calendar.setDate(selected.getTimeInMillis());
        calendar.setOnDateChangeListener((view, year, month, day) -> {
            selectedDate = dateKey(year, month, day);
            render();
        });
        ChipGroup chips = findViewById(R.id.chipsManagerMatching);
        chips.check(filter == ManagerMatchingOverview.Filter.ONGOING ? R.id.chipManagerOngoing
                : filter == ManagerMatchingOverview.Filter.COMPLETED ? R.id.chipManagerCompleted
                : filter == ManagerMatchingOverview.Filter.CANCELED ? R.id.chipManagerCanceled : R.id.chipManagerAll);
        chips.setOnCheckedStateChangeListener((group, checked) -> {
            int id = checked.isEmpty() ? R.id.chipManagerAll : checked.get(0);
            filter = id == R.id.chipManagerOngoing ? ManagerMatchingOverview.Filter.ONGOING
                    : id == R.id.chipManagerCompleted ? ManagerMatchingOverview.Filter.COMPLETED
                    : id == R.id.chipManagerCanceled ? ManagerMatchingOverview.Filter.CANCELED
                    : ManagerMatchingOverview.Filter.ALL;
            render();
        });
        homeBack = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { navigation.setSelectedItemId(R.id.navManagerHome); }
        };
        getOnBackPressedDispatcher().addCallback(this, homeBack);
        navigation.setOnItemSelectedListener(item -> {
            if (currentUser == null) return false;
            if (item.getItemId() == R.id.navManagerChat) {
                startActivity(CompanionChatActivity.createIntent(this));
                return false;
            }
            if (item.getItemId() == R.id.navManagerProfile) {
                openProfile();
                return false;
            }
            selectedTab = item.getItemId();
            render();
            ((ScrollView) findViewById(R.id.scrollManagerHome)).scrollTo(0, 0);
            return true;
        });
        navigation.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navManagerHome && currentUser != null) reload();
        });
        navigation.getMenu().findItem(selectedTab).setChecked(true);
        click(R.id.buttonManagerMatchingHistory, () -> navigation.setSelectedItemId(R.id.navManagerMatching));
        click(R.id.buttonManagerProfile, this::openProfile);
        click(R.id.buttonManagerDocuments, () -> startActivity(ManagerDocumentRegistrationActivity.createIntent(this)));
        click(R.id.buttonManagerSupport, this::openSupport);
        click(R.id.buttonManagerFooterSupport, this::openSupport);
        click(R.id.buttonManagerHistory, this::openHistory);
        click(R.id.buttonManagerAppointmentsHistory, this::openHistory);
        click(R.id.buttonManagerCountRetry, this::reload);
        click(R.id.buttonManagerAppointmentsRefresh, this::reload);
        click(R.id.buttonManagerHeroPrimary, () -> { if (dashboard != null) openGuide(); else reload(); });
        click(R.id.buttonManagerScheduleNote, this::editSchedule);
        click(R.id.buttonManagerNoticeMore, () -> {
            activeDialog = new MaterialAlertDialogBuilder(this).setTitle(R.string.final_home_book)
                    .setMessage(R.string.final_manager_overview_body)
                    .setPositiveButton(R.string.permission_guide_confirm, null).show();
        });
        click(R.id.buttonManagerLogout, () -> {
            generation++;
            authRepository.signOut();
            clearPrivateState();
            openRoleSelection();
        });
    }

    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }
    protected ManagerRepository provideManagerRepository() { return ServiceLocator.provideManagerRepository(this); }

    @Override protected void onStart() { super.onStart(); reload(); }

    @Override protected void onStop() {
        generation++;
        if (activeDialog != null) { activeDialog.dismiss(); activeDialog = null; }
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("managerTab", selectedTab);
        state.putString("managerDate", selectedDate);
        state.putString("managerFilter", filter.name());
        super.onSaveInstanceState(state);
    }

    private void reload() {
        int attempt = ++generation;
        clearPrivateState();
        showBlocking(StatePanelHelper.Tone.INFO, R.string.manager_home_loading_badge,
                R.string.manager_home_loading_title, R.string.manager_home_loading_body, null, null);
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (attempt != generation) return;
                if (user == null) { showAuth(); return; }
                if (AuthFlowRouter.requiresProfileCompletion(user)) {
                    Intent intent = ProfileCompletionActivity.createIntent(ManagerActivity.this);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    return;
                }
                if (user.getRole() != UserRole.MANAGER) {
                    showBlocking(StatePanelHelper.Tone.WARNING, R.string.state_badge_permission,
                            R.string.feature_manager_home_title, R.string.state_permission_body,
                            getString(R.string.state_action_open_home), view -> {
                                startActivity(new Intent(ManagerActivity.this, MainActivity.class));
                                finish();
                            });
                    return;
                }
                currentUser = user;
                StatePanelHelper.hide(findViewById(R.id.managerStatePanel));
                navigation.setVisibility(View.VISIBLE);
                findViewById(R.id.buttonManagerSupport).setEnabled(true);
                binder.bindUser(user);
                render();
                loadProfile(user, attempt);
                loadAppointments(user, attempt);
                loadSession(user, attempt);
            }
            @Override public void onError(String message) { if (attempt == generation) showAuth(); }
        });
    }

    private void loadProfile(User user, int attempt) {
        managerRepository.getManagerHomeProfile(user.getId(), new RepositoryCallback<ManagerHomeProfile>() {
            @Override public void onSuccess(ManagerHomeProfile result) {
                if (attempt != generation) return;
                profile = result;
                profileFailed = result == null;
                render();
            }
            @Override public void onError(String message) {
                if (attempt != generation) return;
                profileFailed = true;
                render();
            }
        });
    }

    private void loadAppointments(User user, int attempt) {
        managerRepository.getManagerAppointments(user.getId(), new RepositoryCallback<List<AppointmentRequest>>() {
            @Override public void onSuccess(List<AppointmentRequest> result) {
                if (attempt != generation) return;
                if (result == null) { onError(""); return; }
                overview = new ManagerMatchingOverview(result);
                render();
            }
            @Override public void onError(String message) {
                if (attempt != generation) return;
                appointmentsFailed = true;
                render();
            }
        });
    }

    private void loadSession(User user, int attempt) {
        managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override public void onSuccess(ManagerDashboard result) {
                if (attempt != generation) return;
                dashboard = result;
                sessionLoaded = true;
                render();
            }
            @Override public void onError(String message) {
                if (attempt != generation) return;
                sessionLoaded = true;
                sessionFailed = !ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message);
                render();
            }
        });
    }

    private void render() {
        if (currentUser == null) return;
        boolean home = selectedTab == R.id.navManagerHome;
        findViewById(R.id.managerContentContainer).setVisibility(home ? View.VISIBLE : View.GONE);
        findViewById(R.id.managerAppointmentsPanel).setVisibility(home ? View.GONE : View.VISIBLE);
        homeBack.setEnabled(!home);
        binder.bindCounts(overview, appointmentsFailed);
        ManagerHomeScreenModel model = dashboard == null ? null : coordinator.createScreenModel(
                currentUser, profile == null ? new ManagerHomeProfile("", "") : profile, dashboard, managerRepository.isFirebaseBacked());
        binder.bindSession(model, sessionLoaded, sessionFailed);
        if (!home) binder.bindAppointments(overview, appointmentsFailed, selectedTab == R.id.navManagerCalendar, selectedDate, filter,
                dashboard == null ? null : dashboard.getAppointmentRequest().getId(), view -> openGuide());
        ((TextView) findViewById(R.id.textManagerScheduleNote)).setText(profile != null ? profile.getAvailabilitySummary()
                : getString(profileFailed ? R.string.final_manager_profile_error : R.string.final_manager_schedule_loading));
        findViewById(R.id.buttonManagerScheduleNote).setEnabled(profile != null);
    }

    private void clearPrivateState() {
        currentUser = null;
        dashboard = null;
        overview = null;
        profile = null;
        appointmentsFailed = false;
        sessionLoaded = false;
        sessionFailed = false;
        profileFailed = false;
        ((TextView) findViewById(R.id.textManagerGreeting)).setText("");
        ((TextView) findViewById(R.id.textManagerActiveBody)).setText("");
        ((TextView) findViewById(R.id.textManagerActiveTitle)).setText("");
        ((TextView) findViewById(R.id.textManagerScheduleNote)).setText("");
        binder.bindCounts(null, false);
        binder.bindAppointments(null, false, false, selectedDate, filter, null, view -> {});
        findViewById(R.id.managerContentContainer).setVisibility(View.GONE);
        findViewById(R.id.managerAppointmentsPanel).setVisibility(View.GONE);
        navigation.setVisibility(View.GONE);
        findViewById(R.id.buttonManagerSupport).setEnabled(false);
        homeBack.setEnabled(false);
    }

    private void editSchedule() {
        if (profile == null || currentUser == null) return;
        activeDialog = quickNote.show(ManagerQuickNoteType.SCHEDULE, profile.getAvailabilitySummary(),
                (type, value, dialog) -> {
                    if (currentUser == null) return;
                    int attempt = generation;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    managerRepository.saveManagerAvailabilitySummary(currentUser.getId(), value, new RepositoryCallback<ManagerHomeProfile>() {
                        @Override public void onSuccess(ManagerHomeProfile result) {
                            if (attempt != generation) return;
                            profile = result;
                            render();
                            dialog.dismiss();
                            Toast.makeText(ManagerActivity.this, R.string.manager_action_schedule_saved, Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String message) {
                            if (attempt != generation) return;
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(ManagerActivity.this, message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
    }

    private void openGuide() {
        if (currentUser != null && dashboard != null) startActivity(new Intent(this, ManagerGuideActivity.class));
    }
    private void openProfile() { startActivity(ManagerProfileActivity.createIntent(this)); }
    private void openSupport() { startActivity(ManagerSupportActivity.createIntent(this)); }
    private void openHistory() { startActivity(ManagerHistoryActivity.createIntent(this)); }

    private void click(int id, Runnable action) {
        findViewById(id).setOnClickListener(view -> { if (currentUser != null) action.run(); });
    }

    private void showAuth() {
        clearPrivateState();
        showBlocking(StatePanelHelper.Tone.WARNING, R.string.state_badge_auth, R.string.state_auth_title,
                R.string.state_auth_body, getString(R.string.state_action_open_login), view -> openRoleSelection());
    }

    private void showBlocking(StatePanelHelper.Tone tone, int badge, int title, int body,
                              @Nullable String action, @Nullable View.OnClickListener listener) {
        StatePanelHelper.show(findViewById(R.id.managerStatePanel), tone, getString(badge), getString(title),
                getString(body), action, listener, null, null);
    }

    private void openRoleSelection() {
        Intent intent = new Intent(this, RoleSelectionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String dateKey(int year, int month, int day) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day);
    }
}
