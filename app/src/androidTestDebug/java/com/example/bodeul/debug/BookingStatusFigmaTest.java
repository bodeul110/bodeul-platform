package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.*;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.report.GuardianReportActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/** 실제 계정·서버·지도 앱을 호출하지 않고 상세 표시와 기존 동작을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class BookingStatusFigmaTest {
    private static final String LONG_NOTE = "천천히 또박또박 안내해 주세요. 병원 정문 안내 데스크 오른쪽 의자에서 만납니다. "
            + "진료 설명은 잘 들릴 수 있도록 한 문장씩 전달하고, 처방 내용을 확인해 주세요. ".repeat(10)
            + "마지막 전달사항까지 빠짐없이 표시합니다.";
    private Fixture fixture;

    @Before public void setUp() {
        fixture = new Fixture();
        BookingStatusPreviewActivity.testAuth = fixture.auth;
        BookingStatusPreviewActivity.testBooking = fixture.booking;
    }
    @After public void tearDown() {
        BookingStatusPreviewActivity.testAuth = null;
        BookingStatusPreviewActivity.testBooking = null;
        // 앞선 오류 안내 토스트가 다음 화면 캡처에 남지 않게 한다.
        android.os.SystemClock.sleep(2200);
    }

    @Test public void allStatusesKeepActualDataAndRoleSpecificActions() {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            fixture.detail = detail(status, "PENDING", false);
            try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
                scenario.onActivity(a -> {
                    assertEquals("합성 검증 병원", text(a, R.id.textBookingStatusHeroTitle));
                    assertEquals("내과", text(a, R.id.textBookingStatusHeroBody));
                    assertEquals("예약번호 TEST-ONLY", text(a, R.id.textBookingStatusReference));
                    assertVisible(a, R.id.buttonBookingStatusEdit, status == AppointmentStatus.REQUESTED);
                    assertVisible(a, R.id.bookingStatusManager, status != AppointmentStatus.REQUESTED);
                    assertVisible(a, R.id.buttonBookingStatusSecondary, status != AppointmentStatus.CANCELED
                            && status != AppointmentStatus.IN_PROGRESS);
                    assertContains(a.findViewById(R.id.bookingStatusSummaryContainer), "2028. 02. 29");
                    assertContains(a.findViewById(R.id.bookingStatusSummaryContainer), "오전 10:17");
                    assertContains(a.findViewById(R.id.bookingStatusParticipantContainer), "합성 환자");
                    assertContains(a.findViewById(R.id.bookingStatusPaymentContainer), "40,000원");
                    assertTextFits(a.findViewById(R.id.bookingStatusRoot));
                });
                capture("status-" + status.name().toLowerCase());
            }
        }
    }

    @Test public void depositUsesServerStateAndMovesPaymentAheadOfPatient() {
        fixture.detail = detail(AppointmentStatus.MATCHED, "AWAITING_DEPOSIT", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                assertEquals("입금대기", text(a, R.id.textBookingStatusHeroBadge));
                LinearLayout information = a.findViewById(R.id.bookingStatusInformation);
                assertTrue(information.indexOfChild(a.findViewById(R.id.bookingStatusPaymentSection))
                        < information.indexOfChild(a.findViewById(R.id.bookingStatusPatientSection)));
                assertContains(a.findViewById(R.id.bookingStatusPaymentContainer), a.getString(R.string.booking_payment_status_awaiting_deposit));
                assertFalse(allText(a.findViewById(R.id.bookingStatusContentContainer)).contains("123-456"));
                assertFalse(allText(a.findViewById(R.id.bookingStatusContentContainer)).contains("3시간"));
            });
            capture("status-deposit");
        }
        for (AppointmentStatus status : new AppointmentStatus[]{AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.COMPLETED, AppointmentStatus.CANCELED}) {
            assertFalse(model(detail(status, "AWAITING_DEPOSIT", false)).isAwaitingDeposit());
        }
        for (BookingPaymentStatus payment : BookingPaymentStatus.values()) {
            assertEquals(payment == BookingPaymentStatus.AWAITING_DEPOSIT,
                    model(detail(AppointmentStatus.REQUESTED, payment.name(), false)).isAwaitingDeposit());
        }
    }

    @Test public void guardianHasNoEditCancelDepositOrConsentButKeepsReport() {
        fixture.user = user(UserRole.GUARDIAN);
        for (AppointmentStatus status : AppointmentStatus.values()) {
            fixture.detail = detail(status, "AWAITING_DEPOSIT", false);
            try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
                scenario.onActivity(a -> {
                    assertVisible(a, R.id.buttonBookingStatusEdit, false);
                    assertVisible(a, R.id.buttonBookingStatusBankTransferPayment, false);
                    assertVisible(a, R.id.buttonBookingStatusGuardianSharingConsent, false);
                    if (status == AppointmentStatus.REQUESTED || status == AppointmentStatus.MATCHED) {
                        assertVisible(a, R.id.buttonBookingStatusSecondary, false);
                    } else if (status == AppointmentStatus.IN_PROGRESS || status == AppointmentStatus.COMPLETED) {
                        a.findViewById(R.id.buttonBookingStatusSecondary).performClick();
                        assertEquals(GuardianReportActivity.class.getName(), a.lastStartedIntent.getComponent().getClassName());
                    }
                });
            }
        }
        assertEquals(0, fixture.cancelCalls);
    }

    @Test public void editAndExistingAuxiliaryRoutesUseSameSavedRequest() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingStatusEdit)).perform(scrollTo(), click());
            scenario.onActivity(a -> assertRoute(a, BookingActivity.class, "editRequestId"));
            onView(withId(R.id.buttonBookingStatusBankTransferPayment)).perform(scrollTo(), click());
            scenario.onActivity(a -> assertRoute(a, BankTransferPaymentActivity.class, "appointmentRequestId"));
            onView(withId(R.id.buttonBookingStatusGuardianSharingConsent)).perform(scrollTo(), click());
            scenario.onActivity(a -> assertRoute(a, GuardianSharingConsentActivity.class, "appointmentRequestId"));
        }
    }

    @Test public void mapUsesHospitalCoordinatesAndHandlesMissingMapApp() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingStatusMap)).perform(scrollTo(), click());
            scenario.onActivity(a -> {
                assertEquals(Intent.ACTION_VIEW, a.lastStartedIntent.getAction());
                assertEquals("geo:0,0?q=37.5,127.1(합성 검증 병원)", android.net.Uri.decode(a.lastStartedIntent.getDataString()));
                a.lastStartedIntent = null;
                a.mapUnavailable = true;
                a.findViewById(R.id.buttonBookingStatusMap).performClick();
                assertNull(a.lastStartedIntent);
                assertFalse(a.isFinishing());
            });
        }
    }

    @Test public void cancelDialogKeepDoesNotWrite() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingStatusSecondary)).perform(scrollTo(), click());
            onView(withText(R.string.booking_cancel_dialog_keep)).perform(click());
            scenario.onActivity(a -> assertEquals("매칭중", text(a, R.id.textBookingStatusHeroBadge)));
            assertEquals(0, fixture.cancelCalls);
        }
    }

    @Test public void confirmedCancelCallsRepositoryOnceAndRendersReturnedState() {
        fixture.detail = detail(AppointmentStatus.MATCHED, "DEPOSIT_CONFIRMED", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingStatusSecondary)).perform(scrollTo(), click());
            onView(withText(R.string.booking_cancel_dialog_confirm)).perform(click());
            scenario.onActivity(a -> {
                assertEquals("취소", text(a, R.id.textBookingStatusHeroBadge));
                assertVisible(a, R.id.buttonBookingStatusSecondary, false);
                assertVisible(a, R.id.buttonBookingStatusEdit, false);
            });
            assertEquals(1, fixture.cancelCalls);
        }
    }

    @Test public void completionPreservesReportFollowUpAndHistory() {
        fixture.detail = detail(AppointmentStatus.COMPLETED, "AUTHORIZED", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            onView(withId(R.id.buttonBookingStatusReport)).perform(scrollTo(), click());
            scenario.onActivity(a -> {
                assertContains(a.findViewById(R.id.bookingStatusReportContainer), "합성 진료 요약");
                assertContains(a.findViewById(R.id.bookingStatusReportContainer), "합성 진료 기록");
            });
            onView(withId(R.id.buttonBookingStatusPrimary)).perform(scrollTo(), click());
            scenario.onActivity(a -> assertRoute(a, BookingFollowUpActivity.class, "requestId"));
            onView(withId(R.id.buttonBookingStatusSecondary)).perform(scrollTo());
            scenario.onActivity(a -> {
                a.findViewById(R.id.buttonBookingStatusSecondary).performClick();
                assertEquals(ClientBookingHistoryActivity.class.getName(), a.lastStartedIntent.getComponent().getClassName());
                assertTrue(a.isFinishing());
            });
        }
    }

    @Test public void disclosuresSurviveRecreationAndServerRefresh() {
        fixture.detail = detail(AppointmentStatus.COMPLETED, "AUTHORIZED", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                for (int id : new int[]{R.id.buttonBookingStatusManager, R.id.buttonBookingStatusNote,
                        R.id.buttonBookingStatusProgress, R.id.buttonBookingStatusReport}) a.findViewById(id).performClick();
                fixture.latest().onSuccess(fixture.detail);
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                for (int id : new int[]{R.id.textBookingStatusManagerContact, R.id.textBookingStatusNote,
                        R.id.bookingStatusProgressDetails, R.id.bookingStatusReportContainer}) assertVisible(a, id, true);
                assertEquals(a.getString(R.string.final_booking_status_collapse),
                        ViewCompat.getStateDescription(a.findViewById(R.id.buttonBookingStatusNote)));
                a.findViewById(R.id.buttonBookingStatusNote).performClick();
                assertVisible(a, R.id.textBookingStatusNote, false);
            });
        }
    }

    @Test public void stoppedAndRecreatedScreenIgnoresOldObserver() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            RepositoryCallback<AppointmentRequestDetail> old = fixture.latest();
            scenario.recreate();
            scenario.onActivity(a -> {
                old.onSuccess(detail(AppointmentStatus.CANCELED, "CANCELED", false));
                assertEquals("매칭중", text(a, R.id.textBookingStatusHeroBadge));
                assertTrue(fixture.unsubscribeCalls >= 1);
                fixture.latest().onSuccess(detail(AppointmentStatus.MATCHED, "DEPOSIT_CONFIRMED", false));
                assertEquals("진행전", text(a, R.id.textBookingStatusHeroBadge));
            });
        }
    }

    @Test public void lateCompletedFollowUpCannotReplaceNewerRequestState() {
        fixture.delayFollowUp = true;
        fixture.detail = detail(AppointmentStatus.COMPLETED, "AUTHORIZED", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                fixture.latest().onSuccess(detail(AppointmentStatus.CANCELED, "REFUNDED", false));
                fixture.followUps.get(0).onSuccess(AppointmentFollowUpRecord.empty("synthetic-request"));
                assertEquals("취소", text(a, R.id.textBookingStatusHeroBadge));
                assertVisible(a, R.id.cardBookingStatusReport, false);
            });
        }
    }

    @Test public void initialErrorHidesActionsAndRetryRecovers() {
        fixture.failDetail = true;
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                assertVisible(a, R.id.bookingStatusContentContainer, false);
                assertVisible(a, R.id.bookingStatusStatePanel, true);
                fixture.failDetail = false;
                a.findViewById(R.id.buttonStatePanelPrimary).performClick();
                assertVisible(a, R.id.bookingStatusStatePanel, false);
                assertVisible(a, R.id.bookingStatusContentContainer, true);
            });
        }
    }

    @Test public void refreshErrorKeepsLastRenderedData() {
        fixture.detail = detail(AppointmentStatus.MATCHED, "DEPOSIT_CONFIRMED", false);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                fixture.latest().onError("합성 일시 조회 실패");
                assertVisible(a, R.id.bookingStatusContentContainer, true);
                assertVisible(a, R.id.bookingStatusStatePanel, false);
                assertEquals("진행전", text(a, R.id.textBookingStatusHeroBadge));
            });
        }
    }

    @Test public void authFailureAndWrongRoleNeverShowRequest() {
        fixture.failAuth = true;
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> assertVisible(a, R.id.bookingStatusContentContainer, false));
            assertTrue(fixture.observers.isEmpty());
        }
        fixture.failAuth = false;
        fixture.user = user(UserRole.MANAGER);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> assertVisible(a, R.id.bookingStatusContentContainer, false));
            assertTrue(fixture.observers.isEmpty());
        }
    }

    @Test public void accountChangeAndDeniedRefreshNeverReusePreviousContent() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.moveToState(Lifecycle.State.CREATED);
            fixture.user = user(UserRole.GUARDIAN);
            fixture.failDetail = true;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                assertVisible(a, R.id.bookingStatusContentContainer, false);
                assertVisible(a, R.id.bookingStatusStatePanel, true);
                a.findViewById(R.id.buttonBookingStatusEdit).performClick();
                assertNull(a.lastStartedIntent);
            });
        }
    }

    @Test public void delayedFollowUpDisablesOldActionsUntilLatestDetailIsBound() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                fixture.delayFollowUp = true;
                fixture.latest().onSuccess(detail(AppointmentStatus.COMPLETED, "AUTHORIZED", false));
                assertFalse(a.findViewById(R.id.buttonBookingStatusEdit).isEnabled());
                assertFalse(a.findViewById(R.id.buttonBookingStatusSecondary).isEnabled());
                a.findViewById(R.id.buttonBookingStatusEdit).performClick();
                assertNull(a.lastStartedIntent);
                fixture.followUps.get(0).onSuccess(AppointmentFollowUpRecord.empty("synthetic-request"));
                assertVisible(a, R.id.buttonBookingStatusEdit, false);
                assertEquals("완료", text(a, R.id.textBookingStatusHeroBadge));
            });
        }
    }

    @Test public void missingIdShowsErrorWithoutSubscribing() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = ActivityScenario.launch(
                new Intent(context(), BookingStatusPreviewActivity.class))) {
            scenario.onActivity(a -> {
                assertVisible(a, R.id.bookingStatusStatePanel, true);
                assertVisible(a, R.id.bookingStatusContentContainer, false);
            });
            assertTrue(fixture.observers.isEmpty());
        }
    }

    @Test public void missingAndLegacyFieldsAreNotReplacedByDesignExamples() {
        AppointmentRequest request = new AppointmentRequest("synthetic-request", "PATIENT", "",
                "", "", "기존 일정 표기", "", "", AppointmentStatus.REQUESTED, "");
        fixture.detail = new AppointmentRequestDetail(request, null, null, null, null, null, null);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                assertVisible(a, R.id.buttonBookingStatusMap, false);
                assertVisible(a, R.id.textBookingStatusReference, false);
                assertVisible(a, R.id.bookingStatusManager, false);
                assertEquals(a.getString(R.string.booking_completion_hospital_pending), text(a, R.id.textBookingStatusHeroTitle));
                assertContains(a.findViewById(R.id.bookingStatusSummaryContainer), "기존 일정 표기");
                a.findViewById(R.id.buttonBookingStatusNote).performClick();
                assertEquals(a.getString(R.string.final_booking_status_note_empty), text(a, R.id.textBookingStatusNote));
                String contents = allText(a.findViewById(R.id.bookingStatusContentContainer));
                assertFalse(contents.contains("김현숙"));
                assertFalse(contents.contains("155cm"));
                assertFalse(contents.contains("대학로"));
            });
        }
    }

    @Test public void longTextAndExpandedContentFitAndLastActionIsReachable() {
        fixture.detail = detail(AppointmentStatus.COMPLETED, "AUTHORIZED", true);
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            scenario.onActivity(a -> {
                a.findViewById(R.id.buttonBookingStatusManager).performClick();
                a.findViewById(R.id.buttonBookingStatusNote).performClick();
                a.findViewById(R.id.buttonBookingStatusProgress).performClick();
                a.findViewById(R.id.buttonBookingStatusReport).performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(a -> assertTextFits(a.findViewById(R.id.bookingStatusRoot)));
            capture("status-long-top");
            onView(withId(R.id.textBookingStatusNote)).perform(scrollTo());
            scenario.onActivity(a -> {
                TextView note = a.findViewById(R.id.textBookingStatusNote);
                assertEquals(LONG_NOTE, note.getText().toString());
                note.requestRectangleOnScreen(new Rect(0, note.getHeight() - note.getLineHeight(),
                        note.getWidth(), note.getHeight()), true);
                Rect visible = new Rect();
                assertTrue(note.getLocalVisibleRect(visible));
                assertTrue(visible.bottom >= note.getHeight() - note.getPaddingBottom());
            });
            capture("status-long-note");
            onView(withId(R.id.buttonBookingStatusSecondary)).perform(scrollTo());
            scenario.onActivity(a -> {
                assertSafe(a.findViewById(R.id.buttonBookingStatusSecondary));
                TextView primary = a.findViewById(R.id.buttonBookingStatusPrimary);
                assertTrue(ColorUtils.calculateContrast(primary.getCurrentTextColor(),
                        a.getColor(R.color.final_brand_primary)) >= 4.5);
            });
            capture("status-long-bottom");
        }
    }

    @Test public void systemInsetsDoNotAccumulateAfterRecreation() {
        try (ActivityScenario<BookingStatusPreviewActivity> scenario = launch()) {
            for (int i = 0; i < 2; i++) {
                scenario.onActivity(a -> {
                    View root = a.findViewById(R.id.bookingStatusRoot);
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                    assertNotNull(insets);
                    assertEquals(insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()).top, root.getPaddingTop());
                    assertEquals(insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()).bottom, root.getPaddingBottom());
                    assertSafe(a.findViewById(R.id.buttonBackBookingStatus));
                    ViewCompat.requestApplyInsets(root);
                });
                scenario.recreate();
            }
        }
    }

    private ActivityScenario<BookingStatusPreviewActivity> launch() {
        return ActivityScenario.launch(new Intent(context(), BookingStatusPreviewActivity.class)
                .putExtra("requestId", "synthetic-request"));
    }
    private BookingStatusScreenModel model(AppointmentRequestDetail detail) {
        return new BookingStatusCoordinator(context(), new BookingPresentationFormatter(context()),
                new AppointmentProgressComposer(context())).createScreenModel(fixture.user, detail, false, null);
    }
    private static void assertRoute(BookingStatusPreviewActivity a, Class<?> type, String idKey) {
        assertEquals(type.getName(), a.lastStartedIntent.getComponent().getClassName());
        assertEquals("synthetic-request", a.lastStartedIntent.getStringExtra(idKey));
    }
    private static void assertVisible(BookingStatusPreviewActivity a, int id, boolean visible) {
        assertEquals(a.getResources().getResourceEntryName(id), visible ? View.VISIBLE : View.GONE, a.findViewById(id).getVisibility());
    }
    private static String text(BookingStatusPreviewActivity a, int id) { return ((TextView)a.findViewById(id)).getText().toString(); }
    private static String allText(View view) {
        StringBuilder value = new StringBuilder();
        if (view instanceof TextView) value.append(((TextView)view).getText()).append('\n');
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) value.append(allText(((ViewGroup)view).getChildAt(i)));
        return value.toString();
    }
    private static void assertContains(View view, String value) { assertTrue(allText(view), allText(view).contains(value)); }
    private static void assertTextFits(View view) {
        if (view.getVisibility() == View.GONE) return;
        if (view instanceof TextView) {
            TextView text = (TextView)view;
            Layout layout = text.getLayout();
            if (layout != null && text.length() > 0) {
                for (int i = 0; i < layout.getLineCount(); i++) {
                    assertEquals("텍스트 생략: " + text.getText(), 0, layout.getEllipsisCount(i));
                    assertTrue("가로 잘림: " + text.getText(), layout.getLineMax(i) <= layout.getWidth() + 1);
                }
                if (text.getId() == R.id.textBookingStatusLineLabel
                        && (text.getText().toString().equals("환자 성함")
                        || text.getText().toString().equals("건강 메모")
                        || text.getText().toString().equals("복약 메모"))) {
                    assertEquals("짧은 항목명 줄바꿈: " + text.getText(), 1, layout.getLineCount());
                }
                assertTrue("세로 잘림: " + text.getText(), layout.getHeight()
                        <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) assertTextFits(((ViewGroup)view).getChildAt(i));
    }
    private static void assertSafe(View view) {
        Rect rect = new Rect();
        assertTrue(view.getGlobalVisibleRect(rect));
        assertEquals(view.getHeight(), rect.height());
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(view);
        assertNotNull(insets);
        assertTrue(rect.top >= insets.getInsets(WindowInsetsCompat.Type.systemBars()).top);
        assertTrue(rect.bottom <= view.getRootView().getHeight() - insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
    }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static User user(UserRole role) { return new User(role.name(), role, "합성 " + (role == UserRole.PATIENT ? "환자" : "보호자"), "synthetic@example.invalid", "01000000000"); }
    private static AppointmentRequestDetail detail(AppointmentStatus status, String payment, boolean longText) {
        AppointmentRequest request = new AppointmentRequest("synthetic-request", "PATIENT", "GUARDIAN",
                longText ? "합성 검증용 이름이 긴 대학병원 본관 재활의학센터" : "합성 검증 병원", "내과", 37.5, 127.1,
                "2028-02-29 10:17", longText ? LONG_NOTE : "병원 정문 안내 데스크", longText ? LONG_NOTE : "합성 전달사항",
                status, "MANAGER", "합성 환자", "01000000000", "synthetic@example.invalid", "합성 보호자", "01000000001", "guardian@example.invalid",
                "합성 건강 정보", "합성 복약 정보", "INDEPENDENT", "ONE_WAY", "ANY", "BANK_TRANSFER", "NONE",
                40000, 0, 0, 40000, payment, "synthetic-approval", "2028-02-28 11:30", "합성 검증");
        request.setPublicCode("TEST-ONLY");
        User manager = status == AppointmentStatus.REQUESTED ? null
                : new User("MANAGER", UserRole.MANAGER, longText ? "합성 검증용 이름이 긴 매니저" : "합성 담당", "manager@example.invalid", "01000000002");
        CompanionSession session = status == AppointmentStatus.IN_PROGRESS || status == AppointmentStatus.COMPLETED
                ? new CompanionSession("synthetic-session", request.getId(), "MANAGER", 2,
                status == AppointmentStatus.COMPLETED ? SessionStatus.COMPLETED : SessionStatus.WAITING,
                "합성 진행 안내", "합성 위치 안내", "합성 현장 메모", "합성 복약 안내", "합성 약국 안내", false) : null;
        SessionReport report = status == AppointmentStatus.COMPLETED ? new SessionReport("synthetic-report", "synthetic-session",
                "합성 진료 요약", "합성 진료 기록", "합성 복약 기록", "합성 약", "변경 없음", "식후 복용", MedicationComparisonDecision.MATCHED, "확인", "2028-03-10") : null;
        return new AppointmentRequestDetail(request, user(UserRole.PATIENT), user(UserRole.GUARDIAN), manager, session, report, null);
    }
    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(400);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = new File(context().getExternalFilesDir(null), "booking-status-figma");
        assertTrue(dir.exists() || dir.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }

    private static final class Fixture {
        User user = user(UserRole.PATIENT);
        AppointmentRequestDetail detail = detail(AppointmentStatus.REQUESTED, "PENDING", false);
        boolean failAuth, failDetail, delayFollowUp;
        int cancelCalls, unsubscribeCalls;
        final List<RepositoryCallback<AppointmentRequestDetail>> observers = new ArrayList<>();
        final List<RepositoryCallback<AppointmentFollowUpRecord>> followUps = new ArrayList<>();
        final AuthRepository auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class<?>[]{AuthRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getCurrentUser")) {
                        RepositoryCallback<User> callback = (RepositoryCallback<User>) args[0];
                        if (failAuth) callback.onError("합성 인증 실패"); else callback.onSuccess(user);
                        return null;
                    }
                    throw new AssertionError("예상하지 못한 인증 호출: " + method.getName());
                });
        final BookingRepository booking = (BookingRepository) Proxy.newProxyInstance(BookingRepository.class.getClassLoader(), new Class<?>[]{BookingRepository.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isFirebaseBacked": return false;
                        case "observeAppointmentRequestDetail": {
                            assertEquals("synthetic-request", args[1]);
                            RepositoryCallback<AppointmentRequestDetail> callback = (RepositoryCallback<AppointmentRequestDetail>) args[2];
                            observers.add(callback);
                            if (failDetail) callback.onError("합성 조회 실패"); else callback.onSuccess(detail);
                            return (Runnable) () -> unsubscribeCalls++;
                        }
                        case "getAppointmentFollowUp": {
                            RepositoryCallback<AppointmentFollowUpRecord> callback = (RepositoryCallback<AppointmentFollowUpRecord>) args[2];
                            followUps.add(callback);
                            if (!delayFollowUp) callback.onSuccess(AppointmentFollowUpRecord.empty("synthetic-request"));
                            return null;
                        }
                        case "cancelAppointmentRequest": {
                            cancelCalls++;
                            assertEquals(UserRole.PATIENT, ((User) args[0]).getRole());
                            assertEquals("synthetic-request", args[1]);
                            detail = detail(AppointmentStatus.CANCELED, "REFUNDED", false);
                            ((RepositoryCallback<AppointmentRequest>) args[2]).onSuccess(detail.getAppointmentRequest());
                            return null;
                        }
                        default: throw new AssertionError("예상하지 못한 예약 호출: " + method.getName());
                    }
                });
        RepositoryCallback<AppointmentRequestDetail> latest() { return observers.get(observers.size() - 1); }
    }
}
