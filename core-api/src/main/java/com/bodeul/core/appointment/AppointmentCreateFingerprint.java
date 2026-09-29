package com.bodeul.core.appointment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;

public final class AppointmentCreateFingerprint {

    private static final String CONTRACT_VERSION = "bodeul-appointment-create-v1";

    private AppointmentCreateFingerprint() {
    }

    /** 서버에서 검증·정규화한 생성 본문의 승인 및 재시도 비교값을 만든다. */
    public static String from(CreateRequest request) {
        MessageDigest digest = sha256();
        add(digest, CONTRACT_VERSION);
        add(digest, request.requesterUserId());
        add(digest, request.requesterRole());
        add(digest, request.clientRequestId());
        add(digest, request.linkedParticipantName());
        add(digest, request.linkedParticipantPhone());
        add(digest, request.linkedParticipantEmail());
        add(digest, request.patientConditionSummary());
        add(digest, request.medicationSummary());
        add(digest, request.hospitalName());
        add(digest, request.departmentName());
        add(digest, Double.toHexString(request.hospitalLatitude()));
        add(digest, Double.toHexString(request.hospitalLongitude()));
        add(digest, request.appointmentAt());
        add(digest, request.meetingPlace());
        add(digest, request.specialNotes());
        add(digest, request.mobilitySupportCode());
        add(digest, request.tripTypeCode());
        add(digest, request.managerGenderPreferenceCode());
        add(digest, request.paymentMethodCode());
        add(digest, request.couponCode());
        return HexFormat.of().formatHex(digest.digest());
    }

    /** 기존 환자 예약 fingerprint는 바꾸지 않고 신규 보호자 승인에만 견적을 묶는다. */
    static String forGuardianApproval(CreateRequest request, String pricePolicyVersion, Integer confirmedPrice) {
        MessageDigest digest = sha256();
        add(digest, "bodeul-guardian-booking-create-v1");
        add(digest, from(request));
        add(digest, pricePolicyVersion);
        add(digest, confirmedPrice);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void add(MessageDigest digest, Object value) {
        byte[] bytes = value == null
                ? null
                : value.toString().getBytes(StandardCharsets.UTF_8);
        int length = bytes == null ? -1 : bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        if (bytes != null) {
            digest.update(bytes);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    public record CreateRequest(
            UUID requesterUserId,
            AppUserRole requesterRole,
            UUID clientRequestId,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            String patientConditionSummary,
            String medicationSummary,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            Instant appointmentAt,
            String meetingPlace,
            String specialNotes,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode) {
    }
}
