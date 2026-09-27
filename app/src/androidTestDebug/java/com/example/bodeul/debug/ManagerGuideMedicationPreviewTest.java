package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 서버 권한 없이 복약 수령 전용 화면의 상태·메모·다음 단계 전환을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideMedicationPreviewTest {

    @Test
    public void medicationScreen_updatesProgress_savesNoteAndAdvances() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(
                        context, "MEDICATION_CONFIRMATION"))) {
            assertMedicationScreen();

            onView(withId(R.id.inputGuideMedicationGuidanceNote))
                    .perform(scrollTo(), replaceText("식후 30분에 복용하도록 안내했습니다."));
            closeSoftKeyboard();
            onView(withId(R.id.buttonGuideMedicationToggleGuidance))
                    .perform(scrollTo(), click());
            onView(withId(R.id.inputGuideMedicationGuidanceNote))
                    .check(matches(withText("식후 30분에 복용하도록 안내했습니다.")));
            onView(withId(R.id.textGuideMedicationGuidanceState))
                    .check(matches(withText(R.string.guide_medication_status_done)));
            onView(withId(R.id.textGuideMedicationProgress))
                    .check(matches(withText("3/3 완료")));

            onView(withId(R.id.buttonGuideMedicationSaveGuidanceNote))
                    .perform(scrollTo(), click());

            scenario.recreate();
            onView(withId(R.id.inputGuideMedicationGuidanceNote))
                    .check(matches(withText("식후 30분에 복용하도록 안내했습니다.")));
            onView(withId(R.id.textGuideMedicationProgress))
                    .check(matches(withText("3/3 완료")));

            onView(withId(R.id.inputGuideMedicationPharmacyNote))
                    .perform(scrollTo(), replaceText("조제 대기 없이 약을 수령했습니다."));
            closeSoftKeyboard();
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guideMedicationToolbar)).check(matches(isDisplayed()));

            onView(withId(R.id.buttonGuideMedicationSavePharmacyNote))
                    .perform(scrollTo(), click());
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guideDefaultToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.textGuideTitle))
                    .check(matches(withText("Step 12. 동행 종료")));
            onView(withId(R.id.managerGuideMedicationContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void unsavedInput_survivesStopStartReload() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(
                        context, "MEDICATION_CONFIRMATION"))) {
            onView(withId(R.id.inputGuideMedicationGuidanceNote))
                    .perform(scrollTo(), replaceText("화면 재진입 뒤에도 남아야 하는 안내 메모"));
            closeSoftKeyboard();

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            onView(withId(R.id.managerGuideMedicationContent)).check(matches(isDisplayed()));
            onView(withId(R.id.inputGuideMedicationGuidanceNote))
                    .check(matches(withText("화면 재진입 뒤에도 남아야 하는 안내 메모")));
        }
    }

    @Test
    public void unsavedBackDiscard_finishesPreviewActivity() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch destroyed = new CountDownLatch(1);

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(
                        context, "MEDICATION_CONFIRMATION"))) {
            scenario.onActivity(activity -> activity.getLifecycle().addObserver(
                    (LifecycleEventObserver) (source, event) -> {
                        if (event == Lifecycle.Event.ON_DESTROY && activity.isFinishing()) {
                            destroyed.countDown();
                        }
                    }));
            onView(withId(R.id.inputGuideMedicationPharmacyNote))
                    .perform(scrollTo(), replaceText("저장하지 않고 버릴 약국 메모"));
            closeSoftKeyboard();

            onView(withId(R.id.buttonBackGuideMedication)).perform(click());
            onView(withText(R.string.guide_medication_exit_body))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.guide_consultation_exit_stay)).perform(click());
            onView(withId(R.id.inputGuideMedicationPharmacyNote))
                    .check(matches(withText("저장하지 않고 버릴 약국 메모")));

            onView(withId(R.id.buttonBackGuideMedication)).perform(click());
            onView(withText(R.string.guide_consultation_exit_discard)).perform(click());

            assertTrue("미리보기 화면이 종료되지 않았습니다.",
                    destroyed.await(5, TimeUnit.SECONDS));
        }
    }

    private void assertMedicationScreen() {
        onView(withText(R.string.debug_manager_guide_preview_banner))
                .check(matches(isDisplayed()));
        onView(withId(R.id.guideMedicationToolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.managerGuideMedicationContent)).check(matches(isDisplayed()));
        onView(withId(R.id.textGuideMedicationExistingSummary))
                .check(matches(withText(R.string.guide_medication_existing_empty)));
        onView(withId(R.id.textGuideMedicationPrescriptionStatus))
                .check(matches(withText(R.string.guide_medication_prescription_empty)));
        onView(withId(R.id.textGuideMedicationPrescriptionState))
                .check(matches(withText(R.string.guide_medication_status_done)));
        onView(withId(R.id.textGuideMedicationPharmacyState))
                .check(matches(withText(R.string.guide_medication_status_done)));
        onView(withId(R.id.textGuideMedicationGuidanceState))
                .check(matches(withText(R.string.guide_medication_status_pending)));
        onView(withId(R.id.textGuideMedicationProgress))
                .check(matches(withText("2/3 완료")));
        onView(withId(R.id.buttonGuideMedicationTogglePrescription))
                .check(matches(withContentDescription("처방전 수령 완료 취소")));
        onView(withId(R.id.buttonGuideMedicationToggleGuidance))
                .check(matches(withContentDescription("복약 안내 완료 처리")));
        onView(withId(R.id.buttonAdvanceGuide)).check(matches(isEnabled()));
        onView(withId(R.id.cardGuideNotesActions))
                .check(matches(withEffectiveVisibility(GONE)));
    }
}
