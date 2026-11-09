package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.*;
import com.google.android.material.textfield.TextInputLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;

/** 모의 저장소로 실제 예약 Activity의 단계·상태 복원·기존 계약을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class BookingFigmaPreviewTest {
    @Test public void entryMatchesFinalDesignAndGuardianDoesNotBypassPolicy() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            onView(withText(R.string.final_booking_who)).check(matches(isDisplayed()));
            onView(withId(R.id.bookingFooter)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            capture("entry");
            onView(withId(R.id.buttonBookingGuardian)).perform(click());
            onView(withText(R.string.booking_guardian_creation_blocked_title)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button1)).perform(click());
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.bookingContactFields).getVisibility());
                assertNull(field(activity, "pendingSubmissionDraft"));
            });
        }
    }

    @Test public void participantValidationKeepsInvalidValuesAndReportsRequiredFields() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingSelf)).perform(click());
            onView(withId(R.id.buttonBookingNext)).perform(click());
            scenario.onActivity(activity -> {
                assertNotNull(((TextInputLayout) activity.findViewById(R.id.layoutBookingLinkedName)).getError());
                assertNotNull(((TextInputLayout) activity.findViewById(R.id.layoutBookingLinkedPhone)).getError());
                assertTrue(activity.findViewById(R.id.bookingStepParticipant).isShown());
                set(activity, R.id.inputBookingLinkedName, "합성 보호자");
                set(activity, R.id.inputBookingLinkedPhone, "010-0000-0002");
                set(activity, R.id.inputBookingLinkedEmail, "invalid@");
                activity.findViewById(R.id.buttonBookingNext).performClick();
                assertNotNull(((TextInputLayout) activity.findViewById(R.id.layoutBookingLinkedEmail)).getError());
            });
            onView(withId(R.id.inputBookingLinkedEmail)).perform(scrollTo());
            capture("contact-error");
        }
    }

    @Test public void stagesKeepDataAndBuildTheSameBookingContract() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            capture("contact");
            onView(withId(R.id.buttonBookingNext)).perform(click());
            onView(withText(R.string.final_booking_visit)).check(matches(isDisplayed()));
            capture("visit-empty");
            onView(withId(R.id.buttonBookingNext)).perform(click());
            onView(withId(R.id.textBookingHospitalSelectionError)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> fillVisit(form(activity)));
            capture("visit-selected");
            onView(withId(R.id.buttonBookingNext)).perform(click());
            onView(withText(R.string.final_booking_health)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                set(activity, R.id.inputBookingHealthSummary, "고혈압, 천천히 보행");
                set(activity, R.id.inputBookingMedicationSummary, "아침 복용약");
                set(activity, R.id.inputBookingSpecialNotes, "병원 입구에서 만나요");
            });
            capture("health");
            onView(withId(R.id.buttonBookingNext)).perform(click());
            onView(withText(R.string.final_booking_payment)).check(matches(isDisplayed()));
            capture("payment");
            onView(withId(R.id.textBookingEstimateFinal)).perform(scrollTo());
            capture("payment-bottom");
            scenario.onActivity(activity -> {
                BookingRequestDraft draft = form(activity).buildDraft();
                assertNotNull(draft);
                assertEquals("합성 보호자", draft.getLinkedParticipantName());
                assertEquals("검증 병원", draft.getHospitalName());
                assertEquals("내과", draft.getDepartmentName());
                assertEquals("2026-12-20 10:30", draft.getAppointmentAt());
                assertEquals("1층 안내데스크", draft.getMeetingPlace());
                assertEquals("고혈압, 천천히 보행", draft.getPatientConditionSummary());
                assertEquals("아침 복용약", draft.getMedicationSummary());
                assertEquals("병원 입구에서 만나요", draft.getSpecialNotes());
                assertEquals(40000, draft.getPriceSummary().getFinalPrice());
                assertTextFits(activity.findViewById(R.id.bookingRoot));
            });
            onView(withId(R.id.buttonBackBooking)).perform(click());
            onView(withId(R.id.inputBookingHealthSummary)).check(matches(withText("고혈압, 천천히 보행")));
        }
    }

    @Test public void recreationPreservesPartialFieldsCoordinatesAndCurrentStage() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            onView(withId(R.id.buttonBookingNext)).perform(click());
            scenario.onActivity(activity -> {
                fillVisit(form(activity));
                set(activity, R.id.inputBookingMedicationSummary, "아직 작성 중 ");
            });
            scenario.recreate();
            onView(withText(R.string.final_booking_visit)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                assertEquals("아직 작성 중 ", ((TextView) activity.findViewById(R.id.inputBookingMedicationSummary)).getText().toString());
                assertEquals("합성 보호자", ((TextView) activity.findViewById(R.id.inputBookingLinkedName)).getText().toString());
                assertEquals(37.5, form(activity).getHospitalSelection().getHospitalLatitude(), 0.00001);
                assertEquals("point-1", form(activity).getMeetingLocationSelection().getPointId());
                assertEquals("1층 안내데스크", form(activity).getMeetingLocationSelection().getMeetingPlace());
            });
            onView(withId(R.id.buttonBookingNext)).perform(click());
            scenario.onActivity(activity -> set(activity, R.id.inputBookingHealthSummary, "없음"));
            onView(withId(R.id.buttonBookingNext)).perform(click());
            scenario.recreate();
            onView(withText(R.string.final_booking_payment)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertNotNull(form(activity).buildDraft()));
        }
    }

    @Test public void changingHospitalInvalidatesOnlyItsMeetingPoint() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                BookingFormBinder form = form(activity);
                fillVisit(form);
                set(activity, R.id.inputBookingHealthSummary, "기존 건강 정보");
                form.applyHospitalSelection(new BookingHospitalSelection("다른 병원", "외과", 37.6, 127.1));
                assertEquals("", form.getMeetingLocationSelection().getMeetingPlace());
                assertEquals("기존 건강 정보", form.getHealthProfileSelection().getPatientConditionSummary());
                assertFalse(form.validateStep(BookingStep.VISIT));
            });
        }
    }

    @Test public void editingLegacyPricesAndPaymentLocksSurviveInputRestore() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                BookingFormBinder form = form(activity);
                User patient = new User("patient-1", UserRole.PATIENT, "합성 환자", "patient@example.com", "010-0000-0001");
                for (String method : new String[]{"CARD", "BANK_TRANSFER"}) {
                    AppointmentRequest legacy = legacy(method);
                    form.bindEditMode(patient, legacy);
                    Bundle snapshot = form.saveInputState();
                    form.bindEditMode(patient, legacy);
                    form.restoreInputState(snapshot);
                    assertEquals("예상 결제 금액 · 96,000원", text(activity, R.id.textBookingEstimateFinal));
                    form.setLoading(true);
                    form.setLoading(false);
                    assertFalse(activity.findViewById(R.id.buttonBookingCouponFamily).isEnabled());
                    if ("BANK_TRANSFER".equals(method)) {
                        assertFalse(activity.findViewById(R.id.buttonBookingTripOneWay).isEnabled());
                        assertFalse(activity.findViewById(R.id.buttonBookingMobilityIndependent).isEnabled());
                        assertFalse(activity.findViewById(R.id.buttonBookingPaymentCard).isEnabled());
                    }
                }
                form.bindCreateMode(patient);
                assertEquals("예상 결제 금액 · 40,000원", text(activity, R.id.textBookingEstimateFinal));
            });
        }
    }

    @Test public void quotePolicyAndLegacyCouponVisibilityArePreserved() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            goToPayment(scenario);
            onView(withId(R.id.textBookingPricePolicy)).perform(scrollTo()).check(matches(withText(R.string.booking_price_mvp_policy)));
            onView(withId(R.id.buttonBookingTripRoundTrip)).perform(scrollTo(), click());
            onView(withId(R.id.textBookingEstimateFinal)).perform(scrollTo()).check(matches(withText("예상 결제 금액 · 40,000원")));
            onView(withId(R.id.buttonBookingCouponFamily)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void paymentCancellationRetainsDraftAndPreventsDuplicateLaunch() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            goToPayment(scenario);
            onView(withId(R.id.buttonSubmitBooking)).perform(click());
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.buttonSubmitBooking).performClick();
                assertEquals(1, activity.paymentLaunchCount);
                assertNotNull(field(activity, "pendingSubmissionDraft"));
                assertFalse(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                paymentResult(activity, android.app.Activity.RESULT_CANCELED, null);
                assertNull(field(activity, "pendingSubmissionDraft"));
                assertTrue(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                assertEquals("합성 보호자", form(activity).buildDraft().getLinkedParticipantName());
                assertEquals(0, activity.completionCount);
                activity.findViewById(R.id.buttonSubmitBooking).performClick();
                assertEquals(2, activity.paymentLaunchCount);
            });
        }
    }

    @Test public void approvedRequestUsesExistingRepositoryAndCompletesOnce() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            goToPayment(scenario);
            onView(withId(R.id.buttonSubmitBooking)).perform(click());
            scenario.onActivity(activity -> {
                paymentResult(activity, android.app.Activity.RESULT_OK, simulatedApproval());
                assertEquals(1, activity.completionCount);
                assertNotNull(activity.completedRequest);
                assertEquals("검증 병원", activity.completedRequest.getHospitalName());
                assertNull(field(activity, "pendingSubmissionDraft"));
                assertEquals(false, field(activity, "submitting"));
                assertFalse(activity.findViewById(R.id.bookingFooter).isShown());
                paymentResult(activity, android.app.Activity.RESULT_OK, simulatedApproval());
                assertEquals(1, activity.completionCount);
            });
        }
    }

    @Test public void recreatedPaymentDoesNotReplayAResultWithoutItsPendingDraft() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            goToPayment(scenario);
            onView(withId(R.id.buttonSubmitBooking)).perform(click());
            scenario.recreate();
            onView(withText(R.string.final_booking_payment)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                paymentResult(activity, android.app.Activity.RESULT_OK, simulatedApproval());
                assertEquals(0, activity.completionCount);
                assertNotNull(form(activity).buildDraft());
                assertTrue(activity.findViewById(R.id.buttonSubmitBooking).isEnabled());
                activity.findViewById(R.id.buttonSubmitBooking).performClick();
                assertEquals(1, activity.paymentLaunchCount);
            });
        }
    }

    @Test public void signedOutOwnerCannotSubmitAfterPaymentReturns() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            goToPayment(scenario);
            onView(withId(R.id.buttonSubmitBooking)).perform(click());
            scenario.onActivity(activity -> {
                ((AuthRepository) field(activity, "authRepository")).signOut();
                paymentResult(activity, android.app.Activity.RESULT_OK, simulatedApproval());
                assertEquals(0, activity.completionCount);
                assertNull(field(activity, "pendingSubmissionDraft"));
                assertNull(field(activity, "currentUser"));
            });
            onView(withId(R.id.bookingContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.bookingFooter)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void requiredHealthAndUnknownPaymentDoNotBuildRequests() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                BookingFormBinder form = form(activity);
                assertFalse(form.validateStep(BookingStep.HEALTH));
                form.bindEditMode(new User("patient-1", UserRole.PATIENT, "합성", "p@example.com", "01000000001"), legacy("UNKNOWN"));
                assertFalse(form.validateStep(BookingStep.PAYMENT));
                assertNull(form.buildDraft());
            });
        }
    }

    @Test public void historyAndBackDoNotDiscardTheDraft() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            onView(withId(R.id.buttonCloseBooking)).perform(click());
            onView(withId(R.id.bookingHistoryPanel)).check(matches(isDisplayed()));
            capture("history");
            onView(withId(R.id.buttonBackBooking)).perform(click());
            onView(withId(R.id.inputBookingLinkedName)).perform(scrollTo()).check(matches(withText("합성 보호자")));
            onView(withId(R.id.buttonBackBooking)).perform(click());
            onView(withText(R.string.final_booking_leave_title)).check(matches(isDisplayed()));
            onView(withId(android.R.id.button2)).perform(click());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    @Test public void authFailureHidesTheDraftAndActions() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            scenario.onActivity(activity -> ((AuthRepository) field(activity, "authRepository")).signOut());
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.bookingContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.bookingFooter)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void accountRoleChangeCannotReusePatientData() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            scenario.onActivity(activity -> ((AuthRepository) field(activity, "authRepository")).signIn(
                    activity.getString(R.string.demo_account_guardian_email), activity.getString(R.string.demo_account_password),
                    UserRole.GUARDIAN, new RepositoryCallback<User>() {
                        @Override public void onSuccess(User user) { }
                        @Override public void onError(String message) { throw new AssertionError(message); }
                    }));
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.bookingContactFields)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            scenario.onActivity(activity -> assertEquals("", text(activity, R.id.inputBookingLinkedName)));
            onView(withId(R.id.buttonBookingSelf)).perform(click());
            onView(withText(R.string.booking_guardian_creation_blocked_title)).check(matches(isDisplayed()));
        }
    }

    @Test public void fixedFooterRespectsBarsKeyboardAndLargeText() {
        try (ActivityScenario<BookingFigmaPreviewActivity> scenario = launch()) {
            selectAndFillContact(scenario);
            onView(withId(R.id.inputBookingLinkedPhone)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                keyboard.showSoftInput(activity.findViewById(R.id.inputBookingLinkedPhone), android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            });
            capture("keyboard");
            onView(withId(R.id.buttonBookingNext)).check(matches(isCompletelyDisplayed()));
            scenario.onActivity(activity -> {
                View root = activity.findViewById(R.id.bookingRoot);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                assertNotNull(insets);
                int bottom = Math.max(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                assertTrue("키보드 여백 반영", insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0);
                assertTrue(root.getPaddingBottom() >= bottom);
                View footer = activity.findViewById(R.id.bookingFooter);
                assertTrue(activity.findViewById(R.id.scrollBooking).getBottom() <= footer.getTop());
                assertTrue(footer.getBottom() <= root.getHeight() - bottom);
                assertTextFits(root);
                TextView next = activity.findViewById(R.id.buttonBookingNext);
                assertTrue(ColorUtils.calculateContrast(next.getCurrentTextColor(), activity.getColor(R.color.final_brand_primary)) >= 4.5);
            });
            onView(withId(R.id.inputBookingLinkedPhone)).perform(closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.buttonBookingNext)).check(matches(isCompletelyDisplayed()));
        }
    }

    private static ActivityScenario<BookingFigmaPreviewActivity> launch() {
        ActivityScenario<BookingFigmaPreviewActivity> scenario = ActivityScenario.launch(BookingFigmaPreviewActivity.createIntent(context()));
        scenario.onActivity(activity -> activity.holdPaymentForTest = true);
        return scenario;
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static void selectAndFillContact(ActivityScenario<BookingFigmaPreviewActivity> scenario) {
        onView(withId(R.id.buttonBookingSelf)).perform(click());
        scenario.onActivity(activity -> {
            set(activity, R.id.inputBookingLinkedName, "합성 보호자");
            set(activity, R.id.inputBookingLinkedPhone, "010-0000-0002");
            set(activity, R.id.inputBookingLinkedEmail, "guardian@example.com");
        });
    }
    private static void fillVisit(BookingFormBinder form) {
        form.applyHospitalSelection(new BookingHospitalSelection("검증 병원", "내과", 37.5, 127.0));
        form.applyAppointmentAt("2026-12-20 10:30");
        form.applyMeetingLocationSelection(new BookingMeetingLocationSelection("point-1", "1층 안내데스크"));
    }
    private static void goToPayment(ActivityScenario<BookingFigmaPreviewActivity> scenario) {
        selectAndFillContact(scenario);
        onView(withId(R.id.buttonBookingNext)).perform(click());
        scenario.onActivity(activity -> fillVisit(form(activity)));
        onView(withId(R.id.buttonBookingNext)).perform(click());
        scenario.onActivity(activity -> set(activity, R.id.inputBookingHealthSummary, "없음"));
        onView(withId(R.id.buttonBookingNext)).perform(click());
    }
    private static AppointmentRequest legacy(String method) {
        return new AppointmentRequest("legacy-price", "patient-1", "guardian-1", "검증 병원", "내과",
                "2026-12-20 10:30", "1층", "합성 기록", AppointmentStatus.REQUESTED, null,
                "합성 환자", "010-0000-0001", "patient@example.com", "합성 보호자", "010-0000-0002", "guardian@example.com",
                "이동 지원 요청", "", "WHEELCHAIR", "ROUND_TRIP", "ANY", method, "FAMILY", 69000, 37000, 10000, 96000);
    }
    private static Object field(BookingActivity activity, String name) {
        try {
            java.lang.reflect.Field field = BookingActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static BookingFormBinder form(BookingActivity activity) { return (BookingFormBinder) field(activity, "formBinder"); }
    private static Intent simulatedApproval() {
        return new Intent().putExtra("paymentStatus", "SIMULATED").putExtra("paymentProvider", "합성 결제 확인");
    }
    private static void paymentResult(BookingActivity activity, int code, Intent data) {
        try {
            java.lang.reflect.Method method = BookingActivity.class.getDeclaredMethod("handlePaymentApprovalResult", int.class, Intent.class);
            method.setAccessible(true);
            method.invoke(activity, code, data);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(BookingActivity activity, int id, String value) { ((TextView) activity.findViewById(id)).setText(value); }
    private static String text(BookingActivity activity, int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null && text.length() > 0) {
                assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 2);
                for (int i = 0; i < text.getLineCount(); i++) {
                    assertEquals("말줄임: " + text.getText(), 0, text.getLayout().getEllipsisCount(i));
                    assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(i) <= text.getLayout().getWidth() + 2);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // 이전 검증의 Toast가 다음 화면 증거를 가리지 않게 한다.
        android.os.SystemClock.sleep(3600);
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context().getFilesDir(), "booking-final");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
