package com.example.bodeul.ui.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.SystemClock;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.example.bodeul.MainActivity;
import com.example.bodeul.R;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.data.mock.MockClientSupportRepository;
import com.example.bodeul.data.mock.MockGuardianReportRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.booking.ClientBookingHistoryActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 Activity의 이동·복원은 검증하되 인증과 업무 데이터는 테스트 프로세스에만 둔다. */
@RunWith(AndroidJUnit4.class)
public class ClientNavigationReturnTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<Field, Object> originalRepositories = new LinkedHashMap<>();
    private AppointmentRequest request;

    @Before
    public void useIsolatedRepositories() throws Exception {
        Context context = instrumentation.getTargetContext();
        MockBodeulRepository data = new MockBodeulRepository();
        MockAuthRepository auth = new MockAuthRepository(data);
        AtomicReference<User> patient = new AtomicReference<>();
        auth.signIn(context.getString(R.string.demo_account_patient_email),
                context.getString(R.string.demo_account_password), UserRole.PATIENT,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        patient.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        throw new AssertionError("로컬 테스트 사용자 준비 실패: " + message);
                    }
                });
        assertNotNull(patient.get());
        request = data.getAppointmentRequestsForUser(
                patient.get().getId(), UserRole.PATIENT).get(0);
        request.setStatus(AppointmentStatus.CANCELED);
        replaceRepository("authRepository", auth);
        replaceRepository("bookingRepository", new MockBookingRepository(data));
        replaceRepository("guardianReportRepository", new MockGuardianReportRepository(data));
        replaceRepository("clientSupportRepository", new MockClientSupportRepository(data));
    }

    @After
    public void restoreRepositories() throws Exception {
        instrumentation.runOnMainSync(() -> {
            Set<Activity> activities = new HashSet<>();
            for (Stage stage : Stage.values()) {
                if (stage != Stage.DESTROYED) {
                    activities.addAll(ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(stage));
                }
            }
            for (Activity activity : activities) {
                activity.finish();
            }
        });
        instrumentation.waitForIdleSync();
        for (Map.Entry<Field, Object> entry : originalRepositories.entrySet()) {
            entry.getKey().set(null, entry.getValue());
        }
    }

    @Test
    public void systemBackToHome_keepsHomeSelected_andHistoryOpensAgain() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            click(home, R.id.clientNavScheduleHistory);
            ClientBookingHistoryActivity history = awaitActivity(ClientBookingHistoryActivity.class);
            instrumentation.runOnMainSync(() -> history.getOnBackPressedDispatcher().onBackPressed());
            assertSame(home, awaitActivity(MainActivity.class));
            assertSelected(home, R.id.clientNavHome);
            click(home, R.id.clientNavScheduleHistory);
            assertSelected(awaitActivity(ClientBookingHistoryActivity.class),
                    R.id.clientNavScheduleHistory);
        }
    }

    @Test
    public void rapidDestinationTaps_doNotStackHistory() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            instrumentation.runOnMainSync(() -> {
                home.findViewById(R.id.clientNavScheduleHistory).performClick();
                home.findViewById(R.id.clientNavScheduleHistory).performClick();
            });
            // 연속 실행 요청을 OS가 처리한 뒤 최종 화면에서 뒤로가기를 누른다.
            SystemClock.sleep(500L);
            ClientBookingHistoryActivity history = awaitActivity(ClientBookingHistoryActivity.class);
            instrumentation.runOnMainSync(() -> history.getOnBackPressedDispatcher().onBackPressed());
            assertSame(home, awaitActivity(MainActivity.class));
            assertSelected(home, R.id.clientNavHome);
        }
    }

    @Test
    public void canceledDetail_returnsToExistingHistory_withoutLeavingDetailInBackStack() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            click(home, R.id.clientNavScheduleHistory);
            ClientBookingHistoryActivity history = awaitActivity(ClientBookingHistoryActivity.class);
            openDetail(history);
            BookingStatusActivity detail = awaitActivity(BookingStatusActivity.class);
            click(detail, R.id.buttonBookingStatusPrimary);
            assertSame(history, awaitActivity(ClientBookingHistoryActivity.class));
            assertTrue(detail.isFinishing() || detail.isDestroyed());
            assertSelected(history, R.id.clientNavScheduleHistory);
            click(history, R.id.clientNavHome);
            assertSame(home, awaitActivity(MainActivity.class));
            assertSelected(home, R.id.clientNavHome);
            click(home, R.id.clientNavScheduleHistory);
            assertSelected(awaitActivity(ClientBookingHistoryActivity.class),
                    R.id.clientNavScheduleHistory);
        }
    }

    @Test
    public void canceledDetailWithoutHistory_createsHistory_andFinishesDetail() {
        try (ActivityScenario<BookingStatusActivity> scenario = ActivityScenario.launch(
                BookingStatusActivity.createIntent(instrumentation.getTargetContext(), request.getId()))) {
            BookingStatusActivity detail = awaitActivity(BookingStatusActivity.class);
            click(detail, R.id.buttonBookingStatusPrimary);
            assertSelected(awaitActivity(ClientBookingHistoryActivity.class),
                    R.id.clientNavScheduleHistory);
            assertTrue(detail.isFinishing() || detail.isDestroyed());
        }
    }

    @Test
    public void completedDetail_secondaryListAction_returnsToHistory() {
        request.setStatus(AppointmentStatus.COMPLETED);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            click(home, R.id.clientNavScheduleHistory);
            ClientBookingHistoryActivity history = awaitActivity(ClientBookingHistoryActivity.class);
            openDetail(history);
            BookingStatusActivity detail = awaitActivity(BookingStatusActivity.class);
            instrumentation.runOnMainSync(() -> assertEquals(
                    detail.getString(R.string.booking_status_action_open_follow_up),
                    ((TextView) detail.findViewById(
                            R.id.buttonBookingStatusPrimary)).getText().toString()));
            click(detail, R.id.buttonBookingStatusSecondary);
            assertSame(history, awaitActivity(ClientBookingHistoryActivity.class));
        }
    }

    @Test
    public void recreateAfterReturningHome_restoresHomeTab_andReselectionDoesNotDuplicate() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            click(home, R.id.clientNavScheduleHistory);
            click(awaitActivity(ClientBookingHistoryActivity.class), R.id.clientNavHome);
            awaitActivity(MainActivity.class);
            scenario.recreate();
            MainActivity recreated = awaitActivity(MainActivity.class);
            assertSelected(recreated, R.id.clientNavHome);
            click(recreated, R.id.clientNavHome);
            assertSame(recreated, awaitActivity(MainActivity.class));
            click(recreated, R.id.clientNavScheduleHistory);
            awaitActivity(ClientBookingHistoryActivity.class);
        }
    }

    private void replaceRepository(String name, Object replacement) throws Exception {
        Field field = ServiceLocator.class.getDeclaredField(name);
        field.setAccessible(true);
        originalRepositories.put(field, field.get(null));
        field.set(null, replacement);
    }

    private void openDetail(Activity activity) {
        instrumentation.runOnMainSync(() -> activity.startActivity(
                BookingStatusActivity.createIntent(activity, request.getId())));
        instrumentation.waitForIdleSync();
    }

    private void click(Activity activity, int id) {
        instrumentation.runOnMainSync(() -> assertTrue(activity.findViewById(id).performClick()));
        instrumentation.waitForIdleSync();
    }

    private void assertSelected(Activity activity, int id) {
        instrumentation.runOnMainSync(() -> {
            BottomNavigationView navigation = activity.findViewById(R.id.clientBottomNavigation);
            assertEquals(id, navigation.getSelectedItemId());
        });
    }

    private <T extends Activity> T awaitActivity(Class<T> expected) {
        AtomicReference<Activity> current = new AtomicReference<>();
        for (int attempt = 0; attempt < 60; attempt++) {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                current.set(null);
                for (Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED)) {
                    current.set(activity);
                }
            });
            if (expected.isInstance(current.get())) {
                return expected.cast(current.get());
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("기대 화면: " + expected.getSimpleName() + ", 실제 화면: "
                + (current.get() == null ? "없음" : current.get().getClass().getSimpleName()));
    }
}
