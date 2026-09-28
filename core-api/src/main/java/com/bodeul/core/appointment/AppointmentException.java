package com.bodeul.core.appointment;

import org.springframework.http.HttpStatus;

final class AppointmentException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private AppointmentException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    static AppointmentException invalidRequest(String message) {
        return new AppointmentException(
                HttpStatus.BAD_REQUEST,
                "invalid_appointment_request",
                message);
    }

    static AppointmentException permissionDenied() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_permission_denied",
                "이 예약을 조회하거나 변경할 권한이 없습니다.");
    }

    static AppointmentException priceConfirmationRequired() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_price_confirmation_required",
                "예약 요금 정보가 변경되었습니다. 최신 앱으로 업데이트한 뒤 요금을 다시 확인해 주세요.");
    }

    static AppointmentException roleNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_role_not_supported",
                "환자 또는 보호자 계정으로 접근해 주세요.");
    }

    static AppointmentException guardianCreationNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "guardian_appointment_creation_not_supported",
                "보호자 계정에서는 새 예약을 만들 수 없습니다. 환자 본인이 예약한 뒤 정보공유 동의를 요청해 주세요.");
    }

    static AppointmentException guardianMutationNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "guardian_appointment_mutation_not_supported",
                "정보공유 동의는 예약 업무 대리 권한이 아닙니다. 예약 변경은 환자 본인에게 요청해 주세요.");
    }

    static AppointmentException readRoleNotSupported() {
        return new AppointmentException(
                HttpStatus.FORBIDDEN,
                "appointment_read_role_not_supported",
                "환자, 보호자 또는 배정된 매니저 계정으로 접근해 주세요.");
    }

    static AppointmentException notFound() {
        return new AppointmentException(
                HttpStatus.NOT_FOUND,
                "appointment_not_found",
                "예약 정보를 찾을 수 없습니다.");
    }

    static AppointmentException profileNotReady() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_profile_not_ready",
                "예약에 사용할 사용자 프로필이 아직 준비되지 않았습니다.");
    }

    static AppointmentException participantAmbiguous() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "linked_participant_ambiguous",
                "입력한 연락처와 일치하는 연결 계정을 하나로 결정할 수 없습니다.");
    }

    static AppointmentException requesterLinkConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_requester_link_conflict",
                "처음 예약을 만든 사용자의 연결 정보를 유지해야 합니다.");
    }

    static AppointmentException stateConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_state_conflict",
                "현재 예약 상태에서는 요청한 변경을 적용할 수 없습니다.");
    }

    static AppointmentException versionConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_version_conflict",
                "다른 변경이 먼저 반영되었습니다. 최신 예약 정보를 다시 확인해 주세요.");
    }

    static AppointmentException idempotencyConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "appointment_idempotency_conflict",
                "같은 clientRequestId를 다른 예약 내용으로 다시 사용할 수 없습니다.");
    }

    static AppointmentException bankTransferTermsConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "bank_transfer_terms_conflict",
                "무통장입금 예약의 결제수단·이동 조건·쿠폰·입금액은 생성 후 변경할 수 없습니다.");
    }

    static AppointmentException paymentOperationConflict() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "payment_operation_conflict",
                "같은 결제 작업 ID를 다른 내용으로 다시 사용할 수 없습니다.");
    }

    static AppointmentException supportEscalationNotSupported() {
        return new AppointmentException(
                HttpStatus.CONFLICT,
                "support_escalation_not_supported",
                "MVP에서는 긴급 지원 상태를 새로 저장하지 않습니다.");
    }

    static AppointmentException publicCodeUnavailable() {
        return new AppointmentException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "appointment_public_code_unavailable",
                "예약 코드를 발급하지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    HttpStatus status() {
        return status;
    }

    String error() {
        return error;
    }
}
