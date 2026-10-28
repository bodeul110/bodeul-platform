package com.example.bodeul;

import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingRequest;

import java.time.Instant;
import java.util.UUID;

public final class GuardianBookingTestData {
    public static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    public static final UUID PATIENT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID GUARDIAN = UUID.fromString("20000000-0000-0000-0000-000000000002");
    public static final UUID REQUEST = UUID.fromString("30000000-0000-0000-0000-000000000003");
    public static final UUID GRANT = UUID.fromString("40000000-0000-0000-0000-000000000004");

    public static GuardianBookingRequest request() {
        return new GuardianBookingRequest(PATIENT, GUARDIAN, REQUEST, BookingRequestDraft.builder()
                .linkedParticipantName("합성 환자").linkedParticipantPhone("01000000000")
                .linkedParticipantEmail("patient@example.com").hospitalName("합성 병원").departmentName("내과")
                .hospitalLatitude(37.5).hospitalLongitude(127).appointmentAt("2026-10-29 10:00")
                .meetingPlace("1층").patientConditionSummary("첫째 줄\n둘째 줄")
                .medicationSummary("합성 복약").specialNotes("합성 요청")
                .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                .priceSummary(new BookingPriceSummary(40_000, 0, 0, 40_000)).build());
    }

    public static GuardianBookingApproval inactive() {
        return new GuardianBookingApproval(null, 0, null, null, null, false);
    }

    public static GuardianBookingApproval active(long version) {
        return new GuardianBookingApproval(GRANT, version, NOW.minusSeconds(1),
                NOW.plusSeconds(3600), null, true);
    }
}
