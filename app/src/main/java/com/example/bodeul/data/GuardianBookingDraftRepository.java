package com.example.bodeul.data;

import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 저장 원문 경로이며 기존 직접 본문 전달 경로로 자동 우회하지 않는다. */
public interface GuardianBookingDraftRepository {
    void loadIdentity(RepositoryCallback<Identity> callback);
    void listReceipts(UUID requestId, RepositoryCallback<List<ReceiptRow>> callback);
    void submitDraft(GuardianBookingRequest request, RepositoryCallback<GuardianBookingStoredDraft.Summary> callback);
    void listDrafts(RepositoryCallback<List<GuardianBookingStoredDraft.Summary>> callback);
    void loadDraft(GuardianBookingDraftReference reference, RepositoryCallback<GuardianBookingStoredDraft> callback);
    void loadApproval(GuardianBookingDraftReference reference, RepositoryCallback<ApprovalStatus> callback);
    void grantDraft(GuardianBookingDraftReference reference, long expectedVersion, boolean adultPatientConfirmed,
            RepositoryCallback<GuardianBookingApproval> callback);
    void revokeDraft(GuardianBookingDraftReference reference, long expectedVersion,
            RepositoryCallback<GuardianBookingApproval> callback);
    void createDraft(GuardianBookingDraftReference reference, GuardianBookingApproval approval,
            RepositoryCallback<GuardianBookingRepository.CreateResult> callback);

    final class Identity {
        public final UUID id;
        public final boolean patient;
        public Identity(UUID id, boolean patient) { this.id = Objects.requireNonNull(id); this.patient = patient; }
    }

    final class ReceiptRow {
        public final UUID requestId;
        public final GuardianBookingReceipt receipt;
        public ReceiptRow(UUID requestId, GuardianBookingReceipt receipt) {
            this.requestId = Objects.requireNonNull(requestId);
            this.receipt = Objects.requireNonNull(receipt);
        }
    }

    final class ApprovalStatus {
        public final GuardianBookingApproval approval;
        public final boolean patient;
        public final boolean revocationOnly;

        public ApprovalStatus(GuardianBookingApproval approval, boolean patient, boolean revocationOnly) {
            this.approval = Objects.requireNonNull(approval);
            this.patient = patient;
            this.revocationOnly = revocationOnly;
            if (revocationOnly && (!patient || approval.getGrantId() == null)) {
                throw new IllegalArgumentException("기존 환자 승인만 철회 모드로 조회할 수 있습니다.");
            }
        }
    }
}
