package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.util.UserProfileSanitizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

@androidx.annotation.RequiresApi(26)
final class GuardianBookingForm {
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.KOREA)
            .withResolverStyle(ResolverStyle.STRICT);

    private GuardianBookingForm() { }

    static boolean isValid(BookingRequestDraft draft, BookingHospitalSelection hospital, Clock clock) {
        if (draft == null || hospital == null || !hospital.isComplete()
                || !hospital.getHospitalName().equals(draft.getHospitalName())
                || !hospital.getDepartmentName().equals(draft.getDepartmentName())
                || hospital.getHospitalLatitude() != draft.getHospitalLatitude()
                || hospital.getHospitalLongitude() != draft.getHospitalLongitude()
                || !UserProfileSanitizer.isValidPhone(draft.getLinkedParticipantPhone())
                || draft.getLinkedParticipantName().isEmpty() || draft.getMeetingPlace().isEmpty()
                || draft.getPatientConditionSummary().isEmpty() || !draft.getPaymentMethod().isSelectableForRequest()) return false;
        try {
            return LocalDateTime.parse(draft.getAppointmentAt(), TIME).atZone(SEOUL).toInstant().isAfter(clock.instant());
        } catch (DateTimeParseException invalid) {
            return false;
        }
    }
}
