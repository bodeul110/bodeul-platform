package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.closeSoftKeyboard;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.ui.manager.ManagerGuideViewModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;

/** 저장소 미리보기로 표시 단계와 실제 저장 단계의 연결을 검증한다. 실제 서버 검증은 아니다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuideFinalFigmaTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private ActivityScenario<ManagerGuidePreviewActivity> launch(String code, boolean overview) {
        Intent intent = ManagerGuidePreviewActivity.createIntent(context(), code)
                .putExtra(ManagerGuidePreviewActivity.EXTRA_START_TIMELINE, overview);
        return ActivityScenario.launch(intent);
    }

    private void openCurrent() {
        onView(allOf(withId(R.id.buttonFinalGuideOpenStep), withEffectiveVisibility(Visibility.VISIBLE)))
                .perform(scrollTo(), click());
    }

    @Test public void timeline_hasNineGroups_andOnlyCurrentStepCanOpen() throws Exception {
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = launch("MEETING_CONFIRMATION", true)) {
            scenario.onActivity(activity -> {
                LinearLayout timeline = activity.findViewById(R.id.finalGuideTimeline);
                assertEquals(9, timeline.getChildCount());
                for (int i = 0; i < 9; i++) {
                    assertEquals(i == 0, timeline.getChildAt(i).findViewById(R.id.finalGuideTimelineHeader).isEnabled());
                }
                assertFalse(activity.findViewById(R.id.buttonFinalGuideFinish).isEnabled());
                assertTextFits(activity.findViewById(android.R.id.content));
            });
            capture("timeline_meeting");
            openCurrent();
            onView(withId(R.id.managerGuideMeetingOverview)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonFinalGuideBack)).perform(click());
            onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
        }
    }

    @Test public void consultationDraft_survivesTimelineAndRecreation_thenSaveKeepsGroupSix() {
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = launch("CONSULTATION_SUPPORT", true)) {
            openCurrent();
            onView(withId(R.id.inputGuideConsultationFieldNote))
                    .perform(scrollTo(), replaceText("진료 결과를 기록하고 다음 방문 일정을 확인했습니다."));
            closeSoftKeyboard();
            onView(withId(R.id.buttonFinalGuideBack)).perform(click());
            scenario.recreate();
            openCurrent();
            onView(withId(R.id.inputGuideConsultationFieldNote))
                    .check(matches(withText("진료 결과를 기록하고 다음 방문 일정을 확인했습니다.")));
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.finalGuideDetailScroll)).check(matches(isDisplayed()));
            closeSoftKeyboard();
            onView(withId(R.id.buttonGuideConsultationSaveFieldNote)).perform(scrollTo());
            onView(withId(R.id.buttonGuideConsultationSaveFieldNote))
                    .perform(ManagerGuideTestNavigation.clickAfterScrollSettles());
            scenario.onActivity(activity -> {
                ManagerGuideViewModel vm = new ViewModelProvider(activity).get(ManagerGuideViewModel.class);
                assertEquals("진료 결과를 기록하고 다음 방문 일정을 확인했습니다.",
                        vm.getUiState().getValue().screenModel.getFieldPhotoNote());
            });
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
            openCurrent();
            onView(withId(R.id.managerGuideConsultationSummaryContent)).check(matches(isDisplayed()));
            onView(withId(R.id.textFinalGuideToolbar)).check(matches(withText(R.string.final_guide_group_6)));
        }
    }

    @Test public void savingCannotBeRepeatedFromTimeline() {
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = launch("CONSULTATION_SUMMARY", false)) {
            scenario.onActivity(activity -> {
                ManagerGuideViewModel vm = new ViewModelProvider(activity).get(ManagerGuideViewModel.class);
                ((MutableLiveData<Boolean>) vm.getMutationInFlight()).setValue(true);
            });
            onView(withId(R.id.buttonAdvanceGuide)).check(matches(not(isEnabled())));
            onView(withId(R.id.buttonFinalGuideBack)).perform(click());
            onView(allOf(withId(R.id.buttonFinalGuideOpenStep), withEffectiveVisibility(Visibility.VISIBLE)))
                    .check(matches(not(isEnabled())));
            scenario.onActivity(activity -> {
                ManagerGuideViewModel vm = new ViewModelProvider(activity).get(ManagerGuideViewModel.class);
                ((MutableLiveData<Boolean>) vm.getMutationInFlight()).setValue(false);
            });
            openCurrent();
            onView(withId(R.id.buttonGuideSummarySaveNote)).check(matches(isEnabled()));
        }
    }

    @Test public void receptionUsesActualInput_andVitalsKeepsValidation() {
        try (ActivityScenario<ManagerGuidePreviewActivity> ignored = launch("RECEPTION_QUEUE", false)) {
            onView(withId(R.id.inputGuideReceptionQueue)).perform(scrollTo(), replaceText("A-12"));
            onView(withId(R.id.inputGuideReceptionWaitMinutes)).perform(scrollTo(), replaceText("15"));
            closeSoftKeyboard();
            onView(withId(R.id.buttonGuideReceptionShare)).perform(scrollTo(), click());
            onView(withId(R.id.textGuideReceptionLastShare)).check(matches(withText(org.hamcrest.Matchers.containsString("15"))));
        }
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = launch("VITALS_CHECK", false)) {
            onView(withId(R.id.inputGuideVitalsSystolic)).perform(scrollTo(), replaceText("0"));
            onView(withId(R.id.inputGuideVitalsDiastolic)).perform(scrollTo(), replaceText("80"));
            closeSoftKeyboard();
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            scenario.onActivity(activity -> assertNotNull(((TextView) activity.findViewById(R.id.inputGuideVitalsSystolic)).getError()));
            onView(withId(R.id.finalGuideDetailScroll)).check(matches(isDisplayed()));
        }
    }

    @Test public void preparationStillRequiresAllThreeChecks() {
        try (ActivityScenario<ManagerGuidePreviewActivity> ignored = launch("PRE_CONSULTATION", false)) {
            onView(withId(R.id.buttonGuidePreConsultationComplete)).check(matches(not(isEnabled())));
            for (int id : new int[]{R.id.checkGuidePreConsultationMedication, R.id.checkGuidePreConsultationRequest,
                    R.id.checkGuidePreConsultationDocuments}) onView(withId(id)).perform(scrollTo(), click());
            onView(withId(R.id.buttonGuidePreConsultationComplete)).check(matches(isEnabled()));
        }
    }

    @Test public void pharmacyTransition_staysInGroupEight_andRequiresConfirmation() {
        try (ActivityScenario<ManagerGuidePreviewActivity> ignored = launch("PHARMACY_ROUTE", true)) {
            openCurrent();
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withText(R.string.guide_route_confirmation_title)).check(matches(isDisplayed()));
            onView(withText(R.string.guide_action_route_confirmed)).perform(click());
            onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
            openCurrent();
            onView(withId(R.id.managerGuidePrescriptionContent)).check(matches(isDisplayed()));
            onView(withId(R.id.textFinalGuideToolbar)).check(matches(withText(R.string.final_guide_group_8)));
        }
    }

    @Test public void returnCompletion_requiresExistingReportSubmission() {
        try (ActivityScenario<ManagerGuidePreviewActivity> ignored = launch("CARE_COMPLETION", true)) {
            onView(withId(R.id.buttonFinalGuideFinish)).check(matches(not(isEnabled())));
            openCurrent();
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.finalGuideTimelineScroll)).check(matches(isDisplayed()));
            onView(withId(R.id.buttonFinalGuideFinish)).check(matches(isEnabled())).perform(click());
            onView(withId(R.id.managerGuideJournalContent)).check(matches(isDisplayed()));
            onView(withId(R.id.cardGuideReportActions)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        }
    }

    @Test public void legacySevenStepGuide_doesNotDisplayUnstoredStages() {
        Intent intent = ManagerGuidePreviewActivity.createLegacyIntent(context(), "LEGACY_CORE_PHARMACY")
                .putExtra(ManagerGuidePreviewActivity.EXTRA_START_TIMELINE, true);
        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertEquals(7,
                    ((LinearLayout) activity.findViewById(R.id.finalGuideTimeline)).getChildCount()));
            openCurrent();
            onView(withId(R.id.managerGuideMedicationContent)).check(matches(isDisplayed()));
        }
    }

    @Test public void everyStepRendersAndRecreates_withReadableText() throws Exception {
        String[] codes = {"MEETING_CONFIRMATION", "HOSPITAL_ROUTE", "RECEPTION_QUEUE", "VITALS_CHECK",
                "PRE_CONSULTATION", "CONSULTATION_SUPPORT", "CONSULTATION_SUMMARY", "PAYMENT_EVIDENCE",
                "PHARMACY_ROUTE", "PRESCRIPTION_DOCUMENTS", "MEDICATION_CONFIRMATION", "CARE_COMPLETION", "MANAGER_JOURNAL"};
        for (String code : codes) {
            try (ActivityScenario<ManagerGuidePreviewActivity> scenario = launch(code, false)) {
                onView(withId(R.id.finalGuideDetailScroll)).check(matches(isDisplayed()));
                scenario.recreate();
                onView(withId(R.id.finalGuideDetailScroll)).check(matches(isDisplayed()));
                scenario.onActivity(activity -> {
                    assertTextFits(activity.findViewById(android.R.id.content));
                    View content = activity.findViewById(R.id.finalGuideDetailScroll);
                    View footer = activity.findViewById(R.id.managerGuideBottomAction);
                    assertTrue("입력 영역과 하단 버튼이 겹칩니다.", content.getBottom() <= footer.getTop());
                    assertTrue("입력 영역을 스크롤할 공간이 없습니다.", content.getHeight() > 100);
                });
                capture(code.toLowerCase());
            }
        }
    }

    private void assertTextFits(View view) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView text && text.getLayout() != null && text.getWidth() > 0) {
            int width = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
            String name = view.getId() == View.NO_ID ? "이름 없음" : view.getResources().getResourceEntryName(view.getId());
            for (int line = 0; line < text.getLineCount(); line++) {
                assertTrue("글자 가로 넘침: " + name + " " + text.getText()
                        + " " + text.getLayout().getLineMax(line) + "/" + width,
                        text.getLayout().getLineMax(line) <= width + 2);
                assertEquals("글자 잘림: " + text.getText(), 0, text.getLayout().getEllipsisCount(line));
            }
            if (!(text instanceof EditText)) {
                int height = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
                assertTrue("글자 세로 넘침: " + text.getText(), text.getLayout().getHeight() <= height + 2);
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) assertTextFits(group.getChildAt(i));
        }
    }

    private void capture(String name) throws Exception {
        String prefix = InstrumentationRegistry.getArguments().getString("guideCapturePrefix");
        if (prefix == null) return;
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        File directory = new File(context().getExternalFilesDir(null), "final-guide-captures");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(new File(directory, prefix + "-" + name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        bitmap.recycle();
    }
}
