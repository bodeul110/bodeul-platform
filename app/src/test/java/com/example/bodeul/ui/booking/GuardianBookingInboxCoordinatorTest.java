package com.example.bodeul.ui.booking;

import static com.example.bodeul.GuardianBookingTestData.*;
import static org.junit.Assert.*;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.GuardianBookingRepository.CreateResult;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Participant;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;
import com.example.bodeul.ui.booking.GuardianBookingInboxCoordinator.Phase;

import org.junit.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class GuardianBookingInboxCoordinatorTest {
    private final FakeRepository repository = new FakeRepository();
    private final AtomicReference<String> account = new AtomicReference<>("patient-session");
    private final List<GuardianBookingInboxCoordinator.State> published = new ArrayList<>();
    private final GuardianBookingInboxCoordinator coordinator = create();

    private GuardianBookingInboxCoordinator create() {
        return new GuardianBookingInboxCoordinator(repository, account::get,
                Clock.fixed(NOW, ZoneOffset.UTC), published::add);
    }

    @Test public void listOnlyContainsMetadata_andDetailNeedsBothReads() {
        coordinator.refreshList();
        assertEquals(Phase.LIST, coordinator.state().phase);
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        repository.deferDetail = true;
        coordinator.select(repository.reference);
        assertEquals(Phase.LOADING_DETAIL, coordinator.state().phase);
        assertNull(coordinator.state().approval);
        assertFalse(coordinator.state().canGrant);
        repository.pendingDetail.onSuccess(repository.detail);
        assertEquals(Phase.DETAIL, coordinator.state().phase);
        assertEquals("합성 보호자", coordinator.state().detail.getGuardian().getName());
        assertEquals("***-****-1234", coordinator.state().detail.getGuardian().getMaskedPhone());
        assertTrue(coordinator.state().canGrant);
    }

    @Test public void guardianCanReadButCannotGrantOrRevoke() {
        repository.patient = false;
        repository.approval = active(1);
        open();
        assertNotNull(coordinator.state().detail);
        coordinator.grant(true, true);
        coordinator.revoke();
        assertEquals(0, repository.grants);
        assertEquals(0, repository.revocations);
        assertFalse(coordinator.state().canGrant);
        assertFalse(coordinator.state().canRevoke);
    }

    @Test public void unknownReferenceCannotBeOpened() {
        coordinator.refreshList();
        coordinator.select(new GuardianBookingDraftReference(PATIENT, GUARDIAN, UUID.randomUUID()));
        assertEquals(0, repository.approvalLoads);
        assertEquals(Phase.LIST, coordinator.state().phase);
    }

    @Test public void emptyListIsNotAnError() {
        repository.rows = List.of();
        coordinator.refreshList();
        assertEquals(Phase.LIST, coordinator.state().phase);
        assertEquals(R.string.guardian_booking_inbox_empty, coordinator.state().message);
    }

    @Test public void listErrorIsNeverShownAsEmptySuccess() {
        repository.listFailure = true;
        coordinator.refreshList();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertEquals(R.string.guardian_booking_load_failed, coordinator.state().message);
        assertTrue(coordinator.state().rows.isEmpty());
    }

    @Test public void duplicateAndOversizedListsAreRejected() {
        repository.rows = List.of(repository.summary, repository.summary);
        coordinator.refreshList();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        repository.rows = java.util.Collections.nCopies(51, repository.summary);
        coordinator.refreshList();
        assertEquals(Phase.ERROR, coordinator.state().phase);
    }

    @Test public void approvalFailureNeverLoadsOrShowsBody() {
        repository.approvalFailure = true;
        open();
        assertEquals(0, repository.detailLoads);
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        assertEquals(Phase.ERROR, coordinator.state().phase);
    }

    @Test public void detailFailureNeverBecomesRevocationMode() {
        repository.approval = active(1);
        repository.detailFailure = true;
        open();
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        assertFalse(coordinator.state().canRevoke);
        assertEquals(Phase.ERROR, coordinator.state().phase);
    }

    @Test public void explicitRevocationOnlyDoesNotLoadDraftOrAllowGrant() {
        repository.revocationOnly = true;
        repository.approval = active(1);
        open();
        assertEquals(0, repository.detailLoads);
        assertNull(coordinator.state().detail);
        assertFalse(coordinator.state().canGrant);
        assertTrue(coordinator.state().canRevoke);
        coordinator.grant(true, true);
        coordinator.revoke();
        assertEquals(0, repository.grants);
        assertEquals(1, repository.revocations);
        assertTrue(coordinator.state().approval.revocationOnly);
        assertFalse(coordinator.state().canGrant);
        assertFalse(coordinator.state().canRevoke);
    }

    @Test public void nullAndMismatchedDetailsAreRejected() {
        repository.detail = null;
        open();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        GuardianBookingRequest other = new GuardianBookingRequest(PATIENT, GUARDIAN, UUID.randomUUID(), request().getDraft());
        repository.detail = new GuardianBookingStoredDraft(new Summary(GuardianBookingDraftReference.from(other),
                NOW, NOW.plusSeconds(3600)), other, new Participant("다른 환자", "****"), new Participant("다른 보호자", "****"));
        coordinator.refreshDetail();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void expiredDraftCannotBeReviewedForNewGrant() {
        repository.detail = new GuardianBookingStoredDraft(new Summary(repository.reference,
                NOW.minusSeconds(100), NOW), request(), new Participant("합성 환자", "****"),
                new Participant("합성 보호자", "****"));
        open();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertFalse(coordinator.state().canGrant);
    }

    @Test public void bothConfirmationsAreRequired() {
        open();
        coordinator.grant(false, true);
        coordinator.grant(true, false);
        assertEquals(0, repository.grants);
        assertEquals(R.string.guardian_booking_confirmation_required, coordinator.state().message);
        coordinator.grant(true, true);
        assertEquals(1, repository.grants);
        assertEquals(1, coordinator.state().approval.approval.getVersion());
        assertEquals(R.string.guardian_booking_granted, coordinator.state().message);
    }

    @Test public void savingBlocksDuplicateGrantAndListNavigation() {
        repository.deferGrant = true;
        open();
        coordinator.grant(true, true);
        coordinator.grant(true, true);
        coordinator.refreshList();
        coordinator.refreshDetail();
        assertEquals(1, repository.grants);
        assertEquals(Phase.SAVING, coordinator.state().phase);
        repository.pendingGrant.onSuccess(active(1));
        assertEquals(Phase.DETAIL, coordinator.state().phase);
    }

    @Test public void unknownGrantResultClearsBodyAndRequiresReload() {
        repository.deferGrant = true;
        open();
        coordinator.grant(true, true);
        repository.pendingGrant.onError("공개하면 안 되는 서버 오류");
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertEquals(R.string.guardian_booking_mutation_failed, coordinator.state().message);
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        coordinator.grant(true, true);
        assertEquals(1, repository.grants);
        repository.approval = active(1);
        coordinator.refreshDetail();
        assertEquals(1, coordinator.state().approval.approval.getVersion());
    }

    @Test public void staleGrantVersionFailsClosed() {
        repository.deferGrant = true;
        open();
        coordinator.grant(true, true);
        repository.pendingGrant.onSuccess(active(3));
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void revokeUsesLatestVersionAndOnlyUpdatesApproval() {
        repository.approval = active(2);
        open();
        coordinator.revoke();
        assertEquals(2, repository.sentVersion);
        assertEquals(3, coordinator.state().approval.approval.getVersion());
        assertNotNull(coordinator.state().detail);
        assertEquals(R.string.guardian_booking_revoked, coordinator.state().message);
    }

    @Test public void invalidRevocationClearsPreviousBody() {
        repository.approval = active(1);
        repository.invalidRevoke = true;
        open();
        coordinator.revoke();
        assertEquals(Phase.ERROR, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void refreshClearsBodyBeforeNetworkReturns() {
        open();
        repository.deferDetail = true;
        coordinator.refreshDetail();
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        assertFalse(coordinator.state().canGrant);
    }

    @Test public void listNavigationDiscardsLateDetail() {
        repository.deferDetail = true;
        open();
        RepositoryCallback<GuardianBookingStoredDraft> old = repository.pendingDetail;
        coordinator.refreshList();
        old.onSuccess(repository.detail);
        assertEquals(Phase.LIST, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void duplicateCallbackCannotReplaceSuccessfulDetail() {
        repository.deferDetail = true;
        open();
        repository.pendingDetail.onSuccess(repository.detail);
        repository.pendingDetail.onError("늦은 오류");
        assertEquals(Phase.DETAIL, coordinator.state().phase);
        assertNotNull(coordinator.state().detail);
    }

    @Test public void accountChangeImmediatelyClearsContentAndNeverReopensOldScreen() {
        open();
        account.set("other-session");
        coordinator.accountChanged();
        assertEquals(Phase.CLOSED, coordinator.state().phase);
        assertNull(coordinator.state().detail);
        assertNull(coordinator.state().approval);
        assertEquals(R.string.guardian_booking_account_changed, coordinator.state().message);
        account.set("patient-session");
        coordinator.refreshList();
        assertEquals(Phase.CLOSED, coordinator.state().phase);
    }

    @Test public void accountIsRecheckedBeforeMutationWithoutListenerNotification() {
        open();
        account.set(null);
        coordinator.grant(true, true);
        assertEquals(0, repository.grants);
        assertEquals(Phase.CLOSED, coordinator.state().phase);
    }

    @Test public void lateCallbackAfterAccountChangeIsDiscarded() {
        repository.deferDetail = true;
        open();
        account.set("other-session");
        repository.pendingDetail.onSuccess(repository.detail);
        assertEquals(Phase.CLOSED, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void lateMutationAfterDisposeCannotRestoreContent() {
        repository.deferGrant = true;
        open();
        coordinator.grant(true, true);
        coordinator.dispose();
        int events = published.size();
        repository.pendingGrant.onSuccess(active(1));
        assertEquals(events, published.size());
        assertEquals(Phase.CLOSED, coordinator.state().phase);
        assertNull(coordinator.state().detail);
    }

    @Test public void newInstanceStartsFromServerListNotRetainedApproval() {
        open();
        coordinator.grant(true, true);
        coordinator.dispose();
        GuardianBookingInboxCoordinator recreated = create();
        assertNull(recreated.state().detail);
        recreated.refreshList();
        assertEquals(Phase.LIST, recreated.state().phase);
        assertNull(recreated.state().approval);
        recreated.select(repository.reference);
        assertEquals(1, recreated.state().approval.approval.getVersion());
    }

    @Test public void loggedOutScreenNeverCallsRepository() {
        account.set(null);
        create().refreshList();
        assertEquals(0, repository.listLoads);
    }

    private void open() {
        coordinator.refreshList();
        coordinator.select(repository.reference);
    }

    private static final class FakeRepository implements GuardianBookingDraftRepository {
        final GuardianBookingDraftReference reference = GuardianBookingDraftReference.from(request());
        final Summary summary = new Summary(reference, NOW, NOW.plusSeconds(3600));
        List<Summary> rows = List.of(summary);
        GuardianBookingStoredDraft detail = new GuardianBookingStoredDraft(summary, request(),
                new Participant("합성 환자", "***-****-0000"), new Participant("합성 보호자", "***-****-1234"));
        GuardianBookingApproval approval = inactive();
        boolean patient = true;
        boolean revocationOnly, listFailure, approvalFailure, detailFailure, deferDetail, deferGrant, invalidRevoke;
        int listLoads, detailLoads, approvalLoads, grants, revocations;
        long sentVersion;
        RepositoryCallback<GuardianBookingStoredDraft> pendingDetail;
        RepositoryCallback<GuardianBookingApproval> pendingGrant;

        @Override public void listDrafts(RepositoryCallback<List<Summary>> callback) {
            listLoads++;
            if (listFailure) callback.onError("목록 실패"); else callback.onSuccess(rows);
        }
        @Override public void loadApproval(GuardianBookingDraftReference ref, RepositoryCallback<ApprovalStatus> callback) {
            assertEquals(reference, ref);
            approvalLoads++;
            if (approvalFailure) callback.onError("승인 조회 실패");
            else callback.onSuccess(new ApprovalStatus(approval, patient, revocationOnly));
        }
        @Override public void loadDraft(GuardianBookingDraftReference ref, RepositoryCallback<GuardianBookingStoredDraft> callback) {
            assertEquals(reference, ref);
            detailLoads++;
            pendingDetail = callback;
            if (deferDetail) return;
            if (detailFailure) callback.onError("원문 조회 실패"); else callback.onSuccess(detail);
        }
        @Override public void grantDraft(GuardianBookingDraftReference ref, long version, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            assertEquals(reference, ref);
            assertTrue(confirmed);
            grants++;
            sentVersion = version;
            pendingGrant = callback;
            if (deferGrant) return;
            approval = active(version + 1);
            callback.onSuccess(approval);
        }
        @Override public void revokeDraft(GuardianBookingDraftReference ref, long version,
                RepositoryCallback<GuardianBookingApproval> callback) {
            assertEquals(reference, ref);
            revocations++;
            sentVersion = version;
            if (!invalidRevoke) approval = new GuardianBookingApproval(approval.getGrantId(), version + 1,
                    approval.getGrantedAt(), approval.getExpiresAt(), NOW, false);
            callback.onSuccess(approval);
        }
        @Override public void submitDraft(GuardianBookingRequest request, RepositoryCallback<Summary> callback) {
            throw new AssertionError("수신함에서 원문을 새로 만들면 안 됩니다.");
        }
        @Override public void createDraft(GuardianBookingDraftReference ref, GuardianBookingApproval approval,
                RepositoryCallback<CreateResult> callback) {
            throw new AssertionError("수신함에서 예약을 접수하면 안 됩니다.");
        }
    }
}
