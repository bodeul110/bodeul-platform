package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 원격 계정이나 DB를 사용하지 않는 합성 UI 검증이다. */
@RunWith(AndroidJUnit4.class)
public class GuardianBookingPreviewTest {
    @Test
    public void patientConfirmsFullBody_thenGuardianReceivesOnlyReceipt() {
        try (ActivityScenario<GuardianBookingPreviewActivity> ignored = launch()) {
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText(containsString("합성 복약 기록"))));
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText(containsString("40,000원"))));
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo(), click());
            onView(withId(R.id.buttonGuardianBookingGrant)).check(matches(not(isEnabled())));
            onView(withId(R.id.checkGuardianBookingAdult)).perform(scrollTo(), click());
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo(), click());
            onView(withId(R.id.checkGuardianBookingReviewed)).check(matches(not(isChecked())));
            onView(withId(R.id.buttonGuardianBookingPreviewGuardian)).perform(click());
            onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo(), click());
            onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                    .check(matches(withText(containsString("TEST-0001"))));
            onView(withId(R.id.layoutGuardianBookingDetails)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingCreate)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test
    public void revokePreventsGuardianSubmission_andDoesNotPromiseCancellation() {
        try (ActivityScenario<GuardianBookingPreviewActivity> ignored = launch()) {
            approve();
            onView(withId(R.id.buttonGuardianBookingRevoke)).perform(scrollTo(), click());
            onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                    .check(matches(withText(R.string.guardian_booking_revoked)));
            onView(withId(R.id.buttonGuardianBookingPreviewGuardian)).perform(click());
            onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }

    @Test
    public void recreationDiscardsConfirmationAndSyntheticGrant() {
        try (ActivityScenario<GuardianBookingPreviewActivity> scenario = launch()) {
            approve();
            scenario.recreate();
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withId(R.id.buttonGuardianBookingRevoke)).check(matches(not(isEnabled())));
            onView(withId(R.id.checkGuardianBookingReviewed)).check(matches(not(isChecked())));
        }
    }

    @Test
    public void closingAndReopeningNeverRestoresSyntheticApproval() {
        try (ActivityScenario<GuardianBookingPreviewActivity> ignored = launch()) { approve(); }
        try (ActivityScenario<GuardianBookingPreviewActivity> ignored = launch()) {
            onView(withId(R.id.buttonGuardianBookingPreviewGuardian)).perform(click());
            onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }

    private static void approve() {
        onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo(), click());
        onView(withId(R.id.checkGuardianBookingAdult)).perform(scrollTo(), click());
        onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo(), click());
    }

    private static ActivityScenario<GuardianBookingPreviewActivity> launch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, GuardianBookingPreviewActivity.class));
    }
}
