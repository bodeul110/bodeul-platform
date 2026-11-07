package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.ui.booking.*;
import com.example.bodeul.ui.chat.CompanionChatActivity;
import com.example.bodeul.ui.health.HealthInfoActivity;
import com.example.bodeul.ui.home.ClientHomeDashboard;
import com.example.bodeul.ui.profile.ClientProfileActivity;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.example.bodeul.ui.support.ClientSupportActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 완성본 홈의 실제 레이아웃·기존 이동·비동기 상태를 합성 데이터로 검사한다. */
@RunWith(AndroidJUnit4.class)
public class ClientHomeFigmaPreviewTest {
    @Test public void emptyHomeKeepsOriginalGeometryReadableTextAndFiveTabs() throws Exception {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("empty")) {
            onView(withId(R.id.layoutHomeProgressSection)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.textHomeSupportHeaderBadge)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.clientBottomNavigation)).check(matches(isCompletelyDisplayed()));
            scenario.onActivity(activity -> {
                float density = activity.getResources().getDisplayMetrics().density;
                BottomNavigationView nav = activity.findViewById(R.id.clientBottomNavigation);
                assertEquals(5, nav.getMenu().size());
                assertEquals(R.id.clientNavHome, nav.getSelectedItemId());
                assertEquals(30, nav.getItemIconSize() / density, 1);
                assertEquals(46, activity.findViewById(R.id.imageHomeLogo).getWidth() / density, 1);
                assertEquals(59.807, activity.findViewById(R.id.imageHomeBanner).getWidth() / density, 1);
                View booking = activity.findViewById(R.id.cardActionBooking);
                assertEquals(30, (activity.findViewById(android.R.id.content).getWidth() - booking.getWidth()) / density, 1);
                int[] bellPosition = new int[2];
                View bell = activity.findViewById(R.id.buttonHomeSupport);
                bell.getLocationOnScreen(bellPosition);
                assertEquals(15, (activity.getResources().getDisplayMetrics().widthPixels - bellPosition[0] - bell.getWidth()) / density, 1);
                assertTextFits(activity.findViewById(R.id.homeContentContainer));
                TextView button = activity.findViewById(R.id.buttonHomeHeroPrimary);
                assertTrue(ColorUtils.calculateContrast(button.getCurrentTextColor(), Color.rgb(1,161,255)) >= 4.5);
                assertTrue(button.getHeight() / density >= 50);
                assertEquals(activity.getColor(R.color.final_line), ((android.graphics.drawable.GradientDrawable)
                        activity.findViewById(R.id.textHomeNoticeMore).getBackground()).getColor().getDefaultColor());
                assertEquals(4, ((LinearLayout) activity.findViewById(R.id.layoutHomeNoticeContainer)).getChildCount());
                assertEquals(1, activity.loadCalls);
            });
            capture("empty-top");
            onView(withId(R.id.buttonHomeSignOut)).perform(scrollTo());
            onView(withId(R.id.textHomeNoticeMore)).check(matches(isDisplayed()));
            capture("empty-bottom");
            scenario.onActivity(activity -> {
                ScrollView scroll = activity.findViewById(R.id.scrollClientHome);
                View content = scroll.getChildAt(0);
                Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(Color.WHITE);
                content.draw(canvas);
                save(activity, bitmap, "empty-content");
            });
        }
    }

    @Test public void bookingEntryPointsDelegateToExistingFormWithoutSubmitting() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("empty")) {
            int[] buttons = {R.id.buttonHomePickup, R.id.buttonHomeDate, R.id.buttonHomeTime, R.id.buttonHomeHeroPrimary};
            String[] entries = {"LOCATION", "APPOINTMENT", null, null};
            for (int i = 0; i < buttons.length; i++) {
                onView(withId(buttons[i])).perform(scrollTo(), click());
                String expected = entries[i];
                scenario.onActivity(activity -> {
                    assertTarget(activity.lastNavigation, BookingActivity.class);
                    assertEquals(expected, activity.lastNavigation.getStringExtra("homeEntry"));
                    assertEquals(1, activity.loadCalls);
                });
            }
        }
    }

    @Test public void allExistingReservationStatesKeepDetailsAndNewBookingEntry() throws Exception {
        for (AppointmentStatus status : AppointmentStatus.values()) {
            try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch(status.name())) {
                onView(withId(R.id.textHomeSupportHeaderBadge)).check(matches(withText("7")));
                onView(withId(R.id.textHomeProgressBody)).perform(scrollTo());
                scenario.onActivity(activity -> {
                    String body = ((TextView) activity.findViewById(R.id.textHomeProgressBody)).getText().toString();
                    assertTrue(body.contains("2026-11-07 10:30"));
                    assertTrue(body.contains("병원 정문"));
                    assertEquals(4, ((LinearLayout) activity.findViewById(R.id.layoutHomeProgressStageContainer)).getChildCount());
                    assertTextFits(activity.findViewById(R.id.layoutHomeProgressSection));
                });
                capture(status.name().toLowerCase());
                onView(withId(R.id.buttonHomeProgressDetail)).perform(scrollTo(), click());
                scenario.onActivity(activity -> assertTarget(activity.lastNavigation, BookingStatusActivity.class));
                onView(withId(R.id.buttonHomeHeroPrimary)).perform(scrollTo(), click());
                scenario.onActivity(activity -> assertTarget(activity.lastNavigation, BookingActivity.class));
            }
        }
    }

    @Test public void guardianKeepsUpdatesAndReportDestination() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("guardian")) {
            scenario.onActivity(activity -> assertTrue(((TextView) activity.findViewById(R.id.textHomeProgressBody))
                    .getText().toString().contains("합성 보호자 전달 내용")));
            onView(withId(R.id.buttonHomeProgressDetail)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, GuardianReportActivity.class));
        }
    }

    @Test public void fiveTabsKeepSourceSelectionAndExistingDestinations() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("empty")) {
            int[] ids = {R.id.clientNavCalendar, R.id.clientNavScheduleHistory, R.id.clientNavCompanionRoom, R.id.clientNavProfile};
            Class<?>[] targets = {ClientCalendarActivity.class, ClientBookingHistoryActivity.class, CompanionChatActivity.class, ClientProfileActivity.class};
            for (int i = 0; i < ids.length; i++) {
                onView(withId(ids[i])).perform(click());
                Class<?> expected = targets[i];
                scenario.onActivity(activity -> {
                    assertTarget(activity.lastNavigation, expected);
                    assertEquals(R.id.clientNavHome, ((BottomNavigationView) activity.findViewById(R.id.clientBottomNavigation)).getSelectedItemId());
                });
            }
        }
    }

    @Test public void noticesOpenTheirActualBodyAndSupportHealthSignOutRemainAvailable() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("empty")) {
            onView(withText(R.string.final_home_guide_steps_title)).perform(scrollTo(), click());
            onView(withText(R.string.final_home_guide_steps_body)).check(matches(isDisplayed()));
            onView(withText(R.string.permission_guide_confirm)).perform(click());
            onView(withId(R.id.textHomeNoticeMore)).perform(scrollTo(), click());
            onView(withText(R.string.client_home_service_intro_dialog_body)).check(matches(isDisplayed()));
            onView(withText(R.string.permission_guide_confirm)).perform(click());
            onView(withId(R.id.cardActionSecondary)).perform(scrollTo(), click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, HealthInfoActivity.class));
            onView(withId(R.id.buttonHomeSupport)).perform(click());
            scenario.onActivity(activity -> assertTarget(activity.lastNavigation, ClientSupportActivity.class));
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.buttonHomeSignOut).performClick();
                assertTrue(activity.signedOut);
                assertTarget(activity.lastNavigation, com.example.bodeul.ui.auth.RoleSelectionActivity.class);
            });
        }
    }

    @Test public void failedDashboardCanRetryWithoutShowingStaleContent() throws Exception {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("error")) {
            onView(withId(R.id.homeContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withText(R.string.state_action_retry)).check(matches(isDisplayed()));
            capture("load-error");
            scenario.onActivity(activity -> activity.retrySucceeds = true);
            onView(withText(R.string.state_action_retry)).perform(click());
            onView(withId(R.id.buttonHomeHeroPrimary)).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertEquals(2, activity.loadCalls));
        }
    }

    @Test public void authenticationFailureHidesDataAndAllClientTabs() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("auth-error")) {
            onView(withId(R.id.homeContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.clientBottomNavigation)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withText(R.string.state_auth_title)).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertEquals(0, activity.loadCalls));
        }
    }

    @Test public void stoppedScreenIgnoresOldCallbacksAndReloadsOnReturn() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("loading")) {
            AtomicReference<RepositoryCallback<ClientHomeDashboard>> old = new AtomicReference<>();
            final ClientHomeDashboard[] value = new ClientHomeDashboard[1];
            scenario.onActivity(activity -> { old.set(activity.pending); value[0] = activity.dashboard("empty"); });
            scenario.moveToState(Lifecycle.State.CREATED);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> old.get().onSuccess(value[0]));
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.homeContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            scenario.onActivity(activity -> { assertEquals(2, activity.loadCalls); activity.pending.onSuccess(value[0]); });
            onView(withId(R.id.buttonHomeHeroPrimary)).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void recreationKeepsHeaderAndBottomBarOutsideSystemInsets() {
        try (ActivityScenario<ClientHomeFigmaPreviewActivity> scenario = launch("empty")) {
            for (int i = 0; i < 2; i++) {
                scenario.onActivity(activity -> {
                    View header = activity.findViewById(R.id.layoutClientHomeTopBar);
                    View scroll = activity.findViewById(R.id.scrollClientHome);
                    View nav = activity.findViewById(R.id.clientBottomNavigation);
                    WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(nav);
                    assertNotNull(insets);
                    assertEquals(Math.round(64 * activity.getResources().getDisplayMetrics().density)
                            + insets.getInsets(WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.displayCutout()).bottom, nav.getHeight());
                    assertTrue(header.getPaddingTop() >= insets.getInsets(WindowInsetsCompat.Type.statusBars()).top);
                    assertEquals(header.getBottom(), scroll.getTop());
                    assertEquals(nav.getTop(), scroll.getBottom());
                });
                if (i == 0) scenario.recreate();
            }
        }
    }

    private static ActivityScenario<ClientHomeFigmaPreviewActivity> launch(String mode) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, ClientHomeFigmaPreviewActivity.class).putExtra("scenario", mode));
    }
    private static void assertTarget(Intent intent, Class<?> type) {
        assertNotNull(intent);
        assertEquals(type.getName(), intent.getComponent().getClassName());
    }
    private static void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            if (text.getLayout() != null) {
                assertTrue("세로 잘림: " + text.getText(), text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom() + 1);
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
        try {
            // 스크롤 후 접근성 이벤트와 실제 화면 갱신까지 기다린 뒤 캡처한다.
            InstrumentationRegistry.getInstrumentation().getUiAutomation().waitForIdle(250, 5000);
        } catch (java.util.concurrent.TimeoutException error) {
            throw new AssertionError("화면 캡처 전 대기 실패", error);
        }
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        save(InstrumentationRegistry.getInstrumentation().getTargetContext(), bitmap, name);
    }
    private static void save(Context context, Bitmap bitmap, String name) {
        File dir = new File(context.getFilesDir(), "home-figma");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
