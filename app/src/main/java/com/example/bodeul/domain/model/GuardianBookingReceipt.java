package com.example.bodeul.domain.model;

import java.util.Objects;
import java.util.UUID;

/** 생성 성공 뒤에도 예약 상세나 리포트 열람 권한을 가정하지 않는 최소 접수증이다. */
public final class GuardianBookingReceipt {
    private final UUID appointmentId;
    private final String publicCode;

    public GuardianBookingReceipt(UUID appointmentId, String publicCode) {
        this.appointmentId = Objects.requireNonNull(appointmentId);
        if (publicCode == null || publicCode.trim().isEmpty()) {
            throw new IllegalArgumentException("접수 번호를 확인하지 못했습니다.");
        }
        this.publicCode = publicCode;
    }

    public UUID getAppointmentId() { return appointmentId; }
    public String getPublicCode() { return publicCode; }
}
