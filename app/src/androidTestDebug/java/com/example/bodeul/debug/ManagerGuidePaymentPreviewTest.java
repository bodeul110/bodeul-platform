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

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 서버 권한 없이 수납 증빙 전용 화면, 메모 보호, 다음 단계 전환을 검증한다. */
@RunWith(AndroidJUnit4.class)
public class ManagerGuidePaymentPreviewTest {

    @Test
    public void noAttachment_recreateAndAdvanceToPharmacyRoute() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "PAYMENT_EVIDENCE"))) {
            assertPaymentScreen();

            scenario.recreate();
            assertPaymentScreen();

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());

            onView(withId(R.id.guidePharmacyRouteToolbar)).check(matches(isDisplayed()));
            onView(withId(R.id.managerGuidePaymentContent))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    @Test
    public void unsavedNote_survivesReloadAndBlocksAdvanceUntilSaved() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(context, "PAYMENT_EVIDENCE"))) {
            onView(withId(R.id.inputGuidePaymentNote)).perform(
                    scrollTo(), replaceText("수납 창구에서 보호자에게 확인할 사항"));
            closeSoftKeyboard();

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.inputGuidePaymentNote)).check(matches(
                    withText("수납 창구에서 보호자에게 확인할 사항")));

            scenario.recreate();
            onView(withId(R.id.inputGuidePaymentNote)).check(matches(
                    withText("수납 창구에서 보호자에게 확인할 사항")));

            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guidePaymentToolbar)).check(matches(isDisplayed()));

            onView(withId(R.id.buttonGuidePaymentSaveNote)).perform(scrollTo(), click());
            onView(withId(R.id.buttonAdvanceGuide)).perform(click());
            onView(withId(R.id.guidePharmacyRouteToolbar)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void registeredEvidence_rendersReplaceStateAndCanBeCleared() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

        try (ActivityScenario<ManagerGuidePreviewActivity> scenario = ActivityScenario.launch(
                ManagerGuidePreviewActivity.createIntent(
                        context, "PAYMENT_EVIDENCE", true))) {
            onView(withId(R.id.textGuidePaymentStatus)).check(matches(
                    withText(R.string.guide_payment_status_registered)));
            onView(withId(R.id.textGuidePaymentFile)).check(matches(
                    withText("receipt.pdf · 2.0 MiB")));
            onView(withId(R.id.buttonGuidePaymentSelect)).check(matches(
                    withContentDescription(R.string.guide_payment_replace_description)));
            onView(withId(R.id.buttonGuidePaymentClear))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()))
                    .perform(click());

            onView(withId(R.id.textGuidePaymentStatus)).check(matches(
                    withText(R.string.guide_payment_status_empty)));
            onView(withId(R.id.buttonGuidePaymentSelect)).check(matches(
                    withContentDescription(R.string.guide_payment_upload_description)));
            onView(withId(R.id.buttonGuidePaymentClear))
                    .check(matches(withEffectiveVisibility(GONE)));
        }
    }

    private void assertPaymentScreen() {
        onView(withText(R.string.debug_manager_guide_preview_banner))
                .check(matches(isDisplayed()));
        onView(withId(R.id.guidePaymentToolbar)).check(matches(isDisplayed()));
        onView(withId(R.id.managerGuidePaymentContent)).check(matches(isDisplayed()));
        onView(withId(R.id.textGuidePaymentStep)).check(matches(withText("08단계")));
        onView(withId(R.id.textGuidePaymentStatus))
                .check(matches(withText(R.string.guide_payment_status_empty)));
        onView(withId(R.id.buttonGuidePaymentSelect)).check(matches(
                withContentDescription(R.string.guide_payment_upload_description)));
        onView(withId(R.id.buttonAdvanceGuide)).check(matches(isEnabled()));
        onView(withId(R.id.cardGuideNotesActions))
                .check(matches(withEffectiveVisibility(GONE)));
    }
}
