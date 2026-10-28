package com.example.bodeul.data;

import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;

public interface GuardianBookingRepository {
    void load(GuardianBookingRequest request, RepositoryCallback<Review> callback);
    void grant(GuardianBookingRequest request, long expectedVersion, boolean adultPatientConfirmed,
            RepositoryCallback<GuardianBookingApproval> callback);
    void revoke(GuardianBookingRequest request, long expectedVersion,
            RepositoryCallback<GuardianBookingApproval> callback);
    void create(GuardianBookingRequest request, GuardianBookingApproval approval,
            RepositoryCallback<CreateResult> callback);

    enum CreateFailure { NOT_SENT, REJECTED, UNKNOWN }

    /** 전송 전 실패·확정 거부·전송 후 불명확한 결과를 문자열 안내와 분리한다. */
    final class CreateResult {
        public final GuardianBookingReceipt receipt;
        public final CreateFailure failure;

        private CreateResult(GuardianBookingReceipt receipt, CreateFailure failure) {
            this.receipt = receipt;
            this.failure = failure;
        }

        public static CreateResult accepted(GuardianBookingReceipt receipt) {
            return new CreateResult(java.util.Objects.requireNonNull(receipt), null);
        }

        public static CreateResult failed(CreateFailure failure) {
            return new CreateResult(null, java.util.Objects.requireNonNull(failure));
        }
    }

    final class Review {
        public final GuardianBookingRequest request;
        public final GuardianBookingApproval approval;
        public final boolean patient;
        public final boolean previewVerified;

        public Review(GuardianBookingRequest request, GuardianBookingApproval approval, boolean patient) {
            this(request, approval, patient, patient);
        }

        public Review(GuardianBookingRequest request, GuardianBookingApproval approval, boolean patient,
                boolean previewVerified) {
            this.request = java.util.Objects.requireNonNull(request);
            this.approval = java.util.Objects.requireNonNull(approval);
            this.patient = patient;
            this.previewVerified = previewVerified;
        }
    }
}
