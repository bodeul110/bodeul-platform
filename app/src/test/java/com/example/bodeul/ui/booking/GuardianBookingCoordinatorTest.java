package com.example.bodeul.ui.booking;

import static com.example.bodeul.GuardianBookingTestData.*;
import static org.junit.Assert.*;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator.Phase;

import org.junit.Test;

import java.time.Clock;
import java.time.ZoneOffset;

public class GuardianBookingCoordinatorTest {
    private final FakeRepository repository = new FakeRepository();
    private final GuardianBookingCoordinator coordinator = new GuardianBookingCoordinator(
            repository, request(), Clock.fixed(NOW, ZoneOffset.UTC), value -> {});

    @Test
    public void patient_mustLoadAndExplicitlyConfirmBothChecks() {
        coordinator.grant(true, true);
        assertEquals(0, repository.grants);
        load(true, inactive());
        coordinator.grant(false, true);
        coordinator.grant(true, false);
        assertEquals(0, repository.grants);
        assertEquals(R.string.guardian_booking_confirmation_required, coordinator.state().message);
        coordinator.grant(true, true);
        coordinator.grant(true, true);
        assertEquals(1, repository.grants);
        assertEquals(0, repository.version);
        assertEquals(Phase.SAVING, coordinator.state().phase);
        repository.approvalCallback.onSuccess(active(1));
        assertEquals(Phase.READY, coordinator.state().phase);
        assertTrue(coordinator.state().canRevoke);
        assertFalse(coordinator.state().canCreate);
    }

