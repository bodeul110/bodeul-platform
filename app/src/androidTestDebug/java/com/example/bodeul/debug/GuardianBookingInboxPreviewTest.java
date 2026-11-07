package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.ui.booking.GuardianBookingInboxActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/** 실제 계정·서버를 사용하지 않는 수신함 UI 검증이다. */
@RunWith(AndroidJUnit4.class)
public class GuardianBookingInboxPreviewTest {
    @Test public void realEntryIsDisabledAndNotExported() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse(context.getResources().getBoolean(R.bool.bodeul_guardian_booking_inbox_enabled));
        assertFalse(context.getPackageManager().getActivityInfo(
                new ComponentName(context, GuardianBookingInboxActivity.class), 0).exported);
    }

    @Test public void listLoadsMaskedParticipantsAndFullBody_thenGrantsAndRevokes() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> ignored = launch("patient")) {
            openFirst();
            onView(withId(R.id.textGuardianBookingParticipants)).check(matches(withText(containsString("합성 보호자 (***-****-1234)"))));
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText(containsString("합성 복약 기록"))));
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText(containsString("40,000원"))));
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
            onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo(), click());
            onView(withId(R.id.buttonGuardianBookingGrant)).check(matches(not(isEnabled())));
            onView(withId(R.id.checkGuardianBookingAdult)).perform(scrollTo(), click());
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo(), click());
            onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo()).check(matches(withText(R.string.guardian_booking_granted)));
            onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo()).check(matches(not(isChecked())));
            onView(withId(R.id.buttonGuardianBookingRevoke)).perform(scrollTo(), click());
            onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo()).check(matches(withText(R.string.guardian_booking_revoked)));
            onView(withId(R.id.buttonGuardianBookingCreate)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void guardianHasNoPatientMutationOrCreateControls() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> ignored = launch("guardian")) {
            openFirst();
            onView(withId(R.id.checkGuardianBookingAdult)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingGrant)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingRevoke)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingCreate)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.textGuardianBookingParticipants)).check(matches(withText(containsString("합성 보호자"))));
        }
    }

    @Test public void explicitRevocationModeHidesBodyAndOnlyRevokes() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> ignored = launch("revoke-only")) {
            openFirst();
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText("")));
            onView(withId(R.id.textGuardianBookingParticipants)).check(matches(withText("")));
            onView(withId(R.id.buttonGuardianBookingGrant)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingRevoke)).perform(scrollTo(), click());
            onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo()).check(matches(withText(R.string.guardian_booking_revoked)));
            onView(withId(R.id.buttonGuardianBookingRevoke)).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }

    @Test public void emptyListHasNoStaleDetail() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> scenario = launch("empty")) {
            onView(withId(R.id.textGuardianBookingInboxMessage)).check(matches(withText(R.string.guardian_booking_inbox_empty)));
            scenario.onActivity(activity -> assertEquals(0, ((LinearLayout) activity.findViewById(R.id.layoutGuardianBookingInboxRows)).getChildCount()));
            onView(withId(R.id.guardianBookingReviewRoot)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void failedListIsErrorNotEmpty() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> ignored = launch("error")) {
            onView(withId(R.id.textGuardianBookingInboxMessage)).check(matches(withText(R.string.guardian_booking_load_failed)));
            onView(withId(R.id.buttonGuardianBookingGrant)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            onView(withId(R.id.buttonGuardianBookingInboxReload)).check(matches(isEnabled()));
        }
    }

    @Test public void detailFailureShowsNoBodyOrRevocation() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> ignored = launch("detail-error")) {
            openFirst();
            onView(withId(R.id.textGuardianBookingMessage)).check(matches(withText(R.string.guardian_booking_load_failed)));
            onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText("")));
            onView(withId(R.id.buttonGuardianBookingRevoke)).check(matches(withEffectiveVisibility(Visibility.GONE)));
        }
    }

    @Test public void recreationDropsDetailAndConfirmation_thenReloadsServerList() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> scenario = launch("patient")) {
            openFirst();
            onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo(), click());
            onView(withId(R.id.checkGuardianBookingAdult)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.guardianBookingReviewRoot)).check(matches(withEffectiveVisibility(Visibility.GONE)));
            openFirst();
            onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo()).check(matches(not(isChecked())));
            onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
        }
    }

    @Test public void backgroundClearsTextAndResumeStartsAtList() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> scenario = launch("patient")) {
            openFirst();
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> {
                assertEquals("", ((TextView) activity.findViewById(R.id.textGuardianBookingSummary)).getText().toString());
                assertEquals("", ((TextView) activity.findViewById(R.id.textGuardianBookingParticipants)).getText().toString());
                assertEquals(View.GONE, activity.findViewById(R.id.guardianBookingReviewRoot).getVisibility());
            });
            scenario.moveToState(Lifecycle.State.RESUMED);
            onView(withId(R.id.textGuardianBookingInboxRowTitle)).check(matches(isDisplayed()));
        }
    }

    @Test public void accountChangeFinishesScreen() {
        try (ActivityScenario<GuardianBookingInboxPreviewActivity> scenario = launch("patient")) {
            openFirst();
            scenario.onActivity(activity -> {
                activity.simulateAccountChange();
                assertTrue(activity.isFinishing());
                assertEquals("", ((TextView) activity.findViewById(R.id.textGuardianBookingSummary)).getText().toString());
            });
        }
    }

    private static void openFirst() { onView(withId(R.id.textGuardianBookingInboxRowTitle)).perform(click()); }
    private static ActivityScenario<GuardianBookingInboxPreviewActivity> launch(String scenario) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, GuardianBookingInboxPreviewActivity.class).putExtra("scenario", scenario));
    }
}
