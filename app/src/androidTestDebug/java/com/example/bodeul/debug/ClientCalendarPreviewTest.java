package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.allOf;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.CalendarView;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.UserRole;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ClientCalendarPreviewTest {
    @Test public void calendarUsesExistingUserScopedReadAndKeepsFiveTabs() {
        try (ActivityScenario<ClientCalendarPreviewActivity> scenario = launch(false)) {
            onView(allOf(withId(R.id.clientCalendar), isAssignableFrom(CalendarView.class))).check(matches(isDisplayed()));
            scenario.onActivity(activity -> {
                assertEquals(1, activity.reads);
                assertEquals(UserRole.PATIENT, activity.requestedUser.getRole());
                assertEquals(1, ((LinearLayout) activity.findViewById(R.id.layoutClientBookingHistoryEntries)).getChildCount());
                assertEquals(R.id.clientNavCalendar, ((BottomNavigationView) activity.findViewById(R.id.clientBottomNavigation)).getSelectedItemId());
                assertEquals(activity.findViewById(R.id.clientBottomNavigation).getTop(),
                        activity.findViewById(R.id.clientBookingHistoryScroll).getBottom());
            });
        }
    }
    @Test public void emptyDateKeepsCalendarReachableAndSurvivesRecreation() {
        try (ActivityScenario<ClientCalendarPreviewActivity> scenario = launch(false)) {
            scenario.onActivity(activity -> activity.selectDate(2030, 0, 2));
            onView(withId(R.id.textClientCalendarEmpty)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.clientBookingHistoryContentContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.final_calendar_date, 2030, 1, 2),
                        ((TextView) activity.findViewById(R.id.textClientCalendarDate)).getText().toString());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.clientCalendarContainer).getVisibility());
                assertEquals(1, activity.reads);
            });
        }
    }
    @Test public void readErrorHidesCalendarUntilSuccessfulRetry() {
        try (ActivityScenario<ClientCalendarPreviewActivity> scenario = launch(true)) {
            onView(withId(R.id.clientCalendarContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            scenario.onActivity(activity -> activity.retrySucceeds = true);
            onView(withText(R.string.state_action_retry)).perform(scrollTo(), click());
            onView(allOf(withId(R.id.clientCalendar), isAssignableFrom(CalendarView.class))).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.onActivity(activity -> assertEquals(2, activity.reads));
        }
    }
    private static ActivityScenario<ClientCalendarPreviewActivity> launch(boolean error) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, ClientCalendarPreviewActivity.class).putExtra("error", error));
    }
}
