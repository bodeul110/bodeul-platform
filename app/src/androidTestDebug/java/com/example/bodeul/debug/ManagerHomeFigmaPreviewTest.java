package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.manager.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 네트워크·실제 계정 없이 운영 화면의 표시, 이동 요청, 비동기 경계를 검사한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerHomeFigmaPreviewTest {
    @Test public void emptyHomeMatchesGeometryWithActualZeroCountsAndFiveTabs() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("empty")) {
            onView(withId(R.id.textManagerOngoingCount)).check(matches(withText("0")));
            onView(withId(R.id.textManagerCompletedCount)).check(matches(withText("0")));
            onView(withId(R.id.textManagerCanceledCount)).check(matches(withText("0")));
            onView(withId(R.id.layoutManagerActiveSession)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.layoutManagerHomeBottomNav)).check(matches(isCompletelyDisplayed()));
            scenario.onActivity(activity -> {
                float density = activity.getResources().getDisplayMetrics().density;
                BottomNavigationView nav = activity.findViewById(R.id.layoutManagerHomeBottomNav);
                assertEquals(5, nav.getMenu().size());
                assertEquals(R.id.navManagerHome, nav.getSelectedItemId());
                assertEquals(30, nav.getItemIconSize() / density, 1);
                assertEquals(46, activity.findViewById(R.id.imageManagerLogo).getWidth() / density, 1);
                assertEquals(50, activity.findViewById(R.id.buttonManagerProfile).getWidth() / density, 1);
                int[] bellPosition = new int[2];
                View bell = activity.findViewById(R.id.buttonManagerSupport);
                bell.getLocationOnScreen(bellPosition);
                assertEquals(15, (activity.getResources().getDisplayMetrics().widthPixels
                        - bellPosition[0] - bell.getWidth()) / density, 1);
                assertEquals(30, (activity.findViewById(android.R.id.content).getWidth()
                        - activity.findViewById(R.id.cardManagerMatching).getWidth()) / density, 1);
                assertEquals(4, ((LinearLayout) activity.findViewById(R.id.layoutManagerNoticeContainer)).getChildCount());
                assertTrue(((TextView) activity.findViewById(R.id.textManagerCountState)).getText().toString().contains("100"));
                assertTextFits(activity.findViewById(R.id.managerContentContainer));
                assertDocumentContrast(activity);
                TextView subtitle = activity.findViewById(R.id.textManagerSubtitle);
                assertTrue(ColorUtils.calculateContrast(subtitle.getCurrentTextColor(), Color.rgb(196, 242, 255)) >= 4.5);
                TextView header = activity.findViewById(R.id.buttonManagerMatchingHistory);
                assertTrue(ColorUtils.calculateContrast(header.getCurrentTextColor(), activity.getColor(R.color.final_brand_primary)) >= 4.5);
                assertEquals(1, activity.appointmentLoads);
            });
            capture("empty-top");
            onView(withText(R.string.final_home_copyright)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            capture("empty-bottom");
            scenario.onActivity(activity -> {
                View content = ((android.widget.ScrollView) activity.findViewById(R.id.scrollManagerHome)).getChildAt(0);
                Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                content.draw(canvas);
                save(activity, bitmap, "empty-content");
            });
        }
    }

    @Test public void countsReflectActualStatusesAndCanceledFilterIsReadOnly() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("mixed")) {
            onView(withId(R.id.textManagerOngoingCount)).check(matches(withText("2")));
            onView(withId(R.id.textManagerCompletedCount)).check(matches(withText("1")));
            onView(withId(R.id.textManagerCanceledCount)).check(matches(withText("1")));
            onView(withId(R.id.buttonManagerMatchingHistory)).perform(scrollTo(), click());
            onView(withId(R.id.chipManagerCanceled)).perform(scrollTo(), click());
            scenario.onActivity(activity -> {
                LinearLayout list = activity.findViewById(R.id.layoutManagerAppointmentsList);
                assertEquals(1, list.getChildCount());
                assertTrue(((TextView) list.getChildAt(0).findViewById(R.id.textManagerMatchingHospital)).getText().toString().endsWith("canceled"));
                assertEquals(1, activity.appointmentLoads);
                assertEquals(0, activity.saves);
            });
            capture("matching-canceled");
        }
    }

    @Test public void calendarUsesSelectedDayAndDoesNotTreatUndatedAsToday() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("mixed")) {
            onView(withId(R.id.navManagerCalendar)).perform(click());
            onView(org.hamcrest.Matchers.allOf(withId(R.id.calendarManagerAppointments),
                    isAssignableFrom(CalendarView.class))).check(matches(isDisplayed()));
            onView(withId(R.id.textManagerSelectedDate)).perform(scrollTo()).check(matches(withText(ManagerHomeFigmaPreviewActivity.today())));
            onView(withId(R.id.textManagerUndated)).perform(scrollTo()).check(matches(isCompletelyDisplayed()));
            scenario.onActivity(activity -> assertEquals(3, ((LinearLayout) activity.findViewById(R.id.layoutManagerAppointmentsList)).getChildCount()));
            onView(withId(R.id.chipManagerCompleted)).perform(scrollTo(), click());
            onView(withId(R.id.textManagerAppointmentsState)).perform(scrollTo()).check(matches(withText(R.string.final_manager_filtered_empty)));
            capture("calendar-filtered");
            scenario.onActivity(activity -> assertEquals(1, activity.appointmentLoads));
        }
    }

    @Test public void currentGuideRemainsReachableFromHomeAndOnlyItsOwnRow() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("active")) {
            scenario.onActivity(activity -> {
                TextView guide = activity.findViewById(R.id.buttonManagerHeroPrimary);
                assertTrue("가이드 버튼 글자 대비", ColorUtils.calculateContrast(guide.getCurrentTextColor(),
                        activity.getColor(R.color.final_brand_primary)) >= 4.5);
            });
            onView(withId(R.id.buttonManagerHeroPrimary)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, ManagerGuideActivity.class));
            capture("active-session");
            onView(withId(R.id.navManagerMatching)).perform(click());
            scenario.onActivity(activity -> {
                LinearLayout list = activity.findViewById(R.id.layoutManagerAppointmentsList);
                int guideCount = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    View guide = list.getChildAt(i).findViewById(R.id.buttonManagerMatchingGuide);
                    if (guide.getVisibility() == View.VISIBLE) { guideCount++; guide.performClick(); }
                }
                assertEquals(1, guideCount);
                assertTarget(activity.lastNavigation, ManagerGuideActivity.class);
            });
        }
    }

    @Test public void previousDestinationsAndLogoutArePreserved() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("empty")) {
            int[] views = {R.id.buttonManagerDocuments, R.id.buttonManagerProfile, R.id.buttonManagerSupport,
                    R.id.navManagerChat, R.id.navManagerProfile, R.id.buttonManagerHistory, R.id.buttonManagerFooterSupport};
            Class<?>[] targets = {ManagerDocumentRegistrationActivity.class, ManagerProfileActivity.class, ManagerSupportActivity.class,
                    CompanionChatActivity.class, ManagerProfileActivity.class, ManagerHistoryActivity.class, ManagerSupportActivity.class};
            for (int i = 0; i < views.length; i++) {
                int id = views[i];
                Class<?> target = targets[i];
                scenario.onActivity(activity -> { activity.findViewById(id).performClick(); assertTarget(activity.lastNavigation, target); });
            }
            AtomicReference<ManagerHomeFigmaPreviewActivity> beforeLogout = new AtomicReference<>();
            scenario.onActivity(beforeLogout::set);
            onView(withId(R.id.buttonManagerLogout)).perform(scrollTo(), click());
            assertTrue(beforeLogout.get().signedOut);
            assertTarget(beforeLogout.get().lastNavigation, com.example.bodeul.ui.auth.RoleSelectionActivity.class);
        }
    }

    @Test public void matchingFailureNeverShowsZeroAndRetryRecovers() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("error")) {
            onView(withId(R.id.textManagerOngoingCount)).check(matches(withText(R.string.final_manager_unknown_count)));
            onView(withId(R.id.textManagerCountState)).check(matches(withText(R.string.final_manager_count_error)));
            capture("matching-error");
            scenario.onActivity(activity -> activity.retrySucceeds = true);
            onView(withId(R.id.buttonManagerCountRetry)).perform(scrollTo(), click());
            onView(withId(R.id.textManagerOngoingCount)).check(matches(withText("0")));
            onView(withId(R.id.buttonManagerCountRetry)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void failedMatchingDoesNotHideActiveGuideOrDocuments() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("partial-error")) {
            onView(withId(R.id.buttonManagerHeroPrimary)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, ManagerGuideActivity.class));
            onView(withId(R.id.buttonManagerDocuments)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, ManagerDocumentRegistrationActivity.class));
        }
    }

    @Test public void sessionFailureIsNotMistakenForNoActiveSession() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("session-error")) {
            onView(withId(R.id.textManagerActiveTitle)).perform(scrollTo()).check(matches(withText(R.string.final_manager_session_error)));
            scenario.onActivity(activity -> activity.retrySucceeds = true);
            onView(withId(R.id.buttonManagerHeroPrimary)).perform(scrollTo(), click());
            onView(withId(R.id.layoutManagerActiveSession)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void oldReadAfterRefreshAndAfterStopCannotReplaceCurrentState() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("loading")) {
            AtomicReference<RepositoryCallback<List<AppointmentRequest>>> first = new AtomicReference<>();
            scenario.onActivity(activity -> first.set(activity.pendingAppointments));
            onView(withId(R.id.navManagerHome)).perform(click());
            scenario.onActivity(activity -> {
                first.get().onSuccess(ManagerHomeFigmaPreviewActivity.requests());
                assertEquals(activity.getString(R.string.final_manager_unknown_count), ((TextView) activity.findViewById(R.id.textManagerOngoingCount)).getText().toString());
                activity.pendingAppointments.onSuccess(java.util.Collections.emptyList());
                assertEquals("0", ((TextView) activity.findViewById(R.id.textManagerOngoingCount)).getText().toString());
                first.set(activity.pendingAppointments);
            });
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> first.get().onSuccess(ManagerHomeFigmaPreviewActivity.requests()));
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.textManagerOngoingCount)).check(matches(withText(R.string.final_manager_unknown_count)));
        }
    }

    @Test public void authAndRoleFailureHideContentAndAllProtectedActions() {
        for (String mode : new String[]{"auth-error", "wrong-role"}) {
            try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch(mode)) {
                onView(withId(R.id.managerContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.layoutManagerHomeBottomNav)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                scenario.onActivity(activity -> {
                    activity.findViewById(R.id.buttonManagerDocuments).performClick();
                    activity.findViewById(R.id.buttonManagerSupport).performClick();
                    assertNull(activity.lastNavigation);
                    assertEquals(0, activity.appointmentLoads);
                });
            }
        }
    }

    @Test public void losingAuthOnReturnClearsOldNameCountsAndList() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("mixed")) {
            onView(withId(R.id.navManagerMatching)).perform(click());
            scenario.onActivity(activity -> activity.authFailure = true);
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> {
                assertEquals("", ((TextView) activity.findViewById(R.id.textManagerGreeting)).getText().toString());
                assertEquals(0, ((LinearLayout) activity.findViewById(R.id.layoutManagerAppointmentsList)).getChildCount());
                assertEquals(View.GONE, activity.findViewById(R.id.managerAppointmentsPanel).getVisibility());
            });
        }
    }

    @Test public void calendarFilterAndTabSurviveRecreationAndBackReturnsHome() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("mixed")) {
            onView(withId(R.id.navManagerCalendar)).perform(click());
            onView(withId(R.id.chipManagerCanceled)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.chipManagerCanceled)).perform(scrollTo()).check(matches(isChecked()));
            scenario.onActivity(activity -> {
                assertEquals(R.id.navManagerCalendar, ((BottomNavigationView) activity.findViewById(R.id.layoutManagerHomeBottomNav)).getSelectedItemId());
                activity.getOnBackPressedDispatcher().onBackPressed();
                assertEquals(R.id.navManagerHome, ((BottomNavigationView) activity.findViewById(R.id.layoutManagerHomeBottomNav)).getSelectedItemId());
                assertEquals(1, activity.appointmentLoads);
            });
            onView(withId(R.id.managerContentContainer)).check(matches(isDisplayed()));
        }
    }

    @Test public void scheduleNoteKeepsExistingValueAndSavePath() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("mixed")) {
            onView(withId(R.id.navManagerCalendar)).perform(click());
            onView(withId(R.id.buttonManagerScheduleNote)).perform(scrollTo(), click());
            onView(withId(R.id.inputManagerQuickNote)).inRoot(isDialog()).check(matches(withText("평일 오전 가능"))).perform(replaceText("수요일 오후 가능"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.textManagerScheduleNote)).check(matches(withText("수요일 오후 가능")));
            scenario.onActivity(activity -> { assertEquals(1, activity.saves); assertEquals("수요일 오후 가능", activity.savedNote); });
        }
    }

    @Test public void scheduleSaveCannotBeSubmittedTwiceWhilePending() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("saving")) {
            onView(withId(R.id.navManagerCalendar)).perform(click());
            onView(withId(R.id.buttonManagerScheduleNote)).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            scenario.onActivity(activity -> { assertEquals(1, activity.saves); activity.pendingSave.onError("합성 저장 오류"); });
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check(matches(isEnabled()));
        }
    }

    @Test public void failedProfileReadDoesNotOpenABlankScheduleEditor() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("profile-error")) {
            onView(withId(R.id.navManagerCalendar)).perform(click());
            onView(withId(R.id.buttonManagerScheduleNote)).perform(scrollTo()).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withId(R.id.textManagerScheduleNote)).check(matches(withText(R.string.final_manager_profile_error)));
        }
    }

    @Test public void noticesAndMoreUseReadableConfirmationDialogs() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("empty")) {
            scenario.onActivity(activity -> ((LinearLayout) activity.findViewById(R.id.layoutManagerNoticeContainer)).getChildAt(0).performClick());
            checkDialogContrast();
            capture("notice-dialog");
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            onView(withId(R.id.buttonManagerNoticeMore)).perform(scrollTo(), click());
            checkDialogContrast();
            capture("more-dialog");
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
        }
    }

    @Test public void longNameAndFontScaleDoNotClipTheLayout() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("long-name")) {
            scenario.onActivity(activity -> assertTextFits(activity.findViewById(R.id.managerContentContainer)));
            capture("long-name");
        }
    }

    @Test public void fixedBarsStayOutsideSystemInsetsAndContentDoesNotOverlapNavigation() {
        try (ActivityScenario<ManagerHomeFigmaPreviewActivity> scenario = launch("empty")) {
            scenario.onActivity(activity -> {
                View nav = activity.findViewById(R.id.layoutManagerHomeBottomNav);
                View scroll = activity.findViewById(R.id.scrollManagerHome);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(nav);
                assertNotNull(insets);
                int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
                float density = activity.getResources().getDisplayMetrics().density;
                assertTrue(nav.getHeight() >= Math.round(64 * density) + bottom);
                assertTrue(nav.getPaddingBottom() >= bottom);
                assertTrue(scroll.getBottom() <= nav.getTop());
            });
        }
    }

    private static ActivityScenario<ManagerHomeFigmaPreviewActivity> launch(String mode) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, ManagerHomeFigmaPreviewActivity.class).putExtra("scenario", mode));
    }
    private static void assertTarget(Intent intent, Class<?> target) { assertNotNull(intent); assertEquals(target.getName(), intent.getComponent().getClassName()); }
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
    private static void checkDialogContrast() {
        onView(withId(android.R.id.button1)).inRoot(isDialog()).check((view, error) -> {
            if (error != null) throw error;
            TextView button = (TextView) view;
            assertTrue(ColorUtils.calculateContrast(button.getCurrentTextColor(), Color.WHITE) >= 4.5);
            assertTextFits(button);
        });
    }
    private static void assertDocumentContrast(ManagerHomeFigmaPreviewActivity activity) {
        View bar = activity.findViewById(R.id.buttonManagerDocuments);
        Bitmap bitmap = Bitmap.createBitmap(bar.getWidth(), bar.getHeight(), Bitmap.Config.ARGB_8888);
        Drawable background = bar.getBackground();
        background.draw(new Canvas(bitmap));
        for (int id : new int[]{R.id.textManagerDocuments, R.id.textManagerDocumentDetail}) {
            TextView text = activity.findViewById(id);
            for (int x = text.getLeft(); x < text.getRight(); x++) {
                int color = bitmap.getPixel(x, bar.getHeight() / 2);
                assertTrue("서류 바 글자 대비: " + x, ColorUtils.calculateContrast(text.getCurrentTextColor(), color) >= 4.5);
            }
        }
        bitmap.recycle();
    }
    private static void capture(String name) {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try { InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000); }
        catch (java.util.concurrent.TimeoutException error) { throw new AssertionError(error); }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        save(InstrumentationRegistry.getInstrumentation().getTargetContext(), bitmap, name);
    }
    private static void save(Context context, Bitmap bitmap, String name) {
        File directory = new File(context.getFilesDir(), "manager-home-figma");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
