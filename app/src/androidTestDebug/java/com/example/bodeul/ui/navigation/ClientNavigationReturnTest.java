package com.example.bodeul.ui.navigation;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
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
import com.example.bodeul.data.mock.MockCompanionChatAttachmentPreviewResolver;
import com.example.bodeul.data.mock.MockCompanionChatAttachmentUploader;
import com.example.bodeul.data.mock.MockGuardianReportRepository;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingMeetingLocationSelection;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.example.bodeul.ui.booking.BookingActivity;
import com.example.bodeul.ui.booking.BookingCompletionActivity;
import com.example.bodeul.ui.booking.BookingFormBinder;
import com.example.bodeul.ui.booking.BookingHealthProfileSelection;
import com.example.bodeul.ui.booking.BookingHealthProfileActivity;
import com.example.bodeul.ui.booking.BookingPaymentApprovalActivity;
import com.example.bodeul.ui.booking.ClientBookingHistoryActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.profile.ClientProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 Activity의 이동·복원은 검증하되 인증과 업무 데이터는 테스트 프로세스에만 둔다. */
@RunWith(AndroidJUnit4.class)
public class ClientNavigationReturnTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<Field, Object> originalRepositories = new LinkedHashMap<>();
    private MockBodeulRepository data;
    private User patient;
    private AppointmentRequest request;

    @Before
    public void useIsolatedRepositories() throws Exception {
        Context context = instrumentation.getTargetContext();
        data = new MockBodeulRepository();
        MockAuthRepository auth = new MockAuthRepository(data);
        AtomicReference<User> signedInUser = new AtomicReference<>();
        auth.signIn(context.getString(R.string.demo_account_patient_email),
                context.getString(R.string.demo_account_password), UserRole.PATIENT,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        signedInUser.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        throw new AssertionError("로컬 테스트 사용자 준비 실패: " + message);
                    }
                });
        patient = signedInUser.get();
        assertNotNull(patient);
        request = data.getAppointmentRequestsForUser(
                patient.getId(), UserRole.PATIENT).get(0);
        request.setStatus(AppointmentStatus.CANCELED);
        replaceRepository("authRepository", auth);
        replaceRepository("bookingRepository", new MockBookingRepository(data));
        replaceRepository("guardianReportRepository", new MockGuardianReportRepository(data));
        replaceRepository("clientSupportRepository", new MockClientSupportRepository(data));
        replaceRepository("managerRepository", new MockManagerRepository(data));
        replaceRepository("companionChatAttachmentUploader",
                new MockCompanionChatAttachmentUploader(context));
        replaceRepository("companionChatAttachmentPreviewResolver",
                new MockCompanionChatAttachmentPreviewResolver());
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

    @Test
    public void submittedBooking_realKeyboardAndAllTabs_keepNavigationBounds() {
        // 진행 중인 fixture를 제외해 동행방에서 원격 Realtime 구독을 시작하지 않는다.
        for (AppointmentRequest existing : data.getAppointmentRequestsForUser(
                patient.getId(), UserRole.PATIENT)) {
            existing.setStatus(AppointmentStatus.CANCELED);
        }
        int beforeCount = data.getAppointmentRequestsForUser(patient.getId(), UserRole.PATIENT).size();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            MainActivity home = awaitActivity(MainActivity.class);
            assertNavigationBounds(home, R.id.clientNavHome);
            click(home, R.id.cardActionBooking);
            BookingActivity booking = awaitActivity(BookingActivity.class);
            fillLocalBooking(booking);
            assertNavigationBounds(booking, R.id.clientNavScheduleHistory);

            click(booking, R.id.buttonBookingHealthProfile);
            BookingHealthProfileActivity health = awaitActivity(BookingHealthProfileActivity.class);
            onView(withId(R.id.inputBookingHealthProfileCondition)).perform(
                    androidx.test.espresso.action.ViewActions.click(), replaceText("화면 이동용 합성 기록"));
            instrumentation.runOnMainSync(() -> {
                View input = health.findViewById(R.id.inputBookingHealthProfileCondition);
                input.requestFocus();
                WindowCompat.getInsetsController(health.getWindow(), input)
                        .show(WindowInsetsCompat.Type.ime());
            });
            awaitIme(health, true);
            onView(withId(R.id.inputBookingHealthProfileCondition)).check(matches(isCompletelyDisplayed()));
            onView(withId(R.id.inputBookingHealthProfileCondition)).perform(closeSoftKeyboard());
            awaitIme(health, false);
            click(health, R.id.buttonBookingHealthProfileComplete);
            assertSame(booking, awaitActivity(BookingActivity.class));
            assertNavigationBounds(booking, R.id.clientNavScheduleHistory);

            scrollAboveNavigation(booking, R.id.buttonSubmitBooking);
            onView(withId(R.id.buttonSubmitBooking)).perform(
                    androidx.test.espresso.action.ViewActions.click());
            BookingPaymentApprovalActivity payment = awaitActivity(BookingPaymentApprovalActivity.class);
            instrumentation.runOnMainSync(() -> {
                View parent = payment.findViewById(R.id.checkBookingPaymentConsent);
                while (!(parent instanceof ScrollView)) {
                    parent = (View) parent.getParent();
                }
                ScrollView scroll = (ScrollView) parent;
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            });
            instrumentation.waitForIdleSync();
            onView(withId(R.id.checkBookingPaymentConsent)).perform(
                    androidx.test.espresso.action.ViewActions.click());
            click(payment, R.id.buttonBookingPaymentApprove);
            BookingCompletionActivity completion = awaitActivity(BookingCompletionActivity.class);
            assertEquals(beforeCount + 1,
                    data.getAppointmentRequestsForUser(patient.getId(), UserRole.PATIENT).size());
            onView(withId(R.id.buttonBookingCompletionList)).check(matches(isCompletelyDisplayed()));
            click(completion, R.id.buttonBookingCompletionList);
            ClientBookingHistoryActivity history = awaitActivity(ClientBookingHistoryActivity.class);
            assertNavigationBounds(history, R.id.clientNavScheduleHistory);

            click(history, R.id.clientNavHome);
            assertSame(home, awaitActivity(MainActivity.class));
            assertNavigationBounds(home, R.id.clientNavHome);
            click(home, R.id.clientNavScheduleHistory);
            history = awaitActivity(ClientBookingHistoryActivity.class);
            assertNavigationBounds(history, R.id.clientNavScheduleHistory);
            click(history, R.id.clientNavCompanionRoom);
            CompanionChatActivity chat = awaitActivity(CompanionChatActivity.class);
            assertNavigationBounds(chat, R.id.clientNavCompanionRoom);
            click(chat, R.id.clientNavProfile);
            ClientProfileActivity profile = awaitActivity(ClientProfileActivity.class);
            assertNavigationBounds(profile, R.id.clientNavProfile);
            click(profile, R.id.clientNavHome);
            assertSame(home, awaitActivity(MainActivity.class));
            assertNavigationBounds(home, R.id.clientNavHome);
            click(home, R.id.clientNavScheduleHistory);
            awaitActivity(ClientBookingHistoryActivity.class);
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            assertSame(home, awaitActivity(MainActivity.class));
            assertNavigationBounds(home, R.id.clientNavHome);
            click(home, R.id.clientNavScheduleHistory);
            assertNavigationBounds(awaitActivity(ClientBookingHistoryActivity.class),
                    R.id.clientNavScheduleHistory);
            assertEquals(beforeCount + 1,
                    data.getAppointmentRequestsForUser(patient.getId(), UserRole.PATIENT).size());
        }
    }

    private void fillLocalBooking(BookingActivity activity) {
        instrumentation.runOnMainSync(() -> {
            try {
                Field field = BookingActivity.class.getDeclaredField("formBinder");
                field.setAccessible(true);
                BookingFormBinder form = (BookingFormBinder) field.get(activity);
                assertNotNull(form);
                form.applyHospitalSelection(new BookingHospitalSelection("합성 검증 병원", "내과"));
                Calendar appointment = Calendar.getInstance();
                appointment.add(Calendar.DAY_OF_MONTH, 7);
                form.applyAppointmentAt(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
                        .format(appointment.getTime()));
                form.applyMeetingLocationSelection(new BookingMeetingLocationSelection("test", "1층 로비"));
                form.applyHealthProfileSelection(new BookingHealthProfileSelection(
                        "화면 이동용 합성 기록", "", "", BookingMobilitySupport.INDEPENDENT));
                ((TextView) activity.findViewById(R.id.inputBookingLinkedName)).setText("합성 보호자");
                ((TextView) activity.findViewById(R.id.inputBookingLinkedPhone)).setText("01000000002");
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("로컬 예약 폼 준비 실패", error);
            }
        });
    }

    private void awaitIme(Activity activity, boolean expected) {
        AtomicReference<Boolean> visible = new AtomicReference<>();
        for (int attempt = 0; attempt < 80; attempt++) {
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(
                        activity.findViewById(android.R.id.content));
                visible.set(insets == null ? null : insets.isVisible(WindowInsetsCompat.Type.ime()));
            });
            if (Boolean.valueOf(expected).equals(visible.get())) {
                return;
            }
            SystemClock.sleep(50L);
        }
        throw new AssertionError("실제 키보드 표시 상태: " + visible.get() + ", 기대: " + expected);
    }

    private void scrollAboveNavigation(Activity activity, int id) {
        onView(withId(id)).perform(scrollTo());
        instrumentation.runOnMainSync(() -> {
            View target = activity.findViewById(id);
            View navigation = activity.findViewById(R.id.clientBottomNavigation);
            int[] targetPosition = new int[2];
            int[] navigationPosition = new int[2];
            target.getLocationOnScreen(targetPosition);
            navigation.getLocationOnScreen(navigationPosition);
            int gap = Math.round(16 * activity.getResources().getDisplayMetrics().density);
            int overlap = targetPosition[1] + target.getHeight() + gap - navigationPosition[1];
            // Espresso scrollTo는 위에 겹친 고정 메뉴를 모른다. 실제 스크롤로 버튼을 메뉴 위에 둔다.
            if (overlap > 0) {
                ((ScrollView) activity.findViewById(R.id.scrollBooking)).scrollBy(0, overlap);
            }
        });
        instrumentation.waitForIdleSync();
        onView(withId(id)).check(matches(isCompletelyDisplayed()));
        instrumentation.runOnMainSync(() -> {
            Rect target = new Rect();
            Rect navigation = new Rect();
            assertTrue(activity.findViewById(id).getGlobalVisibleRect(target));
            assertTrue(activity.findViewById(R.id.clientBottomNavigation).getGlobalVisibleRect(navigation));
            assertTrue("접수 버튼이 하단 메뉴에 가려짐", target.bottom <= navigation.top);
        });
    }

    private void assertNavigationBounds(Activity activity, int selectedItem) {
        onView(withId(R.id.clientBottomNavigation)).check(matches(isCompletelyDisplayed()));
        instrumentation.runOnMainSync(() -> {
            BottomNavigationView navigation = activity.findViewById(R.id.clientBottomNavigation);
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(navigation);
            assertNotNull(insets);
            float density = activity.getResources().getDisplayMetrics().density;
            // 홈 Figma 레이아웃은 공통 메뉴의 84dp를 88dp로 재정의한다.
            int baseHeightDp = activity instanceof MainActivity ? 88 : 84;
            int safeBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()
                    | WindowInsetsCompat.Type.displayCutout()).bottom;
            assertEquals(activity.getClass().getSimpleName() + ": 하단 메뉴 높이, density="
                            + density + ", safeBottom=" + safeBottom + ", layoutHeight="
                            + navigation.getLayoutParams().height + ", paddingBottom="
                            + navigation.getPaddingBottom(),
                    Math.round(baseHeightDp * density) + safeBottom, navigation.getHeight());
            assertEquals(selectedItem, navigation.getSelectedItemId());
            for (int id : new int[]{R.id.clientNavHome, R.id.clientNavScheduleHistory,
                    R.id.clientNavCompanionRoom, R.id.clientNavProfile}) {
                View item = navigation.findViewById(id);
                Rect visible = new Rect();
                assertTrue(item.getGlobalVisibleRect(visible));
                assertEquals(item.getHeight(), visible.height());
                assertEquals(item.getWidth(), visible.width());
                assertTrue(visible.height() >= Math.round(48 * density));
            }
        });
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
        instrumentation.runOnMainSync(() -> assertTrue(
                "클릭 처리 실패: " + activity.getResources().getResourceEntryName(id),
                activity.findViewById(id).performClick()));
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
