package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
import com.example.bodeul.data.*;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.firebase.ClientSupportPushContract;
import com.example.bodeul.ui.support.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 실제 공통 Activity에 합성 저장소를 주입해 UI와 기존 계약을 함께 검증한다. */
@RunWith(AndroidJUnit4.class)
public class SupportFigmaTest {
    private User user;
    private boolean managerMode, failRead, empty, delayedAuth;
    private int writes, marks;
    private Object[] payload;
    private RepositoryCallback<Object> save;
    private final List<RepositoryCallback<User>> pendingAuth = new ArrayList<>();
    private final String question = "문의 내역을 확인하고 싶습니다.\n연결된 예약도 함께 확인 부탁드립니다.";
    private final String answer = "안녕하세요, 보들 고객센터입니다.\n예약과 동행 내용을 확인했습니다.\n추가 문의가 있다면 남겨주세요.";

    @Before public void setup() { configure(UserRole.PATIENT); }
    @After public void cleanup() { SupportFigmaPreviewActivity.auth = null; SupportFigmaPreviewActivity.source = null; }

    @SuppressWarnings("unchecked") private void configure(UserRole role) {
        managerMode = role == UserRole.MANAGER;
        user = new User("owner", role, "테스트 사용자", "test@example.test", "01000000000");
        SupportFigmaPreviewActivity.auth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(), new Class[]{AuthRepository.class}, (p, m, a) -> {
            if (m.getName().equals("getCurrentUser")) {
                RepositoryCallback<User> callback = (RepositoryCallback<User>) a[0];
                if (delayedAuth) pendingAuth.add(callback); else callback.onSuccess(user);
            } else throw new AssertionError("인증 변경 금지: " + m.getName());
            return null;
        });
        Class<?> type = managerMode ? ManagerRepository.class : ClientSupportRepository.class;
        Object repo = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (p, m, a) -> {
            if (m.getName().equals("getSupportInquiries") || m.getName().equals("getClientSupportRequests")) {
                RepositoryCallback callback = (RepositoryCallback) a[1];
                if (failRead) callback.onError("테스트 조회 실패"); else callback.onSuccess(rows());
            } else if (m.getName().startsWith("submit")) {
                writes++; payload = a; save = (RepositoryCallback<Object>) a[a.length - 1];
            } else if (m.getName().equals("markClientSupportResponsesRead")) {
                marks++; ((RepositoryCallback<Void>) a[1]).onSuccess(null);
            } else if (m.getReturnType() == boolean.class) return false;
            else throw new AssertionError("허용하지 않은 문의 호출: " + m.getName());
            return null;
        });
        BookingRepository booking = (BookingRepository) Proxy.newProxyInstance(BookingRepository.class.getClassLoader(), new Class[]{BookingRepository.class}, (p, m, a) -> {
            if (m.getName().equals("getAppointmentRequestDetail")) {
                ((RepositoryCallback<AppointmentRequestDetail>) a[2]).onSuccess(new AppointmentRequestDetail(
                        new AppointmentRequest((String) a[1], "owner", "", "테스트 병원", "내과", "예약 일시", "정문", "", AppointmentStatus.REQUESTED, ""), user, null, null, null, null, null));
            } else throw new AssertionError("예약 변경 금지: " + m.getName());
            return null;
        });
        SupportFigmaPreviewActivity.source = managerMode ? new SupportSource((ManagerRepository) repo) : new SupportSource((ClientSupportRepository) repo, booking);
    }

    @Test public void listFiltersAnsweredPendingAndBackKeepGeometryAndRawText() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            layout(scenario);
            assertRows(scenario, 3);
            geometry(scenario);
            capture("list");
            onView(withId(R.id.supportFilterAnswered)).perform(click());
            assertRows(scenario, 2);
            openRow(scenario, "answered");
            layout(scenario); geometry(scenario);
            onView(withId(R.id.supportDetailBody)).check(matches(withText(question)));
            onView(withId(R.id.supportResponseBody)).check(matches(withText(answer)));
            capture("answered");
            onView(withId(R.id.supportResponseDate)).perform(scrollTo());
            onView(withId(R.id.supportBack)).perform(click());
            onView(withId(R.id.supportFilterAnswered)).check(matches(isSelected()));
            onView(withId(R.id.supportFilterWaiting)).perform(click());
            assertRows(scenario, 1);
            openRow(scenario, "pending");
            layout(scenario); geometry(scenario);
            onView(withId(R.id.supportPending)).check(matches(isDisplayed()));
            capture("pending");
            assertTrue(marks > 0);
            assertEquals(0, writes);
        }
    }

    @Test public void composeRecreationCancelAndDiscardRetainCorrectDraft() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withId(R.id.supportSubmit)).perform(click());
            layout(scenario); geometry(scenario); capture("compose-empty");
            choose("OTHER");
            fill(scenario, "문의 제목", "첫째 줄\n둘째 줄");
            scenario.recreate();
            onView(withId(R.id.supportTitle)).check(matches(withText("문의 제목")));
            onView(withId(R.id.supportBody)).check(matches(withText("첫째 줄\n둘째 줄")));
            onView(withId(R.id.supportSubmit)).check(matches(isEnabled()));
            layout(scenario); geometry(scenario); capture("compose-filled");
            onView(withId(R.id.supportBack)).perform(click());
            onView(withText(R.string.final_support_continue)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.supportTitle)).check(matches(withText("문의 제목")));
            onView(withId(R.id.supportBack)).perform(click());
            onView(withText(R.string.final_support_discard)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.supportSubmit)).perform(click());
            onView(withId(R.id.supportTitle)).check(matches(withText("")));
            onView(withId(R.id.supportSubmit)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            assertEquals(0, writes);
        }
    }

    @Test public void sendIsSingleFlightAcrossRecreationAndUsesOriginalPayload() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("booking-id", "")) {
            onView(withId(R.id.supportSubmit)).perform(click());
            onView(withId(R.id.supportBookingContext)).check(matches(isDisplayed()));
            choose("RESERVATION"); fill(scenario, " 제목 ", " 본문\n다음 줄 ");
            onView(withId(R.id.supportSubmit)).perform(click());
            scenario.onActivity(a -> a.findViewById(R.id.supportSubmit).performClick());
            assertEquals(1, writes);
            assertEquals("booking-id", payload[1]);
            assertEquals(ClientSupportCategory.RESERVATION, payload[2]);
            assertEquals("제목", payload[3]);
            assertEquals("본문\n다음 줄", payload[4]);
            scenario.recreate();
            scenario.onActivity(a -> save.onSuccess(rows()));
            assertRows(scenario, 3);
            onView(withId(R.id.supportSubmit)).perform(click());
            onView(withId(R.id.supportTitle)).check(matches(withText("")));
            assertEquals(1, writes);
        }
    }

    @Test public void noCategoryWhitespaceAndAttachmentDoNotSubmit() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withId(R.id.supportSubmit)).perform(click());
            fill(scenario, "제목", "본문");
            onView(withId(R.id.supportSubmit)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            choose("OTHER"); fill(scenario, "  ", "본문");
            onView(withId(R.id.supportSubmit)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withId(R.id.supportAttachment)).perform(scrollTo(), click());
            onView(withText(R.string.final_support_attachment_unavailable)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.final_support_confirm)).inRoot(isDialog()).perform(click());
            assertEquals(0, writes);
        }
    }

    @Test public void guardianAndManagerUseTheirOwnRolesAndCategories() {
        for (UserRole role : new UserRole[]{UserRole.GUARDIAN, UserRole.MANAGER}) {
            configure(role);
            try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
                layout(scenario); capture(role == UserRole.MANAGER ? "manager-list" : "guardian-list");
                onView(withId(R.id.supportSubmit)).perform(click());
                choose(role == UserRole.MANAGER ? "DOCUMENT" : "REPORT");
                fill(scenario, "역할별 문의", "역할별 본문");
                onView(withId(R.id.supportSubmit)).perform(click());
                if (role == UserRole.MANAGER) assertEquals(SupportInquiryCategory.DOCUMENT, payload[1]);
                else {
                    assertEquals(UserRole.GUARDIAN, ((User) payload[0]).getRole());
                    assertEquals(ClientSupportCategory.REPORT, payload[2]);
                }
                scenario.onActivity(a -> save.onSuccess(rows()));
            }
        }
    }

    @Test public void answerNotificationOpensOwnedDetailAndDoesNotReplaceDraft() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "answered")) {
            onView(withId(R.id.supportDetailTitle)).check(matches(withText("예약 취소 관련 문의")));
            onView(withId(R.id.supportBack)).perform(click());
            onView(withId(R.id.supportSubmit)).perform(click());
            choose("OTHER"); fill(scenario, "알림 중 초안", "보존되는 본문");
            context().sendBroadcast(new Intent(ClientSupportPushContract.ACTION_CLIENT_SUPPORT_UPDATED)
                    .setPackage(context().getPackageName()).putExtra(ClientSupportPushContract.EXTRA_SUPPORT_REQUEST_ID, "pending"));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withId(R.id.supportTitle)).check(matches(withText("알림 중 초안")));
            assertEquals(0, writes);
        }
    }

    @Test public void errorRetryAndEmptyFilteredStateRemainReachable() {
        failRead = true;
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withText(R.string.final_support_read_error)).check(matches(isDisplayed()));
            failRead = false; empty = true;
            onView(withText(R.string.final_support_retry)).perform(scrollTo(), click());
            onView(withId(R.id.supportEmpty)).check(matches(withText(R.string.final_support_empty)));
            onView(withId(R.id.supportFilterWaiting)).perform(click());
            onView(withId(R.id.supportEmpty)).check(matches(withText(R.string.final_support_filter_empty)));
            onView(withId(R.id.supportSubmit)).check(matches(isEnabled()));
            layout(scenario); capture("empty");
        }
    }

    @Test public void accountChangeAndLateAuthCannotExposePreviousDraft() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            onView(withId(R.id.supportSubmit)).perform(click());
            choose("OTHER"); fill(scenario, "이전 계정 초안", "이전 본문");
            scenario.moveToState(Lifecycle.State.CREATED);
            user = new User("other", UserRole.PATIENT, "다른 사용자", "", "01000000000");
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.supportSubmit)).perform(click());
            onView(withId(R.id.supportTitle)).check(matches(withText("")));
        }
        delayedAuth = true;
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "")) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                pendingAuth.get(1).onError("인증 실패");
                pendingAuth.get(0).onSuccess(user);
                assertEquals(View.GONE, a.findViewById(R.id.supportScroll).getVisibility());
                assertEquals(View.GONE, a.findViewById(R.id.supportSubmit).getVisibility());
            });
        }
    }

    @Test public void largeTextDetailsKeepFinalLineAndKeyboardKeepsSubmitAboveSystemArea() {
        try (ActivityScenario<SupportFigmaPreviewActivity> scenario = launch("", "long")) {
            layout(scenario); geometry(scenario);
            onView(withId(R.id.supportResponseAuthor)).perform(scrollTo());
            layout(scenario); capture("long-answer-bottom");
            scenario.onActivity(a -> {
                TextView text = a.findViewById(R.id.supportResponseBody);
                assertTrue(text.getText().toString().endsWith("마지막 답변 문장"));
                assertTrue(text.getLayout().getLineCount() > 5);
            });
            onView(withId(R.id.supportBack)).perform(click());
            onView(withId(R.id.supportSubmit)).perform(click());
            choose("OTHER"); fill(scenario, "키보드 문의", "키보드를 연 상태의 본문");
            onView(withId(R.id.supportBody)).perform(scrollTo(), click());
            waitForKeyboard(scenario);
            layout(scenario);
            scenario.onActivity(a -> {
                View root = a.findViewById(R.id.supportRoot), button = a.findViewById(R.id.supportSubmit);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
                assertNotNull(insets);
                assertTrue(insets.isVisible(WindowInsetsCompat.Type.ime()));
                assertTrue("실제 소프트 키보드가 표시돼야 합니다.",
                        insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 100 * a.getResources().getDisplayMetrics().density);
                int[] location = new int[2]; button.getLocationOnScreen(location);
                assertTrue(location[1] + button.getHeight() <= root.getHeight() - insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
                assertVisible(button);
                View back = a.findViewById(R.id.supportBack);
                assertVisible(back);
                back.getLocationOnScreen(location);
                assertTrue("키보드 표시 중에도 뒤로가기가 상태 표시줄 아래에 있어야 합니다.",
                        location[1] >= insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
            });
            capture("keyboard");
            onView(withId(R.id.supportBody)).perform(closeSoftKeyboard());
        }
    }

    private List<?> rows() {
        if (empty) return Collections.emptyList();
        List<Object> rows = new ArrayList<>();
        for (String id : new String[]{"answered", "pending", "long"}) {
            boolean answered = !id.equals("pending");
            String title = id.equals("long") ? "여러 줄로 길게 작성한 문의의 전체 내용을 확인하는 테스트" : "예약 취소 관련 문의";
            String body = id.equals("long") ? repeat(question, 10) : question;
            String response = id.equals("long") ? repeat(answer, 10) + "마지막 답변 문장" : answer;
            if (managerMode) rows.add(new SupportInquiry(id, user.getId(), "테스트", SupportInquiryCategory.MATCHING, title, body,
                    answered ? SupportInquiryStatus.ANSWERED : SupportInquiryStatus.RECEIVED, 1794708000000L,
                    answered ? response : "", 1794794400000L, "운영 담당자"));
            else rows.add(new ClientSupportRequest(id, user.getId(), "테스트", user.getRole(), "", ClientSupportCategory.RESERVATION, title, body,
                    answered ? ClientSupportStatus.ANSWERED : ClientSupportStatus.RECEIVED, 1794708000000L,
                    answered ? response : "", 1794794400000L, "운영 담당자", false, 0, 0, 0));
        }
        return rows;
    }
    private static String repeat(String value, int n) { StringBuilder result = new StringBuilder(); for (int i = 0; i < n; i++) result.append(value).append('\n'); return result.toString(); }
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private ActivityScenario<SupportFigmaPreviewActivity> launch(String booking, String focus) {
        return ActivityScenario.launch(new Intent(context(), SupportFigmaPreviewActivity.class).putExtra("requestId", booking).putExtra("supportRequestId", focus));
    }
    private void choose(String key) {
        onView(withId(R.id.supportCategory)).perform(scrollTo(), click());
        onData(org.hamcrest.Matchers.equalTo(context().getString(SupportFigmaPreviewActivity.source.categoryLabel(key))))
                .inRoot(isDialog()).perform(click());
    }
    private void fill(ActivityScenario<SupportFigmaPreviewActivity> scenario, String title, String body) {
        scenario.onActivity(a -> { ((EditText) a.findViewById(R.id.supportTitle)).setText(title); ((EditText) a.findViewById(R.id.supportBody)).setText(body); });
    }
    private static void openRow(ActivityScenario<SupportFigmaPreviewActivity> scenario, String id) {
        scenario.onActivity(a -> a.findViewById(R.id.supportRows).findViewWithTag(id).performClick());
    }
    private static void assertRows(ActivityScenario<SupportFigmaPreviewActivity> scenario, int count) {
        scenario.onActivity(a -> assertEquals(count, ((LinearLayout) a.findViewById(R.id.supportRows)).getChildCount()));
    }
    private static void geometry(ActivityScenario<SupportFigmaPreviewActivity> scenario) {
        scenario.onActivity(a -> {
            assertTextFits(a.findViewById(R.id.supportRoot));
            assertVisible(a.findViewById(R.id.supportBack));
            View submit = a.findViewById(R.id.supportSubmit);
            if (submit.getVisibility() == View.VISIBLE) {
                assertVisible(submit);
                TextView text = (TextView) submit;
                assertTrue(ColorUtils.calculateContrast(text.getCurrentTextColor(), a.getColor(R.color.final_brand_primary)) >= 4.5);
            }
        });
    }
    private static void assertVisible(View view) { Rect rect = new Rect(); assertTrue(view.getGlobalVisibleRect(rect)); assertEquals(view.getHeight(), rect.height()); }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView t = (TextView) view;
            if (t.getLayout() != null && !(t instanceof EditText)) {
                assertTrue("세로 잘림: " + t.getText(), t.getLayout().getHeight() <= t.getHeight() - t.getCompoundPaddingTop() - t.getCompoundPaddingBottom());
                for (int line = 0; line < t.getLineCount(); line++) {
                    if (t.getId() != R.id.supportRowTitle) assertEquals("말줄임: " + t.getText(), 0, t.getLayout().getEllipsisCount(line));
                    assertTrue("가로 잘림: " + t.getText(), t.getLayout().getLineMax(line) <= t.getWidth() - t.getCompoundPaddingLeft() - t.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static void layout(ActivityScenario<?> scenario) {
        CountDownLatch done = new CountDownLatch(1);
        scenario.onActivity(a -> a.getWindow().getDecorView().postOnAnimation(() -> a.getWindow().getDecorView().postOnAnimation(done::countDown)));
        try { assertTrue(done.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new AssertionError(e); }
    }
    private static void waitForKeyboard(ActivityScenario<?> scenario) {
        java.util.concurrent.atomic.AtomicBoolean shown = new java.util.concurrent.atomic.AtomicBoolean();
        for (int attempt = 0; attempt < 50; attempt++) {
            scenario.onActivity(a -> {
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(a.findViewById(R.id.supportRoot));
                shown.set(insets != null && insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 100 * a.getResources().getDisplayMetrics().density);
            });
            if (shown.get()) return;
            android.os.SystemClock.sleep(100);
        }
        capture("keyboard-unavailable");
        fail("실제 키보드 높이가 반영되지 않았습니다.");
    }
    private static void capture(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureSupport");
        if (prefix == null) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) activity.getWindow().getDecorView().invalidate();
        });
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException e) { throw new AssertionError(e); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File dir = new File(context().getFilesDir(), "support-captures"); dir.mkdirs();
        try (FileOutputStream stream = new FileOutputStream(new File(dir, prefix + "-" + name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
