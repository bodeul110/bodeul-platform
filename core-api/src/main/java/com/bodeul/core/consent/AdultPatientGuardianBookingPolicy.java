package com.bodeul.core.consent;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

/** 예약 전 생성 승인만 판정하며 예약 이후 정보공유 권한은 부여하지 않는다. */
public final class AdultPatientGuardianBookingPolicy {

    private AdultPatientGuardianBookingPolicy() {
    }

    public static Grant grantByPatient(
            UUID actorUserId,
            AppUserRole actorRole,
            boolean adultPatientConfirmed,
            UUID patientUserId,
            UUID guardianUserId,
            AppUserRole guardianRole,
            UUID clientRequestId,
            String requestFingerprint,
            Instant grantedAt,
            Instant expiresAt,
            String policyVersion) {
        requirePatientActor(actorUserId, actorRole, patientUserId);
        if (!adultPatientConfirmed) {
            throw new IllegalArgumentException("성인 환자 본인 확인이 필요합니다.");
        }
        if (guardianRole != AppUserRole.GUARDIAN) {
            throw new IllegalArgumentException("보호자 역할 계정만 예약 생성 대상으로 지정할 수 있습니다.");
        }
        return new Grant(
                UUID.randomUUID(), patientUserId, guardianUserId, clientRequestId, requestFingerprint,
                policyVersion, actorUserId, grantedAt, expiresAt, null, null, 0);
    }

    public static Grant revokeByPatient(
            Grant grant, UUID actorUserId, AppUserRole actorRole, Instant revokedAt) {
        Objects.requireNonNull(grant, "철회할 예약 생성 승인이 필요합니다.");
        requirePatientActor(actorUserId, actorRole, grant.patientUserId());
        Objects.requireNonNull(revokedAt, "철회 시각이 필요합니다.");
        if (revokedAt.isBefore(grant.grantedAt())) {
            throw new IllegalArgumentException("철회 시각은 승인 시각보다 빠를 수 없습니다.");
        }
        if (grant.revokedAt() != null) {
            return grant;
        }
        return new Grant(
                grant.id(), grant.patientUserId(), grant.guardianUserId(), grant.clientRequestId(),
                grant.requestFingerprint(),
                grant.policyVersion(), grant.grantedByUserId(), grant.grantedAt(), grant.expiresAt(),
                actorUserId, revokedAt, Math.addExact(grant.version(), 1));
    }

    public static Decision evaluateCreation(
            Optional<Grant> candidate,
            UUID requesterUserId,
            AppUserRole requesterRole,
            UUID patientUserId,
            UUID clientRequestId,
            String currentRequestFingerprint,
            String currentPolicyVersion,
            Instant requestedAt) {
        Objects.requireNonNull(candidate, "예약 생성 승인 조회 결과가 필요합니다.");
        Objects.requireNonNull(requesterUserId, "요청자 식별자가 필요합니다.");
        Objects.requireNonNull(requesterRole, "요청자 역할이 필요합니다.");
        Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
        Objects.requireNonNull(clientRequestId, "예약 생성 요청 식별자가 필요합니다.");
        String requestFingerprint = requireRequestFingerprint(currentRequestFingerprint);
        String policyVersion = normalizePolicyVersion(currentPolicyVersion);
        Objects.requireNonNull(requestedAt, "판정 시각이 필요합니다.");

        if (candidate.isEmpty()) {
            return Decision.denied(DecisionReason.GRANT_MISSING);
        }
        Grant grant = candidate.orElseThrow();
        if (requesterRole != AppUserRole.GUARDIAN) {
            return Decision.denied(DecisionReason.REQUESTER_NOT_GUARDIAN);
        }
        if (!grant.patientUserId().equals(patientUserId)) {
            return Decision.denied(DecisionReason.PATIENT_MISMATCH);
        }
        if (!grant.guardianUserId().equals(requesterUserId)) {
            return Decision.denied(DecisionReason.GUARDIAN_MISMATCH);
        }
        if (!grant.clientRequestId().equals(clientRequestId)) {
            return Decision.denied(DecisionReason.REQUEST_MISMATCH);
        }
        // 승인 때 확인한 본문과 최초 INSERT 직전 본문을 비교한다. 요청 ID만으로는 부족하다.
        if (!grant.requestFingerprint().equals(requestFingerprint)) {
            return Decision.denied(DecisionReason.REQUEST_CONTENT_MISMATCH);
        }
        if (!grant.policyVersion().equals(policyVersion)) {
            return Decision.denied(DecisionReason.POLICY_VERSION_MISMATCH);
        }
        // 철회된 승인은 과거 시각을 입력해도 새 예약 생성에 재사용하지 않는다.
        if (grant.revokedAt() != null) {
            return Decision.denied(DecisionReason.REVOKED);
        }
        if (requestedAt.isBefore(grant.grantedAt())) {
            return Decision.denied(DecisionReason.NOT_YET_ACTIVE);
        }
        if (!requestedAt.isBefore(grant.expiresAt())) {
            return Decision.denied(DecisionReason.EXPIRED);
        }
        return new Decision(true, DecisionReason.ALLOWED);
    }