    @Test
    public void guardian_cannotGrantOrRevokeAndPatientCannotCreate() {
        load(false, active(1));
        coordinator.grant(true, true);
        coordinator.revoke();
        assertEquals(0, repository.grants);
        assertEquals(0, repository.revokes);
        assertTrue(coordinator.state().canCreate);
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), active(1), true));
        coordinator.create();
        assertEquals(0, repository.creates);
    }

    @Test
    public void duplicateRefreshAndMutationWhileLoading_areIgnored() {
        coordinator.refresh();
        coordinator.refresh();
        coordinator.grant(true, true);
        coordinator.revoke();
        coordinator.create();
        assertEquals(1, repository.loads);
        assertEquals(0, repository.grants + repository.revokes + repository.creates);
    }

    @Test
    public void failedLoad_isNotAnUnapprovedWritableState() {
        coordinator.refresh();
        repository.loadCallback.onError("서버 연결 실패");
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertNull(coordinator.state().review);
        coordinator.grant(true, true);
        coordinator.create();
        assertEquals(0, repository.grants + repository.creates);
    }

    @Test
    public void invalidPreview_allowsOnlyExistingApprovalRevocation() {
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), active(1), true, false));
        assertFalse(coordinator.state().canGrant);
        assertTrue(coordinator.state().canRevoke);
        coordinator.grant(true, true);
        assertEquals(0, repository.grants);
        coordinator.revoke();
        assertEquals(1, repository.revokes);
        repository.approvalCallback.onSuccess(new GuardianBookingApproval(GRANT, 2,
                NOW.minusSeconds(1), NOW.plusSeconds(3600), NOW, false));
        assertFalse(coordinator.state().review.previewVerified);
        assertFalse(coordinator.state().canGrant);
        assertFalse(coordinator.state().canRevoke);
        coordinator.grant(true, true);
        assertEquals(0, repository.grants);
    }

    @Test
    public void patientGrant_usesNormalizedBodyFromPreviewNotOriginal() {
        coordinator.refresh();
        GuardianBookingRequest normalized = request().withNormalizedDraft(request().getDraft()
                .toBuilder().meetingPlace("정규화된 장소").build());
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(normalized, inactive(), true));
        coordinator.grant(true, true);
        assertSame(normalized, repository.mutationRequest);
    }

    @Test
    public void changedRequestIdentity_isRejectedBeforeRendering() {
        coordinator.refresh();
        GuardianBookingRequest unrelated = new GuardianBookingRequest(PATIENT, GUARDIAN, GRANT, request().getDraft());
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(unrelated, inactive(), true));
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertNull(coordinator.state().review);
    }

    @Test
    public void grantFailure_requiresReloadBeforeAnotherApproval() {
        load(true, inactive());
        coordinator.grant(true, true);
        repository.approvalCallback.onError("409");
        coordinator.grant(true, true);
        assertEquals(1, repository.grants);
        assertNull(coordinator.state().review);
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), active(2), true));
        coordinator.grant(true, true);
        assertEquals(2, repository.grants);
        assertEquals(2, repository.version);
    }

    @Test
    public void mutationResponses_withWrongVersionOrKindRequireReload() {
        load(true, inactive());
        coordinator.grant(true, true);
        repository.approvalCallback.onSuccess(active(4));
        assertEquals(Phase.ERROR, coordinator.state().phase);
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), active(4), true));
        coordinator.revoke();
        repository.approvalCallback.onSuccess(active(5));
        assertEquals(Phase.ERROR, coordinator.state().phase);
    }

    @Test
    public void revokedAndExpiredApproval_cannotCreate() {
        load(false, new GuardianBookingApproval(GRANT, 2, NOW.minusSeconds(10),
                NOW.plusSeconds(30), NOW.minusSeconds(1), false));
        coordinator.create();
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(),
                new GuardianBookingApproval(GRANT, 3, NOW.minusSeconds(10), NOW, null, true), false));
        coordinator.create();
        assertFalse(coordinator.state().canCreate);
        assertEquals(0, repository.creates);
    }

    @Test
    public void revoke_usesVersionAndDoesNotSubmitOrCancelReservation() {
        load(true, active(7));
        coordinator.revoke();
        coordinator.revoke();
        assertEquals(1, repository.revokes);
        assertEquals(7, repository.version);
        repository.approvalCallback.onSuccess(new GuardianBookingApproval(GRANT, 8,
                NOW.minusSeconds(1), NOW.plusSeconds(3600), NOW, false));
        assertFalse(coordinator.state().canRevoke);
        assertEquals(0, repository.creates);
    }

    @Test
    public void uncertainCreate_reusesSameRequestAndGrantWithoutReloadOrNewId() {
        GuardianBookingApproval approved = active(3);
        load(false, approved);
        coordinator.create();
        GuardianBookingRequest original = repository.mutationRequest;
        coordinator.create();
        assertEquals(1, repository.creates);
        repository.receiptCallback.onError("접수 응답 없음");
        assertEquals(Phase.UNCERTAIN, coordinator.state().phase);
        assertNull(coordinator.state().review);
        coordinator.refresh();
        assertEquals(1, repository.loads);
        coordinator.retryCreate();
        assertEquals(2, repository.creates);
        assertSame(original, repository.mutationRequest);
        assertSame(approved, repository.sentApproval);
        repository.receiptCallback.onSuccess(new GuardianBookingReceipt(REQUEST, "TEST-1"));
        assertEquals(Phase.SUBMITTED, coordinator.state().phase);
        assertEquals("TEST-1", coordinator.state().receipt.getPublicCode());
        assertNull(coordinator.state().review);
        coordinator.create();
        coordinator.retryCreate();
        coordinator.refresh();
        assertEquals(2, repository.creates);
        assertEquals(1, repository.loads);
    }

    @Test
    public void lateAndDuplicateCallbacks_doNotRestoreClosedOrSupersededState() {
        load(true, inactive());
        RepositoryCallback<GuardianBookingRepository.Review> old = repository.loadCallback;
        coordinator.refresh();
        old.onError("늦은 오류");
        assertEquals(Phase.LOADING, coordinator.state().phase);
        coordinator.dispose();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), active(4), true));
        assertEquals(Phase.CLOSED, coordinator.state().phase);
        assertNull(coordinator.state().review);
    }

    @Test
    public void closingDuringCreate_discardsReceiptCallback() {
        load(false, active(1));
        coordinator.create();
        coordinator.dispose();
        repository.receiptCallback.onSuccess(new GuardianBookingReceipt(REQUEST, "TEST-1"));
        assertEquals(Phase.CLOSED, coordinator.state().phase);
        assertNull(coordinator.state().receipt);
    }

    private void load(boolean patient, GuardianBookingApproval approval) {
        coordinator.refresh();
        repository.loadCallback.onSuccess(new GuardianBookingRepository.Review(request(), approval, patient));
    }

    private static final class FakeRepository implements GuardianBookingRepository {
        int loads, grants, revokes, creates;
        long version;
        GuardianBookingRequest mutationRequest;
        GuardianBookingApproval sentApproval;
        RepositoryCallback<Review> loadCallback;
        RepositoryCallback<GuardianBookingApproval> approvalCallback;
        RepositoryCallback<GuardianBookingReceipt> receiptCallback;

        @Override
        public void load(GuardianBookingRequest request, RepositoryCallback<Review> callback) {
            loads++;
            loadCallback = callback;
        }

        @Override
        public void grant(GuardianBookingRequest request, long expected, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            assertTrue(confirmed);
            grants++;
            mutationRequest = request;
            version = expected;
            approvalCallback = callback;
        }

        @Override
        public void revoke(GuardianBookingRequest request, long expected, RepositoryCallback<GuardianBookingApproval> callback) {
            revokes++;
            mutationRequest = request;
            version = expected;
            approvalCallback = callback;
        }

        @Override
        public void create(GuardianBookingRequest request, GuardianBookingApproval approval,
                RepositoryCallback<GuardianBookingReceipt> callback) {
            creates++;
            mutationRequest = request;
            sentApproval = approval;
            receiptCallback = callback;
        }
    }
}
