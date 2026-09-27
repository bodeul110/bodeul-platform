package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 서버 권한 없이 처방 자료 전용 화면과 다음 단계 복귀를 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuidePrescriptionPreviewTest {

    @Test
    public void noAttachment_recreateAndAdvanceToMedicationConfirmation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "PRESCRIPTION_DOCUMENTS"))) {
            assertPrescriptionScreen();

            scenario.recreate();
            assertPrescriptionScreen();

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());

            onView(withId(R.id.guideDefaultToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.textGuideTitle))
                    .check(matches(withText("Step 11. 복약 확인")));
            onView(withId(R.id.groupGuideMedicationNote))
                    .check(matches(withEffectiveVisibility(VISIBLE)));
            onView(withId(R.id.managerGuidePrescriptionContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    private void assertPrescriptionScreen() {
        onView(withText(R.string.debug_manager_guide_preview_banner))
                .check(matches(isDisplayed()));
        onView(withId(R.id.textGuidePrescriptionStep))
                .check(matches(withText("STEP 10")));
        onView(withId(R.id.textGuidePrescriptionStatus))
                .check(matches(withText(R.string.guide_prescription_status_empty)));
        onView(withId(R.id.buttonAdvanceGuide))
                .check(matches(isEnabled()));
    }
}
