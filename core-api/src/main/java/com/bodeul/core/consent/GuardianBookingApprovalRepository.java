package com.bodeul.core.consent;

import java.util.UUID;

import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;

interface GuardianBookingApprovalRepository {

    ApprovalState findCurrent(RequestKey key);

    /** 생성 판정부터 예약 INSERT까지 호출자의 같은 쓰기 트랜잭션에서 잠금을 유지한다. */
    ApprovalState lockCurrent(RequestKey key);

    /** 서버 정책으로 만든 다음 상태만 전달한다. 충돌 시 재조회·재판정하며 자동 덮어쓰지 않는다. */
    ApprovalState save(ApprovalState next, long expectedVersion);

    record RequestKey(UUID patientUserId, UUID guardianUserId, UUID clientRequestId) {
        public RequestKey {
            ApprovalState.pending(patientUserId, guardianUserId, clientRequestId);
        }

        ApprovalState pending() {
            return ApprovalState.pending(patientUserId, guardianUserId, clientRequestId);
        }
    }
}
