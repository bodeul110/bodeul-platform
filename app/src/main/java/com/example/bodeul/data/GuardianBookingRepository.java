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
            RepositoryCallback<GuardianBookingReceipt> callback);

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
