package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingPaymentApproval;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.ui.booking.*;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 합성 인텐트로 실제 Activity의 확인·취소·재생성·배치를 검사한다. */
@RunWith(AndroidJUnit4.class)
public class BookingCompletionFigmaTest {
    private static final String LONG_NOTE = "합성 검증용 안내입니다. 병원 정문에서 만나 안내 데스크를 거쳐 진료실까지 이동합니다. "
            + "큰 글자에서도 주의사항이 생략되지 않아야 합니다. 예약자가 전달한 문장을 마지막까지 읽을 수 있는지 확인합니다.";

    @Test public void cardConsentSurvivesRecreationWithoutAuthorizingPayment() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("CARD")) {
            onView(withId(R.id.buttonBookingPaymentApprove)).check(matches(not(isEnabled())));
            onView(withId(R.id.textBookingPaymentAmount)).check(matches(withText("35,000원")));
            capture("payment-card-top");
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.checkBookingPaymentConsent)).check(matches(isChecked()));
            onView(withId(R.id.buttonBookingPaymentApprove)).check(matches(isEnabled())).perform(click());
            assertApproval(scenario, BookingPaymentStatus.PENDING);
        }
    }

    @Test public void bankTransferDoesNotCreateAnAccountOrConfirmADeposit() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("BANK_TRANSFER")) {
            onView(withId(R.id.textBookingPaymentBody)).check(matches(withText(containsString("실제 입금은 받지 않으며"))));
            capture("payment-bank-top");
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            capture("payment-bank-consent");
            onView(withId(R.id.buttonBookingPaymentApprove)).perform(click());
            assertApproval(scenario, BookingPaymentStatus.PENDING);
        }
    }

    @Test public void easyPayStillReturnsSimulationOnly() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("EASY_PAY")) {
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            onView(withId(R.id.buttonBookingPaymentApprove)).perform(click());
            assertApproval(scenario, BookingPaymentStatus.PENDING);
        }
    }

    @Test public void onSiteKeepsDeferredContract() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("ON_SITE")) {
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.buttonBookingPaymentApprove)).perform(click());
            assertApproval(scenario, BookingPaymentStatus.DEFERRED);
        }
    }

    @Test public void unknownMethodRemainsBlockedEvenIfConsentIsRestored() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("FUTURE_METHOD")) {
            onView(withId(R.id.checkBookingPaymentConsent)).check(matches(not(isEnabled())));
            scenario.onActivity(a -> ((CheckBox)a.findViewById(R.id.checkBookingPaymentConsent)).setChecked(true));
            scenario.recreate();
            onView(withId(R.id.buttonBookingPaymentApprove)).check(matches(not(isEnabled())));
            onView(withId(R.id.buttonBackBookingPayment)).perform(click());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test public void uncheckingConsentDisablesConfirmation() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("CARD")) {
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click(), click());
            onView(withId(R.id.buttonBookingPaymentApprove)).check(matches(not(isEnabled())));
        }
    }

    @Test public void backAfterConsentCancelsWithoutReturningApproval() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("BANK_TRANSFER")) {
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            onView(withId(R.id.buttonBackBookingPayment)).perform(click());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
            assertNull(BookingPaymentApprovalActivity.parseResult(scenario.getResult().getResultData()));
        }
    }

    @Test public void systemBackCancelsPayment() {
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = payment("CARD")) {
            scenario.onActivity(a -> a.getOnBackPressedDispatcher().onBackPressed());
            assertEquals(Activity.RESULT_CANCELED, scenario.getResult().getResultCode());
        }
    }

    @Test public void completionShowsSavedValuesAndNoInventedMatchingOrPaymentPromise() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            onView(withId(R.id.textBookingCompletionTitle)).check(matches(withText("동행 신청 완료!")));
            onView(withId(R.id.textBookingCompletionHospital)).check(matches(withText("합성 검증 병원")));
            onView(withId(R.id.textBookingCompletionDate)).check(matches(withText("2028. 02. 29 (화)")));
            onView(withId(R.id.textBookingCompletionTime)).check(matches(withText("오전 10:17")));
            capture("completion-top");
            onView(withId(R.id.textBookingCompletionMeetingPlace)).perform(scrollTo()).check(matches(withText("병원 정문 안내 데스크 앞")));
            onView(withId(R.id.textBookingCompletionNote)).check(matches(withText("합성 검증 주의사항")));
            onView(withId(R.id.textBookingCompletionPayment)).perform(scrollTo()).check(matches(withText(containsString(context().getString(R.string.booking_payment_status_awaiting_deposit)))));
            capture("completion-payment");
            scenario.recreate();
            onView(withId(R.id.textBookingCompletionHospital)).check(matches(withText("합성 검증 병원")));
            scenario.onActivity(a -> {
                String body = ((TextView)a.findViewById(R.id.textBookingCompletionBody)).getText().toString();
                assertFalse(body.contains("2일"));
                assertFalse(body.contains("자동 결제"));
                assertFalse(body.contains("매칭 완료"));
                assertTrue(ViewCompat.isAccessibilityHeading(a.findViewById(R.id.textBookingCompletionTitle)));
                assertTrue(ViewCompat.isAccessibilityHeading(a.findViewById(R.id.textBookingCompletionSummaryTitle)));
                assertEquals(70, Math.round(a.findViewById(R.id.imageBookingCompletionSuccess).getWidth() / a.getResources().getDisplayMetrics().density));
            });
        }
    }

    @Test public void updateDoesNotClaimNewMatchingOrPayment() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(true, false)) {
            onView(withId(R.id.textBookingCompletionTitle)).check(matches(withText("예약 수정 완료!")));
            onView(withId(R.id.textBookingCompletionBody)).check(matches(withText(R.string.booking_completion_body_updated)));
            scenario.recreate();
            onView(withId(R.id.textBookingCompletionTitle)).check(matches(withText("예약 수정 완료!")));
        }
    }

    @Test public void missingDataUsesFallbacksAndHidesUnusableActions() {
        Intent intent = new Intent(context(), BookingCompletionPreviewActivity.class);
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.buttonBookingCompletionDetail)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonBookingCompletionMap)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.textBookingCompletionMeetingPlace)).check(matches(withText(R.string.final_booking_completion_meeting_pending)));
            onView(withId(R.id.textBookingCompletionTime)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            scenario.recreate();
            onView(withId(R.id.buttonBookingCompletionDetail)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void legacyDateIsNotReplacedWithAnInventedTime() {
        Intent intent = completionIntent(false, false).putExtra("appointmentAt", "기존 일정 표기");
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.textBookingCompletionDate)).check(matches(withText("기존 일정 표기")));
            onView(withId(R.id.textBookingCompletionTime)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void everyPaymentStatusUsesExistingFormatterNotMethodBasedSuccess() {
        BookingPresentationFormatter formatter = new BookingPresentationFormatter(context());
        for (BookingPaymentStatus status : BookingPaymentStatus.values()) {
            Intent data = completionIntent(false, false).putExtra("paymentStatusCode", status.name());
            BookingCompletionScreenModel model = new BookingCompletionCoordinator(context())
                    .buildScreenModel(BookingCompletionSnapshot.fromIntent(data), false);
            assertTrue(model.getPayment().endsWith(formatter.toPaymentStatusLabel(status.name())));
            assertFalse(model.getBody().contains("자동 결제"));
        }
    }

    @Test public void snapshotRoundTripRetainsIdsCoordinatesAndExistingPaymentMetadata() {
        Intent source = completionIntent(false, false).putExtra("paymentApprovalCode", "synthetic-approval")
                .putExtra("paymentApprovedAt", "2028-02-28 12:00").putExtra("paymentProviderLabel", "합성 검증");
        Intent restored = new Intent();
        BookingCompletionSnapshot.fromIntent(source).writeToIntent(restored);
        BookingCompletionSnapshot snapshot = BookingCompletionSnapshot.fromIntent(restored);
        assertEquals("synthetic-request", snapshot.getRequestId());
        assertEquals("37.5,127.1(합성 검증 병원)", snapshot.getHospitalMapQuery());
        assertEquals("합성 검증 주의사항", snapshot.getSpecialNotes());
        assertEquals("AWAITING_DEPOSIT", snapshot.getPaymentStatusCode());
        assertEquals("synthetic-approval", snapshot.getPaymentApprovalCode());
        assertEquals("2028-02-28 12:00", snapshot.getPaymentApprovedAt());
        assertEquals("합성 검증", snapshot.getPaymentProviderLabel());
        assertEquals(35000, snapshot.getFinalPrice());
    }

    @Test public void detailLinkUsesSavedRequestAndKeepsCompletionForReturn() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            onView(withId(R.id.buttonBookingCompletionDetail)).perform(scrollTo(), click());
            scenario.onActivity(a -> {
                assertEquals(BookingStatusActivity.class.getName(), a.lastStartedIntent.getComponent().getClassName());
                assertEquals("synthetic-request", a.lastStartedIntent.getStringExtra("requestId"));
                assertFalse(a.isFinishing());
            });
        }
    }

    @Test public void mapLinkUsesSavedHospitalCoordinatesNotCurrentLocation() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            onView(withId(R.id.buttonBookingCompletionMap)).perform(scrollTo(), click());
            scenario.onActivity(a -> {
                assertEquals(Intent.ACTION_VIEW, a.lastStartedIntent.getAction());
                assertEquals("geo:0,0?q=37.5,127.1(합성 검증 병원)", android.net.Uri.decode(a.lastStartedIntent.getDataString()));
                assertFalse(a.isFinishing());
            });
        }
    }

    @Test public void confirmReturnsToHistoryAndDoesNotCreateAnotherBooking() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.buttonBookingCompletionList).performClick();
                assertEquals(ClientBookingHistoryActivity.class.getName(), a.lastStartedIntent.getComponent().getClassName());
                assertEquals(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP, a.lastStartedIntent.getFlags());
                assertTrue(a.isFinishing());
            });
        }
    }

    @Test public void closeOnlyDismissesCompletion() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.buttonBackBookingCompletion).performClick();
                assertNull(a.lastStartedIntent);
                assertTrue(a.isFinishing());
            });
        }
    }

    @Test public void longCompletionTextFitsAndLastLineRemainsReachable() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, true)) {
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingCompletionSheet)));
            capture("completion-long-top");
            onView(withId(R.id.textBookingCompletionNote)).perform(scrollTo());
            scenario.onActivity(a -> {
                TextView note = a.findViewById(R.id.textBookingCompletionNote);
                note.requestRectangleOnScreen(new Rect(0, note.getHeight() - note.getLineHeight(),
                        note.getWidth(), note.getHeight()), true);
                Rect visible = new Rect();
                assertTrue(note.getLocalVisibleRect(visible));
                assertTrue("주의사항 마지막 줄에 도달할 수 없음", visible.bottom >= note.getHeight() - note.getPaddingBottom());
            });
            capture("completion-long-note");
            onView(withId(R.id.textBookingCompletionAmount)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.buttonBookingCompletionList)).check(matches(isDisplayed()));
            scenario.onActivity(a -> assertSafeBottom(a.findViewById(R.id.buttonBookingCompletionList)));
        }
    }

    @Test public void sheetCoversNavigationAreaWithoutAccumulatingInsets() {
        try (ActivityScenario<BookingCompletionPreviewActivity> scenario = completion(false, false)) {
            for (int pass = 0; pass < 2; pass++) {
                scenario.onActivity(a -> {
                    View sheet = a.findViewById(R.id.bookingCompletionSheet);
                    View footer = a.findViewById(R.id.bookingCompletionBottomAction);
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(sheet);
                    assertNotNull(insets);
                    int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()).bottom;
                    assertEquals(Math.round(15 * a.getResources().getDisplayMetrics().density) + bottom,
                            footer.getPaddingBottom());
                    int[] position = new int[2];
                    sheet.getLocationOnScreen(position);
                    assertEquals(sheet.getRootView().getHeight(), position[1] + sheet.getHeight());
                    assertSafeBottom(a.findViewById(R.id.buttonBookingCompletionList));
                    ViewCompat.requestApplyInsets(sheet.getRootView());
                });
                scenario.recreate();
            }
        }
    }

    @Test public void paymentLongSummaryAndConsentFitWithoutCoveringAction() {
        Intent intent = paymentIntent("BANK_TRANSFER").putExtra("hospitalName", "합성 검증용 이름이 긴 대학병원 본관 진료센터")
                .putExtra("meetingPlace", LONG_NOTE);
        try (ActivityScenario<BookingPaymentApprovalActivity> scenario = ActivityScenario.launchActivityForResult(intent)) {
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingPaymentRoot)));
            onView(withId(R.id.checkBookingPaymentConsent)).perform(scrollTo(), click());
            onView(withId(R.id.buttonBookingPaymentApprove)).check(matches(isDisplayed()));
            scenario.onActivity(a -> {
                assertTrue(ViewCompat.isAccessibilityHeading(a.findViewById(R.id.textBookingPaymentHeader)));
                assertSafeBottom(a.findViewById(R.id.buttonBookingPaymentApprove));
                TextView button = a.findViewById(R.id.buttonBookingPaymentApprove);
                assertTrue(ColorUtils.calculateContrast(button.getCurrentTextColor(),
                        a.getColor(R.color.final_brand_primary)) >= 4.5);
            });
            capture("payment-long-consent");
        }
    }

    private static ActivityScenario<BookingPaymentApprovalActivity> payment(String method) {
        return ActivityScenario.launchActivityForResult(paymentIntent(method));
    }

    private static Intent paymentIntent(String method) {
        return new Intent(context(), BookingPaymentApprovalActivity.class)
                .putExtra("paymentMethodCode", method).putExtra("couponCode", "FIRST_VISIT").putExtra("finalPrice", 35000)
                .putExtra("hospitalName", "합성 검증 병원").putExtra("departmentName", "내과")
                .putExtra("appointmentAt", "2028-02-29 10:17").putExtra("meetingPlace", "병원 정문 안내 데스크 앞");
    }

    private static ActivityScenario<BookingCompletionPreviewActivity> completion(boolean updated, boolean longText) {
        return ActivityScenario.launch(completionIntent(updated, longText));
    }

    private static Intent completionIntent(boolean updated, boolean longText) {
        return new Intent(context(), BookingCompletionPreviewActivity.class)
                .putExtra("requestId", "synthetic-request").putExtra("isUpdated", updated)
                .putExtra("hospitalName", longText ? "합성 검증용 이름이 긴 대학병원 본관 진료센터" : "합성 검증 병원")
                .putExtra("hospitalLatitude", 37.5).putExtra("hospitalLongitude", 127.1)
                .putExtra("departmentName", longText ? "가정의학과 및 재활의학과 외래" : "내과")
                .putExtra("appointmentAt", "2028-02-29 10:17").putExtra("meetingPlace", "병원 정문 안내 데스크 앞")
                .putExtra("specialNotes", longText ? LONG_NOTE : "합성 검증 주의사항")
                .putExtra("paymentMethodCode", "BANK_TRANSFER").putExtra("paymentStatusCode", "AWAITING_DEPOSIT")
                .putExtra("couponCode", "FIRST_VISIT").putExtra("finalPrice", 35000);
    }

    private static void assertApproval(ActivityScenario<BookingPaymentApprovalActivity> scenario, BookingPaymentStatus expected) {
        assertEquals(Activity.RESULT_OK, scenario.getResult().getResultCode());
        BookingPaymentApproval result = BookingPaymentApprovalActivity.parseResult(scenario.getResult().getResultData());
        assertNotNull(result);
        assertTrue(result.isCompleted());
        assertEquals(expected, result.getStatus());
        assertEquals("", result.getApprovalCode());
        assertEquals(expected == BookingPaymentStatus.PENDING, result.getApprovedAt().isEmpty());
    }

    private static void assertTextFits(View view) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            Layout layout = text.getLayout();
            if (layout != null && text.length() > 0) {
                for (int i = 0; i < layout.getLineCount(); i++) {
                    assertEquals("텍스트 생략: " + text.getText(), 0, layout.getEllipsisCount(i));
                    // 자동 줄바꿈의 끝 공백은 제외하고 실제 글자가 차지하는 폭을 검사한다.
                    assertTrue("가로 잘림: " + text.getText(), layout.getLineMax(i) <= layout.getWidth() + 1);
                }
                assertTrue("세로 잘림: " + text.getText(), layout.getHeight()
                        <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) assertTextFits(group.getChildAt(i));
        }
    }

    private static void assertSafeBottom(View action) {
        Rect rect = new Rect();
        assertTrue(action.getGlobalVisibleRect(rect));
        assertEquals(action.getHeight(), rect.height());
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(action);
        assertNotNull(insets);
        assertTrue(rect.bottom <= action.getRootView().getHeight()
                - insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
    }

    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // 뷰 좌표뿐 아니라 스크롤 후 합성된 프레임이 캡처에 반영되도록 기다린다.
        android.os.SystemClock.sleep(400);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(context().getExternalFilesDir(null), "booking-completion-figma");
        assertTrue(directory.exists() || directory.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
