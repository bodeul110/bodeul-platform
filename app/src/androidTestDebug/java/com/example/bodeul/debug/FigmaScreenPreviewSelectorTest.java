package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 이번 화면 구현 범위가 debug APK에서 한곳에 노출되는지 확인한다. */
@RunWith(AndroidJUnit4.class)
public class FigmaScreenPreviewSelectorTest {
    @Test
    public void selector_listsAllImplementedTargets() {
        try (ActivityScenario<FigmaScreenPreviewSelectorActivity> ignored =
                     ActivityScenario.launch(FigmaScreenPreviewSelectorActivity.class)) {
            assertTitleAt(0, "매니저 7단계 · 진료 요약");
            assertTitleAt(1, "매니저 9단계 · 약국 이동");
            assertTitleAt(2, "매니저 12단계 · 동행 종료");
            assertTitleAt(3, "매니저 13단계 · 매니저 일지");
            assertTitleAt(4, "환자 예약 메인");
            assertTitleAt(5, "보호자 최종 리포트");
        }
    }

    @Test
    public void selector_routesEveryRowToItsExpectedPreview() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertManagerDestination(context, 0, "CONSULTATION_SUMMARY");
        assertManagerDestination(context, 1, "PHARMACY_ROUTE");
        assertManagerDestination(context, 2, "CARE_COMPLETION");
        assertManagerDestination(context, 3, "MANAGER_JOURNAL");
        assertDestination(context, 4, BookingFigmaPreviewActivity.class);
        assertDestination(context, 5, GuardianReportFigmaPreviewActivity.class);
        assertDestination(context, 6, ManagerGuidePreviewSelectorActivity.class);
    }

    private void assertTitleAt(int position, String expectedTitle) {
        onData(anything())
                .inAdapterView(withId(R.id.listFigmaScreenPreviews))
                .atPosition(position)
                .onChildView(withId(R.id.textManagerGuidePreviewStepTitle))
                .check(matches(withText(expectedTitle)));
    }

    private void assertManagerDestination(Context context, int position, String stepCode) {
        Intent intent = FigmaScreenPreviewSelectorActivity.destinationIntent(context, position);
        assertEquals(ManagerGuidePreviewActivity.class.getName(),
                intent.getComponent().getClassName());
        assertEquals(stepCode, intent.getStringExtra(ManagerGuidePreviewActivity.EXTRA_STEP_CODE));
    }

    private void assertDestination(Context context, int position, Class<?> expectedActivity) {
        Intent intent = FigmaScreenPreviewSelectorActivity.destinationIntent(context, position);
        assertEquals(expectedActivity.getName(), intent.getComponent().getClassName());
    }
}
