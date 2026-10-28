package com.example.bodeul.debug;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingRepository;
import com.example.bodeul.data.GuardianBookingRepository.CreateFailure;
import com.example.bodeul.data.GuardianBookingRepository.CreateResult;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator;
import com.example.bodeul.ui.booking.GuardianBookingReviewBinder;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void rejectedCreate_showsRefreshAndSubmitsReloadedApproval() {
        try (ActivityScenario<GuardianBookingPreviewActivity> scenario = launch()) {
            FailureRepository repository = new FailureRepository(CreateFailure.REJECTED);
            GuardianBookingCoordinator coordinator = bindScenario(scenario, repository);
            try {
                onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(R.string.guardian_booking_create_rejected)));
                onView(withId(R.id.buttonGuardianBookingRetry)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.buttonGuardianBookingRefresh)).perform(scrollTo())
                        .check(matches(isEnabled())).perform(click());
                onView(withId(R.id.textGuardianBookingApproval)).perform(scrollTo())
                        .check(matches(withText(containsString("승인 버전: 2"))));
                onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(containsString("TEST-RECOVERY"))));
                scenario.onActivity(activity -> assertEquals(Arrays.asList(1L, 2L), repository.sentVersions));
            } finally {
                scenario.onActivity(activity -> coordinator.dispose());
            }
        }
    }

    @Test
    public void lostResponse_thenRejectedRetry_keepsSameRequestRecovery() {
        try (ActivityScenario<GuardianBookingPreviewActivity> scenario = launch()) {
            FailureRepository repository = new FailureRepository(CreateFailure.UNKNOWN, CreateFailure.REJECTED);
            GuardianBookingCoordinator coordinator = bindScenario(scenario, repository);
            try {
                onView(withId(R.id.buttonGuardianBookingCreate)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(R.string.guardian_booking_create_uncertain)));
                onView(withId(R.id.buttonGuardianBookingRefresh)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.buttonGuardianBookingRetry)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(R.string.guardian_booking_retry_rejected)));
                onView(withId(R.id.buttonGuardianBookingRefresh)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.buttonGuardianBookingRetry)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(containsString("TEST-RECOVERY"))));
                scenario.onActivity(activity -> {
                    assertEquals(1, repository.loads);
                    assertEquals(Arrays.asList(1L, 1L, 1L), repository.sentVersions);
                });
            } finally {
                scenario.onActivity(activity -> coordinator.dispose());
            }
        }
    }

    @Test
    public void revocationOnly_hidesUnverifiedDraftAndNeverEnablesNewApproval() {
        try (ActivityScenario<GuardianBookingPreviewActivity> scenario = launch()) {
            RevocationOnlyRepository repository = new RevocationOnlyRepository();
            GuardianBookingCoordinator coordinator = bindScenario(scenario, repository);
            try {
                onView(withId(R.id.textGuardianBookingSummary)).check(matches(withText("")));
                onView(withId(R.id.checkGuardianBookingReviewed)).perform(scrollTo()).check(matches(not(isEnabled())));
                onView(withId(R.id.checkGuardianBookingAdult)).perform(scrollTo()).check(matches(not(isEnabled())));
                onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
                onView(withId(R.id.buttonGuardianBookingCreate)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.buttonGuardianBookingRevoke)).perform(scrollTo()).check(matches(isEnabled())).perform(click());
                onView(withId(R.id.textGuardianBookingMessage)).perform(scrollTo())
                        .check(matches(withText(R.string.guardian_booking_revoked)));
                onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
                onView(withId(R.id.buttonGuardianBookingRevoke)).check(matches(not(isEnabled())));
                onView(withId(R.id.buttonGuardianBookingRefresh)).perform(scrollTo(), click());
                onView(withId(R.id.textGuardianBookingApproval)).perform(scrollTo())
                        .check(matches(withText(containsString("승인 버전: 2"))));
                onView(withId(R.id.buttonGuardianBookingGrant)).perform(scrollTo()).check(matches(not(isEnabled())));
                onView(withId(R.id.buttonGuardianBookingRevoke)).check(matches(not(isEnabled())));
                scenario.onActivity(activity -> assertEquals(1, repository.revocations));
            } finally {
                scenario.onActivity(activity -> coordinator.dispose());
            }
        }
    }

    private static GuardianBookingCoordinator bindScenario(
            ActivityScenario<GuardianBookingPreviewActivity> scenario, GuardianBookingRepository repository) {
        AtomicReference<GuardianBookingCoordinator> result = new AtomicReference<>();
        scenario.onActivity(activity -> {
            GuardianBookingRequest request = new GuardianBookingRequest(UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), BookingRequestDraft.builder().linkedParticipantName("합성 환자")
                            .hospitalName("합성 검증 병원").appointmentAt("2099-01-01 10:00")
                            .priceSummary(new BookingPriceSummary(40_000, 0, 0, 40_000)).build());
            GuardianBookingReviewBinder binder = new GuardianBookingReviewBinder(
                    activity.findViewById(R.id.guardianBookingReviewRoot));
            GuardianBookingCoordinator coordinator = new GuardianBookingCoordinator(
                    repository, request, Clock.systemUTC(), binder::render);
            binder.bindActions(coordinator);
            coordinator.refresh();
            result.set(coordinator);
        });
        return result.get();
    }

    private static final class RevocationOnlyRepository implements GuardianBookingRepository {
        final Instant now = Instant.now();
        GuardianBookingApproval approval = new GuardianBookingApproval(UUID.randomUUID(), 1,
                now.minusSeconds(1), now.plusSeconds(3600), null, true);
        int revocations;

        @Override
        public void load(GuardianBookingRequest request, RepositoryCallback<Review> callback) {
            callback.onSuccess(new Review(request, approval, true, false));
        }

        @Override
        public void revoke(GuardianBookingRequest request, long version,
                RepositoryCallback<GuardianBookingApproval> callback) {
            assertEquals(1, version);
            revocations++;
            approval = new GuardianBookingApproval(approval.getGrantId(), 2,
                    now.minusSeconds(1), now.plusSeconds(3600), now, false);
            callback.onSuccess(approval);
        }

        @Override
        public void grant(GuardianBookingRequest request, long version, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            throw new AssertionError("철회 전용 화면에서는 새 승인을 만들면 안 됩니다.");
        }

        @Override
        public void create(GuardianBookingRequest request, GuardianBookingApproval approval,
                RepositoryCallback<CreateResult> callback) {
            throw new AssertionError("철회 전용 화면에서는 예약을 접수하면 안 됩니다.");
        }
    }

    private static final class FailureRepository implements GuardianBookingRepository {
        final ArrayDeque<CreateFailure> failures;
        final List<Long> sentVersions = new ArrayList<>();
        int loads;

        FailureRepository(CreateFailure... failures) {
            this.failures = new ArrayDeque<>(Arrays.asList(failures));
        }

        @Override
        public void load(GuardianBookingRequest request, RepositoryCallback<Review> callback) {
            Instant now = Instant.now();
            callback.onSuccess(new Review(request, new GuardianBookingApproval(UUID.randomUUID(), ++loads,
                    now.minusSeconds(1), now.plusSeconds(3600), null, true), false));
        }

        @Override
        public void create(GuardianBookingRequest request, GuardianBookingApproval approval,
                RepositoryCallback<CreateResult> callback) {
            sentVersions.add(approval.getVersion());
            callback.onSuccess(failures.isEmpty()
                    ? CreateResult.accepted(new GuardianBookingReceipt(UUID.randomUUID(), "TEST-RECOVERY"))
                    : CreateResult.failed(failures.removeFirst()));
        }

        @Override
        public void grant(GuardianBookingRequest request, long version, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            throw new AssertionError("보호자 접수 검증에서는 승인 생성이 호출되면 안 됩니다.");
        }

        @Override
        public void revoke(GuardianBookingRequest request, long version,
                RepositoryCallback<GuardianBookingApproval> callback) {
            throw new AssertionError("보호자 접수 검증에서는 철회가 호출되면 안 됩니다.");
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
