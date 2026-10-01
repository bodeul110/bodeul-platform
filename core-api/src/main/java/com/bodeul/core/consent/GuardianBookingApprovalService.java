package com.bodeul.core.consent;

import java.time.Instant;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository.AppUser;

public interface GuardianBookingApprovalService {
    void requireEnabled();

    void requireParticipants(AppUser actor, UUID patientId, UUID guardianId);

    ApprovalView get(AppUser actor, UUID patientId, UUID guardianId, UUID requestId);

    ApprovalView grant(AppUser patient, UUID guardianId, UUID requestId, long expectedVersion,
                       boolean adultPatientConfirmed, String serverFingerprint, Instant appointmentAt);

    ApprovalView revoke(AppUser patient, UUID guardianId, UUID requestId, long expectedVersion);

    /** 예약 INSERT를 수행하는 쓰기 트랜잭션이 시작된 뒤 호출해야 한다. */
    void lockForCreation(AppUser guardian, UUID patientId, UUID requestId,
                         UUID grantId, long version, String serverFingerprint);

    record ApprovalView(UUID grantId, long version, String policyVersion,
                        Instant grantedAt, Instant expiresAt, Instant revokedAt, boolean active) { }
}
