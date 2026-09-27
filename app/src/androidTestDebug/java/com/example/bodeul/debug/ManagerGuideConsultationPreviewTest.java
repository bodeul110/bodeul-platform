package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.lifecycle.Lifecycle;

import com.example.bodeul.R;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 서버 권한이나 실제 음성 저장 없이 진료 보조 전용 화면의 로컬 미리보기를 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideConsultationPreviewTest {

    @Test
    public void unsavedInputsAreNotRestoredIntoFreshActivityFromViewHierarchyState() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SparseArray<Parcelable> hierarchyState = new SparseArray<>();

        try (ActivityScenario<ManagerGuidePreviewActivity> first = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CONSULTATION_SUPPORT"))) {
            onView(withId(R.id.inputGuideConsultationGuardian))
                    .perform(scrollTo(), replaceText("프로세스 복원에서 버릴 보호자 입력"));
            onView(withId(R.id.inputGuideConsultationFieldNote))
                    .perform(scrollTo(), replaceText("프로세스 복원에서 버릴 현장 메모"));
            closeSoftKeyboard();

            first.onActivity(activity -> {
                View consultationContent = activity.findViewById(
                        R.id.managerGuideConsultationContent);
                consultationContent.saveHierarchyState(hierarchyState);
                assertNull(hierarchyState.get(R.id.inputGuideConsultationGuardian));
                assertNull(hierarchyState.get(R.id.inputGuideConsultationFieldNote));
            });
        }

        try (ActivityScenario<ManagerGuidePreviewActivity> fresh = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CONSULTATION_SUPPORT"))) {
            fresh.onActivity(activity -> {
                TextInputEditText guardian = activity.findViewById(
                        R.id.inputGuideConsultationGuardian);
                TextInputEditText fieldNote = activity.findViewById(
                        R.id.inputGuideConsultationFieldNote);
                String freshGuardian = valueOf(guardian);
                String freshFieldNote = valueOf(fieldNote);

                activity.findViewById(R.id.managerGuideConsultationContent)
                        .restoreHierarchyState(hierarchyState);

                assertEquals(freshGuardian, valueOf(guardian));
                assertEquals(freshFieldNote, valueOf(fieldNote));
            });
        }
    }

    @Test
    public void unsavedBackDiscard_finishesPreviewActivity() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CONSULTATION_SUPPORT"))) {
            onView(withId(R.id.inputGuideConsultationGuardian))
                    .perform(scrollTo(), replaceText("저장하지 않고 버릴 입력"));
            closeSoftKeyboard();

            onView(withId(R.id.buttonBackGuideConsultation)).perform(click());
            onView(withText(R.string.guide_consultation_exit_title))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.guide_consultation_exit_discard)).perform(click());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    @Test
    public void recordingSurvivesRecreate_unsavedBackStays_thenSavedNotesAdvance() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CONSULTATION_SUPPORT"))) {
            assertConsultationScreenReady();

            onView(withId(R.id.buttonGuideConsultationRecordPreview)).perform(click());
            onView(withId(R.id.textGuideConsultationRecordStatus))
                    .check(matches(withText(R.string.guide_consultation_record_active)));
            onView(withId(R.id.inputGuideConsultationGuardian))
                    .perform(scrollTo(), replaceText("보호자 공유 진료 진행"));
            onView(withId(R.id.inputGuideConsultationFieldNote))
                    .perform(scrollTo(), replaceText("진료실 현장 메모"));
            closeSoftKeyboard();

            scenario.recreate();

            onView(withId(R.id.textGuideConsultationStep))
                    .check(matches(withText("STEP 06")));
            onView(withId(R.id.textGuideConsultationRecordStatus))
                    .check(matches(withText(R.string.guide_consultation_record_active)));
            onView(withId(R.id.inputGuideConsultationGuardian))
                    .check(matches(withText("보호자 공유 진료 진행")));
            onView(withId(R.id.inputGuideConsultationFieldNote))
                    .check(matches(withText("진료실 현장 메모")));
            onView(withId(R.id.buttonGuideConsultationRecordPreview)).perform(click());
            onView(withId(R.id.textGuideConsultationRecordStatus))
                    .check(matches(withText(R.string.guide_consultation_record_stopped)));

            onView(withId(R.id.buttonBackGuideConsultation)).perform(click());
            onView(withText(R.string.guide_consultation_exit_title))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.guide_consultation_exit_stay)).perform(click());
            onView(withId(R.id.textGuideConsultationStep))
                    .check(matches(withText("STEP 06")));
            onView(withId(R.id.inputGuideConsultationGuardian))
                    .check(matches(withText("보호자 공유 진료 진행")));

            onView(withId(R.id.buttonGuideConsultationSaveGuardian))
                    .perform(scrollTo(), click());

            onView(withId(R.id.buttonGuideConsultationSaveFieldNote))
                    .perform(scrollTo(), click());

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());

            onView(withId(R.id.guideDefaultToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.textGuideTitle))
                    .check(matches(withText("Step 7. 진료 요약")));
            onView(withId(R.id.managerGuideConsultationContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    private void assertConsultationScreenReady() {
        onView(withText(R.string.debug_manager_guide_preview_banner))
                .check(matches(isDisplayed()));
        onView(withId(R.id.textGuideConsultationStep))
                .check(matches(withText("STEP 06")));
        onView(withText(R.string.guide_consultation_record_preview_badge))
                .check(matches(isDisplayed()));
        onView(withId(R.id.textGuideConsultationRecordStatus))
                .check(matches(withText(R.string.guide_consultation_record_ready)));
    }

    private static String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }
}