    private static void requirePatientActor(
            UUID actorUserId, AppUserRole actorRole, UUID patientUserId) {
        Objects.requireNonNull(actorUserId, "행위자 식별자가 필요합니다.");
        Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
        if (actorRole != AppUserRole.PATIENT || !actorUserId.equals(patientUserId)) {
            throw new IllegalArgumentException("성인 환자 본인만 예약 생성 승인을 변경할 수 있습니다.");
        }
    }

    private static String normalizePolicyVersion(String policyVersion) {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("예약 생성 승인 정책 버전이 필요합니다.");
        }
        return policyVersion.trim();
    }

    private static String requireRequestFingerprint(String fingerprint) {
        if (fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("서버가 계산한 예약 생성 본문의 SHA-256 값이 필요합니다.");
        }
        return fingerprint;
    }

    public enum DecisionReason {
        ALLOWED, GRANT_MISSING, REQUESTER_NOT_GUARDIAN, PATIENT_MISMATCH,
        GUARDIAN_MISMATCH, REQUEST_MISMATCH, REQUEST_CONTENT_MISMATCH, POLICY_VERSION_MISMATCH,
        NOT_YET_ACTIVE, EXPIRED, REVOKED
    }

    public record Decision(boolean allowed, DecisionReason reason) {
        public Decision {
            Objects.requireNonNull(reason, "판정 사유가 필요합니다.");
            if (allowed != (reason == DecisionReason.ALLOWED)) {
                throw new IllegalArgumentException("허용 여부와 판정 사유가 일치하지 않습니다.");
            }
        }

        private static Decision denied(DecisionReason reason) {
            return new Decision(false, reason);
        }
    }

    public record Grant(
            UUID id,
            UUID patientUserId,
            UUID guardianUserId,
            UUID clientRequestId,
            String requestFingerprint,
            String policyVersion,
            UUID grantedByUserId,
            Instant grantedAt,
            Instant expiresAt,
            UUID revokedByUserId,
            Instant revokedAt,
            long version) {
        public Grant {
            Objects.requireNonNull(id, "승인 식별자가 필요합니다.");
            Objects.requireNonNull(patientUserId, "환자 식별자가 필요합니다.");
            Objects.requireNonNull(guardianUserId, "보호자 식별자가 필요합니다.");
            Objects.requireNonNull(clientRequestId, "예약 생성 요청 식별자가 필요합니다.");
            requestFingerprint = requireRequestFingerprint(requestFingerprint);
            if (patientUserId.equals(guardianUserId)) {
                throw new IllegalArgumentException("환자 본인을 보호자로 지정할 수 없습니다.");
            }
            policyVersion = normalizePolicyVersion(policyVersion);
            if (!patientUserId.equals(grantedByUserId)) {
                throw new IllegalArgumentException("환자 본인의 승인만 기록할 수 있습니다.");
            }
            Objects.requireNonNull(grantedAt, "승인 시각이 필요합니다.");
            Objects.requireNonNull(expiresAt, "만료 시각이 필요합니다.");
            if (!expiresAt.isAfter(grantedAt)) {
                throw new IllegalArgumentException("만료 시각은 승인 시각보다 늦어야 합니다.");
            }
            if ((revokedByUserId == null) != (revokedAt == null)) {
                throw new IllegalArgumentException("철회 행위자와 시각은 함께 기록해야 합니다.");
            }
            if (revokedByUserId != null && !patientUserId.equals(revokedByUserId)) {
                throw new IllegalArgumentException("환자 본인의 철회만 기록할 수 있습니다.");
            }
            if (revokedAt != null && revokedAt.isBefore(grantedAt)) {
                throw new IllegalArgumentException("철회 시각은 승인 시각보다 빠를 수 없습니다.");
            }
            if (version < 0) {
                throw new IllegalArgumentException("승인 버전은 0 이상이어야 합니다.");
            }
        }
    }
}
