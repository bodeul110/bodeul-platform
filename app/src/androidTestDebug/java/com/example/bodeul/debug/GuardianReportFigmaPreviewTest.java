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

/** 완료된 로컬 동행이 보호자용 Figma 리포트 구조로 표현되는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class GuardianReportFigmaPreviewTest {
    @Test
    public void preview_showsCompletedReportWithRealModelFields() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<GuardianReportFigmaPreviewActivity> ignored = ActivityScenario.launch(
                GuardianReportFigmaPreviewActivity.createIntent(context))) {
            onView(withText(R.string.debug_manager_guide_preview_banner))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withId(R.id.guardianFinalReportGroup)).check(matches(isDisplayed()));
            onView(withText(R.string.guardian_final_report_completion_title))
                    .check(matches(isDisplayed()));
            onView(withText("서울내과병원")).check(matches(isDisplayed()));
            onView(withText("신경과")).check(matches(isDisplayed()));
            onView(withText(R.string.guardian_final_report_treatment_title))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText("오늘 동행을 안전하게 마쳤습니다."))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withId(R.id.cardGuardianFinalReportManager))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
            onView(withText("김승민"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
        }
    }
}
