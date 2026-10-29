package com.example.bodeul.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 원문 없이도 같은 요청의 승인·철회·접수증을 다시 확인하는 식별자다. */
public final class GuardianBookingDraftReference {
    private final UUID patientId;
    private final UUID guardianId;
    private final UUID requestId;

    public GuardianBookingDraftReference(UUID patientId, UUID guardianId, UUID requestId) {
        this.patientId = Objects.requireNonNull(patientId);
        this.guardianId = Objects.requireNonNull(guardianId);
        this.requestId = Objects.requireNonNull(requestId);
        if (patientId.equals(guardianId)) {
            throw new IllegalArgumentException("환자와 보호자는 서로 다른 계정이어야 합니다.");
        }
    }

    public static GuardianBookingDraftReference from(GuardianBookingRequest request) {
        return new GuardianBookingDraftReference(request.getPatientId(), request.getGuardianId(), request.getRequestId());
    }

    public UUID getPatientId() { return patientId; }
    public UUID getGuardianId() { return guardianId; }
    public UUID getRequestId() { return requestId; }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof GuardianBookingDraftReference)) return false;
        GuardianBookingDraftReference value = (GuardianBookingDraftReference) other;
        return patientId.equals(value.patientId) && guardianId.equals(value.guardianId)
                && requestId.equals(value.requestId);
    }

    @Override
    public int hashCode() { return Objects.hash(patientId, guardianId, requestId); }
}
