package com.example.bodeul.domain.model;

import java.time.Instant;
import java.util.UUID;

/** 예약 생성 승인이다. 정보공유 동의나 기존 예약 취소 권한으로 사용하지 않는다. */
public final class GuardianBookingApproval {
    public static final String POLICY_VERSION = "adult-patient-guardian-booking-v1";
    private final UUID grantId;
    private final long version;
    private final Instant grantedAt;
    private final Instant expiresAt;
    private final Instant revokedAt;
    private final boolean active;

    public GuardianBookingApproval(UUID grantId, long version, Instant grantedAt,
            Instant expiresAt, Instant revokedAt, boolean active) {
        if (version < 0 || version == Long.MAX_VALUE
                || (version == 0) != (grantId == null)
                || (grantId == null && (grantedAt != null || expiresAt != null || revokedAt != null || active))
                || (grantId != null && (grantedAt == null || expiresAt == null
                    || !expiresAt.isAfter(grantedAt)))
                || (revokedAt != null && revokedAt.isBefore(grantedAt))
                || (active && revokedAt != null)) {
            throw new IllegalArgumentException("예약 승인 응답을 확인하지 못했습니다.");
        }
        this.grantId = grantId;
        this.version = version;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.active = active;
    }

    public UUID getGrantId() { return grantId; }
    public long getVersion() { return version; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean canCreateAt(Instant now) {
        return active && !now.isBefore(grantedAt) && now.isBefore(expiresAt);
    }

    public boolean canRevoke() { return grantId != null && revokedAt == null; }
}
