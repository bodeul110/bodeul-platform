package com.bodeul.core.consent;

import org.springframework.http.HttpStatus;

public final class GuardianBookingException extends RuntimeException {
    private final HttpStatus status;
    private final String error;

    private GuardianBookingException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static GuardianBookingException disabled() {
        return new GuardianBookingException(HttpStatus.FORBIDDEN, "guardian_booking_disabled",
                "보호자 예약 승인 기능은 아직 사용할 수 없습니다.");
    }

    public static GuardianBookingException denied() {
        return new GuardianBookingException(HttpStatus.FORBIDDEN, "guardian_booking_permission_denied",
                "이 예약 생성 승인을 조회하거나 사용할 권한이 없습니다.");
    }

    public static GuardianBookingException invalid(String message) {
        return new GuardianBookingException(HttpStatus.BAD_REQUEST, "invalid_guardian_booking_request", message);
    }

    public static GuardianBookingException conflict() {
        return new GuardianBookingException(HttpStatus.CONFLICT, "guardian_booking_approval_conflict",
                "승인 상태가 변경되었거나 유효하지 않습니다. 환자의 최신 승인을 다시 확인해 주세요.");
    }

    public static GuardianBookingException unavailable() {
        return new GuardianBookingException(HttpStatus.SERVICE_UNAVAILABLE, "guardian_booking_unavailable",
                "예약 승인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    public HttpStatus status() { return status; }
    public String error() { return error; }
}
