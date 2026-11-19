package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.BuildConfig;
import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.ClientProgressActivity;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.profile.ProfileInfoActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FinalDetailBatchTest {
    private User user;
    private List<AppointmentRequest> appointments;
    private AppointmentRequestDetail detail;
    private ManagerDashboard manager;
    private RepositoryCallback<List<AppointmentRequest>> pending;
    private RepositoryCallback<AppointmentRequestDetail> stream;
    private int listing, observing, closed, dashboard;
    private boolean delayed, fail;
    private static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private static User account(String id,UserRole role) { return new User(id,role,"테스트 사용자","qa@example.test","01000000000"); }
    private static AppointmentRequest row(String id,String hospital,AppointmentStatus state) {
        AppointmentRequest r = new AppointmentRequest(id,"p","g",hospital,"내과","2026-11-19 10:00","본관","",state,"m");
        r.setManagerProfile("테스트 매니저","","");
        return r;
    }
    @Before @SuppressWarnings("unchecked") public void setUp() {
        user = account("p",UserRole.PATIENT);
        appointments = Arrays.asList(row("one","가람병원",AppointmentStatus.IN_PROGRESS),row("two","새봄병원",AppointmentStatus.COMPLETED));
        MockBodeulRepository store = new MockBodeulRepository();
        AppointmentRequestDetail sample = store.getAppointmentRequestDetail("request-1");
        sample.getSession().setCurrentStepCode("HOSPITAL_ROUTE");
        detail = new AppointmentRequestDetail(sample.getAppointmentRequest(),null,null,null,sample.getSession(),null,
                new HospitalGuide("guide","가람병원","내과",Arrays.asList(
                        new GuideStep("MEETING_CONFIRMATION",1,"만남",""),new GuideStep("HOSPITAL_ROUTE",2,"병원",""),
                        new GuideStep("RECEPTION_QUEUE",3,"접수",""),new GuideStep("VITALS_CHECK",4,"기초 측정",""),
                        new GuideStep("MANAGER_JOURNAL",5,"비공개 일지",""))));
        manager = store.getManagerDashboard("manager-1");
        ProfilePreviewDependencies.auth = (AuthRepository)Proxy.newProxyInstance(AuthRepository.class.getClassLoader(),
                new Class[]{AuthRepository.class},(p,m,args) -> {
                    if (!m.getName().equals("getCurrentUser")) throw new AssertionError(m.getName());
                    ((RepositoryCallback<User>)args[0]).onSuccess(user); return null;
                });
        BatchPreviewActivities.bookings = (BookingRepository)Proxy.newProxyInstance(BookingRepository.class.getClassLoader(),
                new Class[]{BookingRepository.class},(p,m,args) -> {
                    if (!user.getId().equals(((User)args[0]).getId())) throw new AssertionError("요청 주체 불일치");
                    if (m.getName().equals("getMyAppointmentRequests")) {
                        listing++;
                        pending = (RepositoryCallback<List<AppointmentRequest>>)args[1];
                        if (!delayed) { if (fail) pending.onError("테스트"); else pending.onSuccess(appointments); }
                        return null;
                    }
                    if (m.getName().equals("observeAppointmentRequestDetail")) {
                        observing++;
                        stream = (RepositoryCallback<AppointmentRequestDetail>)args[2];
                        stream.onSuccess(detail);
                        return (Runnable)() -> closed++;
                    }
                    throw new AssertionError("허용되지 않은 저장소 호출: " + m.getName());
                });
        BatchPreviewActivities.managers = (ManagerRepository)Proxy.newProxyInstance(ManagerRepository.class.getClassLoader(),
                new Class[]{ManagerRepository.class},(p,m,args) -> {
                    if (!m.getName().equals("getManagerDashboard")) throw new AssertionError(m.getName());
                    dashboard++;
                    ((RepositoryCallback<ManagerDashboard>)args[1]).onSuccess(manager);
                    return null;
                });
    }
    @After public void tearDown() {
        BatchPreviewActivities.bookings = null; BatchPreviewActivities.managers = null;
        ProfilePreviewDependencies.auth = null; ProfilePreviewDependencies.lastNavigation = null;
    }
    private static ActivityScenario<BatchPreviewActivities.Info> info(ProfileInfoActivity.Page p) {
        return ActivityScenario.launch(ProfileInfoActivity.createIntent(context(),p).setClass(context(),BatchPreviewActivities.Info.class));
    }
    private ActivityScenario<BatchPreviewActivities.Progress> progress(String id) {
        return ActivityScenario.launch(ClientProgressActivity.createIntent(context(),id).setClass(context(),BatchPreviewActivities.Progress.class));
    }
    @Test public void searchAndFiltersOpenOnlySelectedRoomWithoutFetchingOrMarkingMessages() {
        try (ActivityScenario<BatchPreviewActivities.Rooms> s = ActivityScenario.launch(BatchPreviewActivities.Rooms.class)) {
            onView(withText("가람병원")).check(matches(isDisplayed()));
            screenshot("rooms");
            onView(withId(R.id.batchRoomSearch)).perform(replaceText("새봄"),closeSoftKeyboard());
            onView(withText("가람병원")).check(doesNotExist());
            onView(withText("새봄병원")).check(matches(isDisplayed()));
            screenshot("search");
            onView(withText(R.string.batch_chat_active)).perform(click());
            onView(withText(R.string.batch_chat_no_match)).check(matches(isDisplayed()));
            onView(withText(R.string.batch_chat_unread)).perform(click());
            onView(withText(R.string.batch_chat_unread_unavailable)).check(matches(isDisplayed()));
            onView(withText(R.string.batch_all)).perform(click());
            onView(withText("새봄병원")).perform(click());
            assertEquals(CompanionChatActivity.class.getName(),ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
            assertEquals("two",ProfilePreviewDependencies.lastNavigation.getStringExtra("requestId"));
            assertEquals(1,listing); assertEquals(0,observing);
        }
    }
    @Test public void oldListAndQueryDoNotCrossAccountOrSurviveFailure() {
        try (ActivityScenario<BatchPreviewActivities.Rooms> s = ActivityScenario.launch(BatchPreviewActivities.Rooms.class)) {
            onView(withId(R.id.batchRoomSearch)).perform(replaceText("새봄"),closeSoftKeyboard());
            RepositoryCallback<List<AppointmentRequest>> old = pending;
            s.moveToState(Lifecycle.State.CREATED);
            user = account("g",UserRole.GUARDIAN);
            delayed = true;
            s.moveToState(Lifecycle.State.RESUMED);
            s.onActivity(a -> { old.onSuccess(appointments); pending.onError("테스트"); });
            onView(withText("새봄병원")).check(doesNotExist());
            onView(withText(R.string.batch_data_error)).check(matches(isDisplayed()));
            delayed = false;
            onView(withText(R.string.final_profile_retry)).perform(click());
            onView(withId(R.id.batchRoomSearch)).check(matches(withText("")));
            onView(withText("가람병원")).check(matches(isDisplayed()));
            onView(withText("테스트 매니저")).check(doesNotExist());
            onView(withText("새봄병원")).check(doesNotExist());
        }
    }
    @Test public void systemKeyboardLeavesSearchVisibleAndBottomTabsReturnOnDismiss() {
        try (ActivityScenario<BatchPreviewActivities.Rooms> s = ActivityScenario.launch(BatchPreviewActivities.Rooms.class)) {
            onView(withId(R.id.batchRoomSearch)).perform(click(),typeText("a"));
            onView(withId(R.id.batchRoomSearch)).check(matches(isCompletelyDisplayed()));
            awaitNavigation(s, false);
            onView(withId(R.id.batchRoomSearch)).perform(closeSoftKeyboard());
            awaitNavigation(s, true);
        }
    }
    private static void awaitNavigation(ActivityScenario<BatchPreviewActivities.Rooms> s,boolean visible) {
        long deadline = android.os.SystemClock.elapsedRealtime() + 5000;
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            boolean[] ready = {false};
            s.onActivity(a -> {
                android.view.View nav = a.findViewById(R.id.clientBottomNavigation);
                androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(nav);
                ready[0] = insets != null && insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) != visible
                        && nav.isShown() == visible;
            });
            if (ready[0]) return;
            android.os.SystemClock.sleep(50);
        }
        fail("키보드와 하단 내비게이션 표시 전환 실패");
    }
    @Test public void managerGetsOnlyCurrentAssignmentNotPastRoomsOrPatientList() {
        user = account("manager-1",UserRole.MANAGER);
        try (ActivityScenario<BatchPreviewActivities.Rooms> s = ActivityScenario.launch(BatchPreviewActivities.Rooms.class)) {
            onView(withText(manager.getAppointmentRequest().getHospitalName())).check(matches(isDisplayed()));
            assertEquals(0,listing); assertEquals(1,dashboard);
            s.moveToState(Lifecycle.State.CREATED);
            manager.getSession().applyCompletionState(1L,"","",0,"",0L,java.util.Collections.emptyList());
            s.moveToState(Lifecycle.State.RESUMED);
            onView(withText(R.string.batch_chat_no_rooms)).check(matches(isDisplayed()));
        }
    }
    @Test public void paymentQueryIsPatientOnlyAndErrorsAreNotShownAsEmpty() {
        try (ActivityScenario<BatchPreviewActivities.Payment> s = ActivityScenario.launch(BatchPreviewActivities.Payment.class)) {
            onView(withText(R.string.batch_payment_card_unavailable)).check(matches(isDisplayed()));
            screenshot("payment");
            onView(withText(R.string.batch_payment_card)).perform(click());
            onView(withText(R.string.permission_guide_confirm)).inRoot(isDialog()).perform(click());
            s.moveToState(Lifecycle.State.CREATED); fail = true; s.moveToState(Lifecycle.State.RESUMED);
            onView(withText(R.string.batch_data_error)).check(matches(isDisplayed()));
            onView(withText(R.string.batch_payment_none)).check(doesNotExist());
            int old = listing;
            s.moveToState(Lifecycle.State.CREATED); user = account("g",UserRole.GUARDIAN); s.moveToState(Lifecycle.State.RESUMED);
            onView(withText(R.string.batch_payment_patient_only)).check(matches(isDisplayed()));
            assertEquals(old,listing);
        }
    }
    @Test public void paymentHistoryLinksKnownReservationWithoutTreatingItAsStoredCard() {
        AppointmentRequest paid = new AppointmentRequest("payment-id","p",null,"늘봄병원","내과","2026-11-19 10:00","본관","",
                AppointmentStatus.MATCHED,"m","","","","","","","","","INDEPENDENT","ONE_WAY","ANY",
                "BANK_TRANSFER","",50000,0,0,50000);
        appointments = java.util.Collections.singletonList(paid);
        try (ActivityScenario<BatchPreviewActivities.Payment> s = ActivityScenario.launch(BatchPreviewActivities.Payment.class)) {
            onView(withText(new com.example.bodeul.ui.booking.BookingPresentationFormatter(context()).formatPrice(50000)))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("늘봄병원")).perform(scrollTo(),click());
            assertEquals(com.example.bodeul.ui.booking.BookingStatusActivity.class.getName(),
                    ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
            assertEquals("payment-id",ProfilePreviewDependencies.lastNavigation.getStringExtra("requestId"));
        }
    }
    @Test public void timelineIsReadOnlyAndClearsRevokedOrMismatchedDetail() {
        user = account("g",UserRole.GUARDIAN);
        try (ActivityScenario<BatchPreviewActivities.Progress> s = progress("request-1")) {
            onView(withText(R.string.final_guide_group_2)).check(matches(isDisplayed()));
            onView(withText(R.string.batch_progress_now)).check(matches(isDisplayed()));
            onView(withText("비공개 일지")).check(doesNotExist());
            onView(withText(R.string.batch_report)).check(doesNotExist());
            screenshot("progress");
            RepositoryCallback<AppointmentRequestDetail> old = stream;
            s.onActivity(a -> { old.onError("공유 종료"); old.onSuccess(detail); });
            assertEquals(1,closed);
            onView(withText(R.string.final_guide_group_2)).check(doesNotExist());
            onView(withText(R.string.batch_data_error)).check(matches(isDisplayed()));
            onView(withText(R.string.final_profile_retry)).perform(click());
            s.moveToState(Lifecycle.State.CREATED);
            assertEquals(2,closed);
        }
        try (ActivityScenario<BatchPreviewActivities.Progress> s = progress("another")) {
            onView(withText(R.string.batch_progress_missing)).check(matches(isDisplayed()));
        }
        user = account("m",UserRole.MANAGER);
        int before = observing;
        try (ActivityScenario<BatchPreviewActivities.Progress> s = progress("request-1")) {
            onView(withText(R.string.batch_progress_missing)).check(matches(isDisplayed()));
            assertEquals(before,observing);
        }
    }
    @Test public void policyFilterRestoresWithoutInventingDocumentsAndVersionMatchesBuild() {
        try (ActivityScenario<BatchPreviewActivities.Info> s = info(ProfileInfoActivity.Page.TERMS)) {
            onView(withText(R.string.batch_policy_terms)).perform(click());
            s.recreate();
            onView(withText(context().getString(R.string.batch_policy_missing,context().getString(R.string.batch_policy_terms))))
                    .check(matches(isDisplayed()));
            screenshot("terms");
        }
        try (ActivityScenario<BatchPreviewActivities.Info> s = info(ProfileInfoActivity.Page.NOTICE)) {
            screenshot("notices");
            onView(withText(R.string.batch_event)).perform(click());
            onView(withText(context().getString(R.string.batch_notice_unavailable,context().getString(R.string.batch_event))))
                    .check(matches(isDisplayed()));
        }
        try (ActivityScenario<BatchPreviewActivities.Info> s = info(ProfileInfoActivity.Page.VERSION)) {
            onView(withText(context().getString(R.string.batch_installed_version,BuildConfig.VERSION_NAME))).check(matches(isDisplayed()));
            screenshot("version");
        }
    }
    @Test public void onlyReportForSelectedCompletedSessionCanBeOpened() {
        detail.getAppointmentRequest().setStatus(AppointmentStatus.COMPLETED);
        try (ActivityScenario<BatchPreviewActivities.Progress> s = progress("request-1")) {
            for (String session : Arrays.asList("different", detail.getSession().getId())) {
                SessionReport report = new SessionReport("r",session,"","","","","","",null,"","");
                AppointmentRequestDetail updated = new AppointmentRequestDetail(detail.getAppointmentRequest(),null,null,null,
                        detail.getSession(),report,detail.getHospitalGuide());
                s.onActivity(a -> stream.onSuccess(updated));
                if (session.equals("different")) onView(withText(R.string.batch_report)).check(doesNotExist());
                else onView(withText(R.string.batch_report)).perform(scrollTo(),click());
            }
            assertEquals(com.example.bodeul.ui.report.FinalReportActivity.class.getName(),
                    ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
            assertEquals("request-1",ProfilePreviewDependencies.lastNavigation.getStringExtra("requestId"));
        }
    }
    @Test public void profileEditRetainsVerificationAndExistingHealthRoute() {
        user = account("g",UserRole.GUARDIAN);
        try (ActivityScenario<BatchPreviewActivities.Info> s = info(ProfileInfoActivity.Page.EDIT)) {
            screenshot("profile");
            onView(withText(R.string.batch_edit_action)).perform(click());
            onView(withText(R.string.final_profile_edit_unavailable)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(R.string.permission_guide_confirm)).inRoot(isDialog()).perform(click());
            onView(withText(R.string.final_profile_account)).perform(click());
            onView(withText(R.string.final_profile_health)).inRoot(isDialog()).perform(click());
            assertEquals(HealthInfoActivity.class.getName(),ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
            onView(withText(R.string.final_profile_health)).perform(click());
            assertEquals(HealthInfoActivity.class.getName(),ProfilePreviewDependencies.lastNavigation.getComponent().getClassName());
        }
    }
    private static void screenshot(String name) {
        String prefix = InstrumentationRegistry.getArguments().getString("captureBatch");
        if (prefix == null) return;
        android.app.Instrumentation i = InstrumentationRegistry.getInstrumentation();
        i.waitForIdleSync();
        try { i.getUiAutomation().waitForIdle(220,4000); } catch (java.util.concurrent.TimeoutException e) { throw new AssertionError(e); }
        Bitmap b = i.getUiAutomation().takeScreenshot();
        assertNotNull(b);
        File dir = new File(context().getExternalFilesDir(null),"detail-batch");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir,prefix+"-"+name+".png"))) {
            assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));
        } catch (java.io.IOException e) { throw new AssertionError(e); } finally { b.recycle(); }
    }
}
