package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withSubstring;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 환자 진행현황 debug 미리보기가 실제 상태 화면을 단계별 로컬 데이터로 갱신하는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class PatientProgressFigmaPreviewTest {
    @Test
    public void nextStep_movesActualPatientStatusFromOneToTwo() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<PatientProgressFigmaPreviewActivity> ignored =
                     ActivityScenario.launch(PatientProgressFigmaPreviewActivity.createIntent(context))) {
            onView(withId(R.id.textPatientProgressPreviewState))
                    .check(matches(withText("1단계 · 환자 접촉")));
            onView(withId(R.id.buttonPatientProgressPreviewPrevious))
                    .check(matches(not(isEnabled())));
            assertProgressBody("1 / 7", "현재 안내: 환자 접촉");

            onView(withId(R.id.buttonPatientProgressPreviewNext)).perform(click());

            onView(withId(R.id.textPatientProgressPreviewState))
                    .check(matches(withText("2단계 · 간편 등록")));
            assertProgressBody("2 / 7", "현재 안내: 간편 등록");
        }
    }

    @Test
    public void stepControls_coverEveryProgressStateInBothDirections() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] labels = context.getResources().getStringArray(
                R.array.debug_patient_progress_preview_states);
        try (ActivityScenario<PatientProgressFigmaPreviewActivity> ignored =
                     ActivityScenario.launch(PatientProgressFigmaPreviewActivity.createIntent(context))) {
            for (int state = PatientProgressFigmaPreviewActivity.FIRST_STATE;
                 state <= PatientProgressFigmaPreviewActivity.COMPLETED_STATE;
                 state++) {
                onView(withId(R.id.textPatientProgressPreviewState))
                        .check(matches(withText(labels[state - 1])));
                if (state <= 7) {
                    assertProgressBody(state + " / 7", "현재 안내:");
                }
                if (state < PatientProgressFigmaPreviewActivity.COMPLETED_STATE) {
                    onView(withId(R.id.buttonPatientProgressPreviewNext)).perform(click());
                }
            }

            for (int state = PatientProgressFigmaPreviewActivity.COMPLETED_STATE;
                 state > PatientProgressFigmaPreviewActivity.FIRST_STATE;
                 state--) {
                onView(withId(R.id.buttonPatientProgressPreviewPrevious)).perform(click());
                onView(withId(R.id.textPatientProgressPreviewState))
                        .check(matches(withText(labels[state - 2])));
            }
            onView(withId(R.id.buttonPatientProgressPreviewPrevious))
                    .check(matches(not(isEnabled())));
        }
    }

    @Test
    public void lastProgressStep_showsCareEndedInsteadOfPayment() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<PatientProgressFigmaPreviewActivity> ignored =
                     ActivityScenario.launch(PatientProgressFigmaPreviewActivity.createIntent(
                             context,
                             7))) {
            assertProgressBody("7 / 7", "현재 안내: 환자 귀가(서비스 종료)");
            onView(withText(R.string.guardian_report_session_care_ended))
                    .perform(scrollTo())
                    .check(matches(withText(R.string.guardian_report_session_care_ended)));
        }
    }

    @Test
    public void completedState_showsFinalReportAndStopsAdvancing() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<PatientProgressFigmaPreviewActivity> ignored =
                     ActivityScenario.launch(PatientProgressFigmaPreviewActivity.createIntent(
                             context,
                             PatientProgressFigmaPreviewActivity.COMPLETED_STATE))) {
            onView(withId(R.id.textPatientProgressPreviewState))
                    .check(matches(withText(R.string.debug_patient_progress_preview_completed)));
            onView(withId(R.id.buttonPatientProgressPreviewNext))
                    .check(matches(not(isEnabled())));
            onView(withId(R.id.textBookingStatusProgressTitle))
                    .perform(scrollTo())
                    .check(matches(withText(R.string.booking_status_progress_title_completed)));
        }
    }

    private static void assertProgressBody(String stepText, String currentStepText) {
        onView(withId(R.id.textBookingStatusProgressBody))
                .perform(scrollTo())
                .check(matches(withSubstring(stepText)))
                .check(matches(withSubstring(currentStepText)));
    }
}
