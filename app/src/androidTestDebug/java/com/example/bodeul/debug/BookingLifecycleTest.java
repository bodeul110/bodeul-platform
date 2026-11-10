package com.example.bodeul.debug;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 동기식 Mock이 숨기는 선택기 복귀·저장 중 화면 재생성 경쟁 조건을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class BookingLifecycleTest {
    private Fixture fixture;

    @Before public void setUp() {
        fixture = new Fixture();
        BookingLifecyclePreviewActivity.testAuth = fixture.auth;
        BookingLifecyclePreviewActivity.testBooking = fixture.booking;
        BookingLifecyclePreviewActivity.selectionRequestCode = -1;
    }

    @After public void tearDown() {
        BookingLifecyclePreviewActivity.testAuth = null;
        BookingLifecyclePreviewActivity.testBooking = null;
    }

    @Test public void selectorResultWinsOverRestoredFormAfterDelayedDashboard() {
        for (String kind : new String[]{"hospital", "appointment", "health", "location"}) {
            try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
                prepareSelector(scenario, kind);
                fixture.delayDashboard = true;
                scenario.recreate();
                scenario.onActivity(activity -> {
                    returnSelection(activity, kind);
                    fixture.flushDashboard();
                    assertSelection(activity, kind);
                    assertEquals("합성 보호자", text(activity, R.id.inputBookingLinkedName));
                });
                fixture.delayDashboard = false;
            }
        }
    }

    @Test public void inFlightCreateSurvivesRecreationAndCompletesOnlyOnNewScreen() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            BookingLifecyclePreviewActivity[] old = new BookingLifecyclePreviewActivity[1];
            scenario.onActivity(activity -> {
                fillAndSubmit(activity);
                old[0] = activity;
                assertEquals(1, fixture.saveCalls);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse("저장 응답 전에는 제출 버튼이 잠겨야 합니다.", activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                activity.findViewById(R.id.buttonSubmitBooking).performClick();
                assertEquals(0, activity.paymentLaunchCount);
                assertEquals(1, fixture.saveCalls);
                fixture.finishSave();
                assertEquals(1, activity.completionCount);
                assertEquals(0, old[0].completionCount);
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(0, activity.completionCount));
        }
    }

    @Test public void pendingSelectionSurvivesSecondRecreationBeforeDashboard() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            prepareSelector(scenario, "hospital");
            fixture.delayDashboard = true;
            scenario.recreate();
            scenario.onActivity(activity -> returnSelection(activity, "hospital"));
            scenario.recreate();
            scenario.onActivity(activity -> {
                fixture.flushDashboard();
                assertSelection(activity, "hospital");
                assertEquals("합성 보호자", text(activity, R.id.inputBookingLinkedName));
            });
            fixture.delayDashboard = false;
            scenario.recreate();
            scenario.onActivity(activity -> assertSelection(activity, "hospital"));
        }
    }

    @Test public void selectionAfterDashboardRestorationIsAppliedOnce() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            prepareSelector(scenario, "appointment");
            scenario.recreate();
            scenario.onActivity(activity -> {
                returnSelection(activity, "appointment");
                assertSelection(activity, "appointment");
                form(activity).applyAppointmentAt("2026-12-22 14:30");
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals("2026-12-22 14:30", form(activity).saveInputState().getString("appointment")));
        }
    }

    @Test public void cancelledSelectionKeepsPreviousInput() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            prepareSelector(scenario, "hospital");
            fixture.delayDashboard = true;
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.getActivityResultRegistry().dispatchResult(BookingLifecyclePreviewActivity.selectionRequestCode,
                        Activity.RESULT_CANCELED, null);
                fixture.flushDashboard();
                assertEquals("기존 병원", form(activity).getHospitalSelection().getHospitalName());
                assertEquals("old-point", form(activity).getMeetingLocationSelection().getPointId());
            });
        }
    }

    @Test public void selectorResultIsDiscardedAfterAccountChanges() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            prepareSelector(scenario, "health");
            fixture.signInGuardian();
            fixture.delayDashboard = true;
            scenario.recreate();
            scenario.onActivity(activity -> {
                returnSelection(activity, "health");
                fixture.flushDashboard();
                assertNotEquals("새 건강 정보", form(activity).getHealthProfileSelection().getPatientConditionSummary());
                assertNotEquals("합성 보호자", text(activity, R.id.inputBookingLinkedName));
                assertEquals(UserRole.GUARDIAN, ((User) field(activity, "currentUser")).getRole());
            });
        }
    }

    @Test public void selectorResultIsDiscardedWhenEditedRequestBecomesUneditable() {
        fixture.data.getAppointmentRequestDetail("request-1").getAppointmentRequest().setStatus(AppointmentStatus.REQUESTED);
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launchEdit()) {
            prepareSelector(scenario, "hospital");
            fixture.data.getAppointmentRequestDetail("request-1").getAppointmentRequest().setStatus(AppointmentStatus.CANCELED);
            fixture.delayDashboard = true;
            scenario.recreate();
            scenario.onActivity(activity -> {
                returnSelection(activity, "hospital");
                fixture.flushDashboard();
                assertNull(field(activity, "editingRequest"));
                assertEquals(android.view.View.VISIBLE, activity.findViewById(R.id.bookingHistoryPanel).getVisibility());
                assertNotEquals("새 병원", form(activity).getHospitalSelection().getHospitalName());
            });
        }
    }

    @Test public void inFlightUpdateSurvivesRecreation() {
        fixture.data.getAppointmentRequestDetail("request-1").getAppointmentRequest().setStatus(AppointmentStatus.REQUESTED);
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launchEdit()) {
            scenario.onActivity(BookingLifecycleTest::fillAndSubmit);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertFalse(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                assertEquals(1, fixture.saveCalls);
                assertEquals("updateAppointmentRequest", fixture.lastSaveMethod);
                fixture.finishSave();
                assertEquals(1, activity.completionCount);
                assertTrue(activity.completedUpdate);
            });
        }
    }

    @Test public void saveFailureAfterRecreationUnlocksWithoutReplaying() {
        fixture.failSave = true;
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            scenario.onActivity(BookingLifecycleTest::fillAndSubmit);
            scenario.recreate();
            scenario.onActivity(activity -> {
                fixture.finishSave();
                assertEquals(0, activity.completionCount);
                assertEquals(1, fixture.saveCalls);
                assertTrue(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                assertEquals("합성 보호자", text(activity, R.id.inputBookingLinkedName));
                assertEquals("기존 병원", form(activity).getHospitalSelection().getHospitalName());
                activity.findViewById(R.id.buttonSubmitBooking).performClick();
                assertEquals(1, activity.paymentLaunchCount);
                assertEquals(1, fixture.saveCalls);
            });
        }
    }

    @Test public void completionWaitsForNewScreenDashboard() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            scenario.onActivity(BookingLifecycleTest::fillAndSubmit);
            fixture.delayDashboard = true;
            scenario.recreate();
            scenario.onActivity(activity -> {
                fixture.finishSave();
                assertEquals(0, activity.completionCount);
                assertFalse(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                fixture.flushDashboard();
                assertEquals(1, activity.completionCount);
                fixture.flushDashboard();
                assertEquals(1, activity.completionCount);
            });
        }
    }

    @Test public void stoppedScreenDoesNotConsumeCompletionBeforeAccountRecheck() {
        try (ActivityScenario<BookingLifecyclePreviewActivity> scenario = launch()) {
            scenario.onActivity(BookingLifecycleTest::fillAndSubmit);
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                fixture.finishSave();
                fixture.signInGuardian();
            });
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertEquals(0, activity.completionCount);
                assertEquals(UserRole.GUARDIAN, ((User) field(activity, "currentUser")).getRole());
                assertFalse(((BookingSubmissionViewModel) field(activity, "submission")).isBusy());
                assertNotEquals("합성 보호자", text(activity, R.id.inputBookingLinkedName));
            });
        }
    }

    private ActivityScenario<BookingLifecyclePreviewActivity> launch() {
        return ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                BookingLifecyclePreviewActivity.class));
    }

    private ActivityScenario<BookingLifecyclePreviewActivity> launchEdit() {
        return ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                BookingLifecyclePreviewActivity.class).putExtra("editRequestId", "request-1"));
    }

    private void prepareSelector(ActivityScenario<BookingLifecyclePreviewActivity> scenario, String kind) {
        scenario.onActivity(activity -> {
            fill(activity);
            activity.findViewById(R.id.buttonBookingNext).performClick();
            int selector = "hospital".equals(kind) ? R.id.buttonBookingSelectHospital
                    : "appointment".equals(kind) ? R.id.buttonBookingAppointmentSummary
                    : "health".equals(kind) ? R.id.buttonBookingHealthProfile : R.id.buttonBookingSelectMeetingPlace;
            activity.findViewById(selector).performClick();
            assertNotEquals(-1, BookingLifecyclePreviewActivity.selectionRequestCode);
        });
    }

    private static void returnSelection(BookingLifecyclePreviewActivity activity, String kind) {
        Intent data = new Intent();
        switch (kind) {
            case "hospital": data.putExtra("initialHospital", "새 병원").putExtra("initialDepartment", "외과")
                    .putExtra("hospitalLatitude", 37.6).putExtra("hospitalLongitude", 127.1); break;
            case "appointment": data.putExtra("selectedAppointmentAt", "2026-12-21 11:30"); break;
            case "health": data.putExtra("patientConditionSummary", "새 건강 정보").putExtra("medicationSummary", "새 복약 정보")
                    .putExtra("specialNotes", "새 전달 사항").putExtra("mobilitySupport", "WHEELCHAIR"); break;
            default: data.putExtra("pointId", "new-point").putExtra("meetingPlace", "새 만남 장소");
        }
        assertTrue(activity.getActivityResultRegistry().dispatchResult(
                BookingLifecyclePreviewActivity.selectionRequestCode, Activity.RESULT_OK, data));
    }

    private static void assertSelection(BookingLifecyclePreviewActivity activity, String kind) {
        BookingFormBinder form = form(activity);
        switch (kind) {
            case "hospital":
                assertEquals("새 병원", form.getHospitalSelection().getHospitalName());
                assertEquals(37.6, form.getHospitalSelection().getHospitalLatitude(), 0.00001);
                assertEquals("", form.getMeetingLocationSelection().getPointId());
                break;
            case "appointment": assertEquals("2026-12-21 11:30", form.saveInputState().getString("appointment")); break;
            case "health":
                assertEquals("새 건강 정보", form.getHealthProfileSelection().getPatientConditionSummary());
                assertEquals("새 복약 정보", form.getHealthProfileSelection().getMedicationSummary());
                assertEquals("새 전달 사항", form.getHealthProfileSelection().getSpecialNotes());
                break;
            default: assertEquals("new-point", form.getMeetingLocationSelection().getPointId());
                assertEquals("새 만남 장소", form.getMeetingLocationSelection().getMeetingPlace());
        }
    }

    private static void fill(BookingLifecyclePreviewActivity activity) {
        activity.findViewById(R.id.buttonBookingSelf).performClick();
        set(activity, R.id.inputBookingLinkedName, "합성 보호자");
        set(activity, R.id.inputBookingLinkedPhone, "010-0000-0002");
        set(activity, R.id.inputBookingLinkedEmail, "guardian@example.com");
        set(activity, R.id.inputBookingHealthSummary, "기존 건강 정보");
        form(activity).applyHospitalSelection(new BookingHospitalSelection("기존 병원", "내과", 37.5, 127.0));
        form(activity).applyAppointmentAt("2026-12-20 10:30");
        form(activity).applyMeetingLocationSelection(new BookingMeetingLocationSelection("old-point", "기존 만남 장소"));
    }

    private static void fillAndSubmit(BookingLifecyclePreviewActivity activity) {
        fill(activity);
        for (int i = 0; i < 3; i++) activity.findViewById(R.id.buttonBookingNext).performClick();
        activity.findViewById(R.id.buttonBookingPaymentCard).performClick();
        assertNotNull("전송 전 유효한 초안을 준비해야 합니다.", form(activity).buildDraft());
        activity.findViewById(R.id.buttonSubmitBooking).performClick();
        assertNotNull(field(activity, "pendingSubmissionDraft"));
        activity.approvePayment();
        assertTrue(((BookingSubmissionViewModel) field(activity, "submission")).isBusy());
    }

    private static Object field(BookingActivity activity, String name) {
        try {
            java.lang.reflect.Field field = BookingActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static BookingFormBinder form(BookingActivity activity) { return (BookingFormBinder) field(activity, "formBinder"); }
    private static void set(BookingActivity activity, int id, String value) { ((TextView) activity.findViewById(id)).setText(value); }
    private static String text(BookingActivity activity, int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }

    private static final class Fixture {
        final MockBodeulRepository data = new MockBodeulRepository();
        final MockAuthRepository auth = new MockAuthRepository(data);
        final BookingRepository delegate = new MockBookingRepository(data);
        final List<Runnable> dashboards = new ArrayList<>();
        final List<Runnable> saves = new ArrayList<>();
        boolean delayDashboard;
        boolean failSave;
        int saveCalls;
        String lastSaveMethod;
        final BookingRepository booking = (BookingRepository) Proxy.newProxyInstance(
                BookingRepository.class.getClassLoader(), new Class<?>[]{BookingRepository.class}, (proxy, method, args) -> {
                    if ("getMyAppointmentRequests".equals(method.getName()) && delayDashboard) {
                        dashboards.add(() -> delegate.getMyAppointmentRequests((User) args[0], callback(args[1])));
                        return null;
                    }
                    if ("createAppointmentRequest".equals(method.getName()) || "updateAppointmentRequest".equals(method.getName())) {
                        saveCalls++;
                        lastSaveMethod = method.getName();
                        RepositoryCallback<AppointmentRequest> callback = callback(args[args.length - 1]);
                        if (failSave) {
                            saves.add(() -> callback.onError("테스트용 저장 실패"));
                            return null;
                        }
                        args[args.length - 1] = new RepositoryCallback<AppointmentRequest>() {
                            @Override public void onSuccess(AppointmentRequest request) { saves.add(() -> callback.onSuccess(request)); }
                            @Override public void onError(String message) { saves.add(() -> callback.onError(message)); }
                        };
                    }
                    return method.invoke(delegate, args);
                });

        Fixture() {
            signIn(UserRole.PATIENT, R.string.demo_account_patient_email);
        }
        void signInGuardian() { signIn(UserRole.GUARDIAN, R.string.demo_account_guardian_email); }
        void signIn(UserRole role, int emailRes) {
            android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            auth.signIn(context.getString(emailRes), context.getString(R.string.demo_account_password),
                    role, new RepositoryCallback<User>() {
                        @Override public void onSuccess(User user) { }
                        @Override public void onError(String message) { throw new AssertionError(message); }
                    });
        }
        void flushDashboard() {
            List<Runnable> pending = new ArrayList<>(dashboards);
            dashboards.clear();
            pending.forEach(Runnable::run);
        }
        void finishSave() { assertEquals(1, saves.size()); saves.remove(0).run(); }
        @SuppressWarnings("unchecked") private static <T> RepositoryCallback<T> callback(Object value) { return (RepositoryCallback<T>) value; }
    }
}
