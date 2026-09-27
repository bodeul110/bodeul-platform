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
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.google.android.material.textfield.TextInputEditText;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 서버 권한 없이 Figma 기반 Step 7·9·12·13 전용 화면과 기존 계약 연결을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideRemainingStepsPreviewTest {

    @Test
    public void consultationSummary_savesExistingFieldNote_andAdvances() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CONSULTATION_SUMMARY"))) {
            onView(withId(R.id.guideConsultationSummaryToolbar))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.managerGuideConsultationSummaryContent))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.cardGuideNotesActions))
                    .check(matches(withEffectiveVisibility(GONE)));

            onView(withId(R.id.inputGuideSummaryNote))
                    .perform(scrollTo(), replaceText("검사 결과와 다음 방문 일정을 확인했습니다."));
            closeSoftKeyboard();
            onView(withId(R.id.buttonGuideSummarySaveNote)).perform(scrollTo(), click());

            scenario.recreate();
            onView(withId(R.id.inputGuideSummaryNote))
                    .check(matches(withText("검사 결과와 다음 방문 일정을 확인했습니다.")));
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guidePaymentToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuideConsultationSummaryContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void pharmacyRoute_keepsOnlyKakaoPharmacyAction_andConfirmsAdvance() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "PHARMACY_ROUTE"))) {
            onView(withId(R.id.guidePharmacyRouteToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuidePharmacyRouteContent)).check(matches(isDisplayed()));
            onView(withId(R.id.cardGuideMap)).perform(scrollTo()).check(matches(isDisplayed()));
            onView(allOf(withId(R.id.buttonGuideMapAction), isDisplayed()))
                    .check(matches(withText(R.string.guide_map_action_pharmacy_button)));

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withText(R.string.guide_route_confirmation_title))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.guide_action_route_confirmed)).perform(click());
            onView(withId(R.id.guidePrescriptionToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuidePharmacyRouteContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void careCompletion_showsMemoSummary_thenOpensJournalWith300CharacterLimit() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "CARE_COMPLETION"))) {
            onView(withId(R.id.guideCareCompletionToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuideCareCompletionContent)).check(matches(isDisplayed()));
            onView(withId(R.id.guideCompletionMemoContainer))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withId(R.id.cardGuideReportActions))
                    .check(matches(withEffectiveVisibility(GONE)));

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guideJournalToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuideJournalContent)).check(matches(isDisplayed()));
            onView(withId(R.id.cardGuideReportActions))
                    .perform(scrollTo()).check(matches(isDisplayed()));

            String overLimit = repeatedKoreanCharacter(320);
            onView(withId(R.id.inputReportSummary))
                    .perform(scrollTo(), replaceText(overLimit));
            scenario.onActivity(activity -> {
                TextInputEditText input = activity.findViewById(R.id.inputReportSummary);
                assertEquals(300, input.getText() == null ? 0 : input.getText().length());
            });
        }
    }

    private String repeatedKoreanCharacter(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append('가');
        }
        return builder.toString();
    }

    @Test
    public void journal_submitsThroughExistingReportAction() throws InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch destroyed = new CountDownLatch(1);

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "MANAGER_JOURNAL"))) {
            scenario.onActivity(activity -> activity.getLifecycle().addObserver(
                    (LifecycleEventObserver) (source, event) -> {
                        if (event == Lifecycle.Event.ON_DESTROY && activity.isFinishing()) {
                            destroyed.countDown();
                        }
                    }));
            onView(withId(R.id.inputReportSummary))
                    .perform(scrollTo(), replaceText("환자 인계까지 안전하게 마쳤습니다."));
            closeSoftKeyboard();
            onView(withId(R.id.radioMedicationComparisonMatched)).perform(scrollTo(), click());
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());

            assertTrue("리포트 제출 후 미리보기 화면이 종료되지 않았습니다.",
                    destroyed.await(5, TimeUnit.SECONDS));
        }
    }
}
