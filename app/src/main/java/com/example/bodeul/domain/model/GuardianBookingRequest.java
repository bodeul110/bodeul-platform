package com.example.bodeul.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 한 번 검토한 요청 ID와 본문을 승인·접수·재시도 동안 함께 보존한다. */
public final class GuardianBookingRequest {
    private final UUID patientId;
    private final UUID guardianId;
    private final UUID requestId;
    private final BookingRequestDraft draft;

    public GuardianBookingRequest(UUID patientId, UUID guardianId, UUID requestId,
            BookingRequestDraft draft) {
        this.patientId = Objects.requireNonNull(patientId);
        this.guardianId = Objects.requireNonNull(guardianId);
        this.requestId = Objects.requireNonNull(requestId);
        this.draft = Objects.requireNonNull(draft);
        if (patientId.equals(guardianId)) {
            throw new IllegalArgumentException("환자와 보호자는 서로 다른 계정이어야 합니다.");
        }
        if (draft.getPriceSummary().getFinalPrice() != BookingPricePolicy.BASE_PRICE
                || draft.getCouponType() != BookingCouponType.NONE) {
            throw new IllegalArgumentException("신규 예약 금액과 쿠폰을 확인해 주세요.");
        }
    }

    public UUID getPatientId() { return patientId; }
    public UUID getGuardianId() { return guardianId; }
    public UUID getRequestId() { return requestId; }
    public BookingRequestDraft getDraft() { return draft; }

    public GuardianBookingRequest withNormalizedDraft(BookingRequestDraft normalizedDraft) {
        return new GuardianBookingRequest(patientId, guardianId, requestId, normalizedDraft);
    }
}
