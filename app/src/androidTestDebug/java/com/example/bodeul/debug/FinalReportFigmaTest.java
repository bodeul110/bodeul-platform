package com.example.bodeul.debug;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class FinalReportFigmaTest {
    private final List<RepositoryCallback<GuardianReportEntry>> pending = new ArrayList<>();
    private User user;
    private GuardianReportEntry entry;
    private boolean fail;
    private boolean delayed;
    private int reads;
    private int authReads;

    @Before public void setup() {
        user = new User("patient", UserRole.PATIENT, "김현숙", "", "01000000000");
        entry = fixture(false, false);
        FinalReportPreviewActivity.testAuth = (AuthRepository) Proxy.newProxyInstance(AuthRepository.class.getClassLoader(),
                new Class<?>[]{AuthRepository.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("getCurrentUser")) throw new AssertionError(method.getName());
                    authReads++;
                    @SuppressWarnings("unchecked") RepositoryCallback<User> callback = (RepositoryCallback<User>) args[0];
                    if (user == null) callback.onError("로그인 필요"); else callback.onSuccess(user);
                    return null;
                });
        FinalReportPreviewActivity.testReports = (account, requestId, callback) -> {
            reads++;
            if (delayed) pending.add(callback);
            else if (fail) callback.onError("요청 실패");
            else callback.onSuccess(entry);
        };
    }

    @After public void cleanup() {
        FinalReportPreviewActivity.testAuth = null;
        FinalReportPreviewActivity.testReports = null;
    }

    @Test public void completeReportPreservesMedicalValuesAndRecreates() {
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            onView(withId(R.id.textFinalReportTitle)).check(matches(withText(R.string.final_report_title)));
            assertClinicalText(scenario);
            scenario.recreate();
            assertClinicalText(scenario);
            assertEquals(2, reads);
            assertEquals(2, authReads);
            capture(scenario, "complete");
        }
    }

    @Test public void partialReportHidesMissingSectionsAndDoesNotInventAdvice() {
        entry = fixture(true, false);
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                String text = collectText(activity.findViewById(R.id.finalReportContent));
                assertTrue(text.contains("실제 진료 요약"));
                assertFalse(text.contains("약국 조제 안내"));
                assertFalse(text.contains("다음 예약 안내"));
                assertFalse(text.contains("건강 인사이트"));
                assertFalse(text.contains("2시간 10분"));
                assertFalse(text.contains("50세"));
            });
        }
    }

    @Test public void missingReportShowsPendingAndRebindClearsPriorText() {
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            entry = new GuardianReportEntry(entry.getAppointmentRequest(), null, null, null, null, null);
            refresh();
            onView(withId(R.id.textFinalReportNotice)).check(matches(withText(R.string.final_report_pending)));
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.cardFinalReportTreatment).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.cardGuardianFinalReportManager).getVisibility());
                assertFalse(collectText(activity.findViewById(R.id.finalReportContent)).contains("기존 처방 비교"));
            });
        }
    }

    @Test public void managerSeesOnlyJournalEvenIfInjectedStoreReturnsClinicalData() {
        user = new User("manager", UserRole.MANAGER, "매니저", "", "01000000000");
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            onView(withId(R.id.textFinalReportNotice)).check(matches(withText(R.string.final_report_manager_scope)));
            scenario.onActivity(activity -> {
                String text = collectText(activity.findViewById(R.id.finalReportContent));
                assertTrue(text.contains("안전하게 인계했습니다"));
                assertFalse(text.contains("실제 진료 요약"));
                assertFalse(text.contains("최근 현장 업데이트"));
                assertEquals(View.GONE, activity.findViewById(R.id.cardFinalReportTreatment).getVisibility());
            });
            capture(scenario, "manager");
        }
    }

    @Test public void errorRetryRefreshAndBackRemainUsable() {
        fail = true;
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            assertEquals(2, reads);
            fail = false;
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            onView(withId(R.id.textFinalReportTitle)).check(matches(isDisplayed()));
            refresh();
            assertEquals(4, reads);
            onView(withId(R.id.buttonFinalReportBack)).perform(click());
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    @Test public void accountChangeRejectsLateDataAndClearsOldReportOnReturn() {
        delayed = true;
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            assertEquals(1, pending.size());
            scenario.moveToState(Lifecycle.State.CREATED);
            user = new User("other", UserRole.ADMIN, "다른 계정", "", "");
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> pending.get(0).onSuccess(entry));
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.finalReportScroll).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.finalReportStateScroll).getVisibility());
            });
            assertEquals(1, reads);
        }
    }

    @Test public void recreationIgnoresOldCallbackAndAuthFailureHidesRenderedContent() {
        delayed = true;
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.recreate();
            assertEquals(2, pending.size());
            scenario.onActivity(activity -> {
                pending.get(0).onSuccess(entry);
                assertEquals(View.GONE, activity.findViewById(R.id.finalReportScroll).getVisibility());
                pending.get(1).onSuccess(entry);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.finalReportScroll).getVisibility());
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            user = null;
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertEquals(View.GONE,
                    activity.findViewById(R.id.finalReportScroll).getVisibility()));
        }
    }

    @Test public void longTextFitsAtDeviceFontScaleAndLastLineRemainsReachable() {
        entry = fixture(false, true);
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            onView(withId(R.id.textGuardianFinalReportManagerMessage)).perform(scrollTo());
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.finalReportScroll);
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertTextFits(activity.findViewById(R.id.finalReportContent));
                View root = activity.findViewById(R.id.finalReportRoot);
                View scroll = activity.findViewById(R.id.finalReportScroll);
                assertTrue(scroll.getBottom() <= root.getHeight() - root.getPaddingBottom());
                View last = activity.findViewById(R.id.textGuardianFinalReportManagerMessage);
                Rect viewport = new Rect();
                assertTrue(scroll.getGlobalVisibleRect(viewport));
                int[] location = new int[2];
                last.getLocationOnScreen(location);
                assertTrue("마지막 줄이 화면 아래로 가려짐", location[1] + last.getHeight() <= viewport.bottom);
                assertTrue("마지막 문장이 화면 밖에 있음", location[1] + last.getHeight() > viewport.top);
            });
            capture(scenario, "long");
        }
    }

    @Test public void emptyFieldsAndManagerWithoutJournalDoNotReuseMedicalOrLiveText() {
        SessionReport blank = new SessionReport("report", "session", " ", "", "", "", "", "", null, "", "");
        entry = new GuardianReportEntry(entry.getAppointmentRequest(), null, null, null, blank, null);
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> assertEquals(View.GONE,
                    activity.findViewById(R.id.cardFinalReportTreatment).getVisibility()));
            onView(withId(R.id.textFinalReportNotice)).check(matches(withText(R.string.final_report_pending)));
        }
        user = new User("manager", UserRole.MANAGER, "매니저", "", "01000000000");
        entry = fixture(false, false);
        entry.getSession().applyCompletionState(1L, "", "READY", 1, "", 1L, null);
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.cardGuardianFinalReportManager).getVisibility());
                assertFalse(collectText(activity.findViewById(R.id.finalReportContent)).contains("최근 현장 업데이트"));
            });
        }
    }

    @Test public void missingRequestAndUnfinishedResponseCannotShowFinalContent() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<FinalReportPreviewActivity> scenario = ActivityScenario.launch(
                new Intent(context, FinalReportPreviewActivity.class))) {
            assertEquals(0, reads);
            assertEquals(0, authReads);
            scenario.onActivity(activity -> assertEquals(View.GONE,
                    activity.findViewById(R.id.finalReportScroll).getVisibility()));
        }
        entry.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> assertEquals(View.GONE,
                    activity.findViewById(R.id.finalReportScroll).getVisibility()));
        }
    }

    @Test public void guardianListRebindKeepsProgressAndRequestDetailAction() {
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                android.widget.FrameLayout parent = new android.widget.FrameLayout(activity);
                View card = activity.getLayoutInflater().inflate(R.layout.item_guardian_report, parent, false);
                com.example.bodeul.ui.report.GuardianReportCoordinator coordinator =
                        new com.example.bodeul.ui.report.GuardianReportCoordinator(activity,
                                new com.example.bodeul.ui.report.GuardianReportPresentationFormatter(activity));
                List<String> opened = new ArrayList<>();
                com.example.bodeul.ui.report.GuardianReportEntryCardBinder binder =
                        new com.example.bodeul.ui.report.GuardianReportEntryCardBinder(
                                activity, activity.getLayoutInflater(), opened::add);
                binder.bind(card, coordinator.createEntryCardModel(entry));
                assertEquals(View.VISIBLE, card.findViewById(R.id.guardianFinalReportGroup).getVisibility());
                card.findViewById(R.id.buttonGuardianReportEntryAction).performClick();
                assertEquals(List.of("request"), opened);
                entry.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
                binder.bind(card, coordinator.createEntryCardModel(entry));
                assertEquals(View.GONE, card.findViewById(R.id.guardianFinalReportGroup).getVisibility());
                assertEquals(View.VISIBLE, card.findViewById(R.id.guardianReportProgressGroup).getVisibility());
                assertTrue(collectText(card).contains("기존 처방 비교"));
                assertTrue(collectText(card).contains("최근 현장 업데이트"));
            });
        }
    }

    @Test public void managerHistoryButtonUsesOnlyCompletedRequestId() {
        try (ActivityScenario<FinalReportPreviewActivity> scenario = launch()) {
            scenario.onActivity(activity -> {
                android.widget.FrameLayout parent = new android.widget.FrameLayout(activity);
                View card = activity.getLayoutInflater().inflate(R.layout.item_manager_history_entry, parent, false);
                com.example.bodeul.ui.manager.ManagerHistoryCoordinator coordinator =
                        new com.example.bodeul.ui.manager.ManagerHistoryCoordinator(activity,
                                new com.example.bodeul.ui.manager.ManagerHomePresentationFormatter(activity));
                AppointmentRequestDetail detail = new AppointmentRequestDetail(entry.getAppointmentRequest(),
                        entry.getPatient(), null, entry.getManager(), entry.getSession(), entry.getSessionReport(), null);
                com.example.bodeul.ui.manager.ManagerHistoryEntryCardBinder binder =
                        new com.example.bodeul.ui.manager.ManagerHistoryEntryCardBinder(activity.getLayoutInflater());
                List<String> opened = new ArrayList<>();
                com.example.bodeul.ui.manager.ManagerHistoryBinder.Listener listener =
                        new com.example.bodeul.ui.manager.ManagerHistoryBinder.Listener() {
                            @Override public void onSelectFilter(com.example.bodeul.ui.manager.ManagerHistoryFilter filter) { }
                            @Override public void onOpenReport(String id) { opened.add(id); }
                        };
                binder.bind(card, coordinator.createScreenModel(entry.getManager(), List.of(detail), false,
                        com.example.bodeul.ui.manager.ManagerHistoryFilter.ALL).getEntryCards().get(0), listener);
                assertEquals(View.VISIBLE, card.findViewById(R.id.buttonManagerHistoryFinalReport).getVisibility());
                card.findViewById(R.id.buttonManagerHistoryFinalReport).performClick();
                assertEquals(List.of("request"), opened);
                entry.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
                binder.bind(card, coordinator.createScreenModel(entry.getManager(), List.of(detail), false,
                        com.example.bodeul.ui.manager.ManagerHistoryFilter.ALL).getEntryCards().get(0), listener);
                assertEquals(View.GONE, card.findViewById(R.id.buttonManagerHistoryFinalReport).getVisibility());
            });
        }
    }

    private ActivityScenario<FinalReportPreviewActivity> launch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, FinalReportPreviewActivity.class).putExtra("requestId", "request"));
    }

    private void refresh() {
        onView(withId(R.id.buttonFinalReportRefresh)).perform(click());
        onView(withText(R.string.final_report_refresh)).perform(click());
    }

    private GuardianReportEntry fixture(boolean partial, boolean longText) {
        AppointmentRequest request = new AppointmentRequest("request", "patient", "guardian",
                longText ? "서울특별시 지역사회 통합돌봄 대학병원 재활의학센터" : "서울대학교병원",
                "신경과", "2026-11-15 10:30", "입구", "", AppointmentStatus.COMPLETED, "manager");
        CompanionSession session = new CompanionSession("session", "request", "manager", 13,
                SessionStatus.COMPLETED, "최근 현장 업데이트", "", "", "", "", true);
        String journal = longText ? String.join("", java.util.Collections.nCopies(12,
                "안전하게 인계했습니다. 보호자에게 전달한 실제 메모를 확인합니다. "))
                : "안전하게 인계했습니다.";
        session.applyCompletionState(1794718800000L, journal, "READY", 1, "", 1794718800000L, null);
        SessionReport report = new SessionReport("report", "session", "실제 진료 요약",
                partial ? "" : "기존 처방 비교와 진료 기록을 확인했습니다.", partial ? "" : "복약 안내",
                partial ? "" : "처방된 약 이름", partial ? "" : "변경 내용", partial ? "" : "복약 일정",
                partial ? null : MedicationComparisonDecision.CHANGED, partial ? "" : "변경 확인 메모",
                partial ? "" : "2026-12-22 10:30");
        return new GuardianReportEntry(request, new User("patient", UserRole.PATIENT,
                longText ? "화면에서누락되지않아야하는아주긴환자이름" : "김현숙", "", ""),
                new User("manager", UserRole.MANAGER, "김승민", "", ""), session, report, null);
    }

    private void assertClinicalText(ActivityScenario<FinalReportPreviewActivity> scenario) {
        scenario.onActivity(activity -> {
            String text = collectText(activity.findViewById(R.id.finalReportContent));
            for (String expected : new String[]{"실제 진료 요약", "기존 처방 비교", "복약 안내", "처방된 약 이름",
                    "변경 내용", "복약 일정", "변경 확인 메모", "2026-12-22 10:30", "안전하게 인계했습니다"}) {
                assertTrue(expected, text.contains(expected));
            }
        });
    }

    private static String collectText(View view) {
        if (view.getVisibility() != View.VISIBLE) return "";
        StringBuilder text = new StringBuilder();
        if (view instanceof TextView) text.append(((TextView) view).getText()).append('\n');
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) text.append(collectText(((ViewGroup) view).getChildAt(i)));
        }
        return text.toString();
    }

    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                assertTrue(text.getText().toString(), text.getLayout().getHeight()
                        <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
                for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                    assertEquals(text.getText().toString(), 0, text.getLayout().getEllipsisCount(line));
                    assertTrue("가로 잘림: " + text.getText(), text.getLayout().getLineMax(line)
                            <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
                }
            }
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) assertTextFits(((ViewGroup) view).getChildAt(i));
        }
    }

    private void capture(ActivityScenario<FinalReportPreviewActivity> scenario, String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureReport");
        if (prefix == null) return;
        // 스크롤 좌표뿐 아니라 화면 버퍼에도 최종 위치가 그려진 뒤 캡처한다.
        CountDownLatch frameReady = new CountDownLatch(1);
        scenario.onActivity(activity -> {
            View decor = activity.getWindow().getDecorView();
            decor.postOnAnimation(() -> decor.postOnAnimation(() -> decor.post(frameReady::countDown)));
        });
        try {
            assertTrue("화면 그리기 대기 시간 초과", frameReady.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
        scenario.onActivity(activity -> {
            View content = activity.findViewById(R.id.finalReportContent);
            Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
            content.draw(new Canvas(bitmap));
            File directory = new File(activity.getExternalFilesDir(null), "final-report-captures");
            directory.mkdirs();
            try (FileOutputStream stream = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
            } catch (Exception error) { throw new AssertionError(error); }
            bitmap.recycle();
        });
        Bitmap screen = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File output = new File(context.getExternalFilesDir(null),
                "final-report-captures/" + prefix + "-" + name + "-viewport.png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertNotNull(screen);
            screen.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (Exception error) { throw new AssertionError(error); }
        if (screen != null) screen.recycle();
    }
}
