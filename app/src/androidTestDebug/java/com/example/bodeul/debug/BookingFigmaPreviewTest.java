package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 로컬 데이터 예약 미리보기가 Figma 단계 구조를 실제 BookingActivity에 표시하는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class BookingFigmaPreviewTest {
    @Test
    public void preview_showsFigmaBookingHierarchyWithoutServer() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<BookingFigmaPreviewActivity> ignored = ActivityScenario.launch(
                BookingFigmaPreviewActivity.createIntent(context))) {
            onView(withText(R.string.debug_manager_guide_preview_banner))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_heading)).check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_visit)).check(matches(isDisplayed()));
            onView(withId(R.id.layoutBookingHospitalMapPlaceholder))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_health))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText(R.string.booking_main_step_options))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
        }
    }
}
