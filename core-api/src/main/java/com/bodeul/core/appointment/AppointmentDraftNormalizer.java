package com.bodeul.core.appointment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.appointment.AppointmentService.AppointmentDraft;

/** 환자 직접 예약과 보호자 승인 경로의 입력 정규화를 동일하게 유지한다. */
final class AppointmentDraftNormalizer {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter APPOINTMENT_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm", Locale.KOREA)
            .withResolverStyle(ResolverStyle.STRICT);

    private AppointmentDraftNormalizer() {
    }

    static NormalizedDraft normalize(AppointmentDraft draft) {
        if (draft == null) {
            throw AppointmentException.invalidRequest("예약 입력값이 필요합니다.");
        }

        String linkedParticipantName = requireText(
                normalizeName(draft.linkedParticipantName()),
                "연결 사용자 이름",
                100);
        String linkedParticipantPhone = normalizePhone(draft.linkedParticipantPhone());
        if (!isValidPhone(linkedParticipantPhone)) {
            throw AppointmentException.invalidRequest("연결 사용자 전화번호를 확인해 주세요.");
        }
        String linkedParticipantEmail = limitText(
                normalizeEmail(draft.linkedParticipantEmail()),
                "연결 사용자 이메일",
                320);
        if (!linkedParticipantEmail.isEmpty() && !isValidEmail(linkedParticipantEmail)) {
            throw AppointmentException.invalidRequest("연결 사용자 이메일을 확인해 주세요.");
        }
        String patientConditionSummary = requireText(
                draft.patientConditionSummary(),
                "환자 상태",
                2_000);
        String hospitalName = requireText(draft.hospitalName(), "병원 이름", 200);
        String departmentName = requireText(draft.departmentName(), "진료과", 100);
        String meetingPlace = requireText(draft.meetingPlace(), "만남 장소", 300);
        String appointmentAtText = requireText(draft.appointmentAt(), "예약 일시", 16);
        Instant appointmentAt = parseAppointmentAt(appointmentAtText);
        if (!Double.isFinite(draft.hospitalLatitude())
                || !Double.isFinite(draft.hospitalLongitude())
                || draft.hospitalLatitude() < -90 || draft.hospitalLatitude() > 90
                || draft.hospitalLongitude() < -180 || draft.hospitalLongitude() > 180) {
            throw AppointmentException.invalidRequest("병원 좌표 범위를 확인해 주세요.");
        }

        String mobilitySupport = requireCode(
                draft.mobilitySupportCode(),
                "이동 보조 방식",
                MobilitySupport.values());
        String tripType = requireCode(draft.tripTypeCode(), "이동 범위", TripType.values());
        String managerGender = requireCode(
                draft.managerGenderPreferenceCode(),
                "매니저 성별 선호",
                ManagerGender.values());
        String paymentMethod = requireCode(
                draft.paymentMethodCode(),
                "결제 방식",
                PaymentMethod.values());
        String coupon = requireCode(draft.couponCode(), "쿠폰", Coupon.values());

        return new NormalizedDraft(
                linkedParticipantName,
                linkedParticipantPhone,
                linkedParticipantEmail,
                patientConditionSummary,
                limitText(normalizeText(draft.medicationSummary()), "복약 정보", 2_000),
                hospitalName,
                departmentName,
                draft.hospitalLatitude(),
                draft.hospitalLongitude(),
                appointmentAt,
                meetingPlace,
                limitText(normalizeText(draft.specialNotes()), "특이사항", 2_000),
                mobilitySupport,
                tripType,
                managerGender,
                paymentMethod,
                coupon);
    }

    static AppointmentCreateFingerprint.CreateRequest toFingerprintRequest(
            AppUserRepository.AppUser appUser,
            UUID clientRequestId,
            NormalizedDraft draft) {
        return new AppointmentCreateFingerprint.CreateRequest(
                appUser.id(),
                appUser.role(),
                clientRequestId,
                draft.linkedParticipantName(),
                draft.linkedParticipantPhone(),
                draft.linkedParticipantEmail(),
                draft.patientConditionSummary(),
                draft.medicationSummary(),
                draft.hospitalName(),
                draft.departmentName(),
                draft.hospitalLatitude(),
                draft.hospitalLongitude(),
                draft.appointmentAt(),
                draft.meetingPlace(),
                draft.specialNotes(),
                draft.mobilitySupportCode(),
                draft.tripTypeCode(),
                draft.managerGenderPreferenceCode(),
                draft.paymentMethodCode(),
                draft.couponCode());
    }

    private static Instant parseAppointmentAt(String value) {
        try {
            return LocalDateTime.parse(value, APPOINTMENT_FORMATTER)
                    .atZone(SEOUL)
                    .toInstant();
        } catch (DateTimeParseException exception) {
            throw AppointmentException.invalidRequest("예약 일시는 yyyy-MM-dd HH:mm 형식이어야 합니다.");
        }
    }

    private static String requireText(String value, String label, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            throw AppointmentException.invalidRequest(label + "이(가) 필요합니다.");
        }
        return limitText(normalized, label, maxLength);
    }

    private static String limitText(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw AppointmentException.invalidRequest(label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return value;
    }

    private static <T extends Enum<T>> String requireCode(String value, String label, T[] values) {
        String normalized = normalizeText(value);
        List<String> allowed = new ArrayList<>();
        for (T candidate : values) {
            allowed.add(candidate.name());
        }
        if (!allowed.contains(normalized)) {
            throw AppointmentException.invalidRequest(label + " 값이 올바르지 않습니다.");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    static String normalizeName(String value) {
        return normalizeText(value).replaceAll("\\s+", " ");
    }

    static String normalizeEmail(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String value) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (digits.startsWith("82") && digits.length() >= 11) {
            digits = "0" + digits.substring(2);
        }
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7);
        }
        if (digits.length() == 10) {
            int prefix = digits.startsWith("02") ? 2 : 3;
            int middle = prefix + (10 - prefix) / 2;
            return digits.substring(0, prefix) + "-" + digits.substring(prefix, middle) + "-" + digits.substring(middle);
        }
        if (digits.length() == 9 && digits.startsWith("02")) {
            return digits.substring(0, 2) + "-" + digits.substring(2, 5) + "-" + digits.substring(5);
        }
        return digits;
    }

    private static boolean isValidPhone(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return digits.startsWith("0") && digits.length() >= 9 && digits.length() <= 11;
    }

    private static boolean isValidEmail(String value) {
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < value.length() - 1 && value.indexOf(' ') < 0;
    }

    private enum MobilitySupport {
        INDEPENDENT,
        WALKING_AID,
        WHEELCHAIR
    }

    private enum TripType {
        ONE_WAY,
        ROUND_TRIP
    }

    private enum ManagerGender {
        ANY,
        FEMALE,
        MALE
    }

    private enum PaymentMethod {
        CARD,
        EASY_PAY,
        ON_SITE,
        BANK_TRANSFER
    }

    private enum Coupon {
        NONE,
        FIRST_VISIT,
        FAMILY
    }

    record NormalizedDraft(
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
        AppointmentDraft toDraft() {
            return new AppointmentDraft(linkedParticipantName, linkedParticipantPhone, linkedParticipantEmail,
                    patientConditionSummary, medicationSummary, hospitalName, departmentName,
                    hospitalLatitude, hospitalLongitude, APPOINTMENT_FORMATTER.format(appointmentAt.atZone(SEOUL)),
                    meetingPlace, specialNotes, mobilitySupportCode, tripTypeCode,
                    managerGenderPreferenceCode, paymentMethodCode, couponCode);
        }
    }

}
