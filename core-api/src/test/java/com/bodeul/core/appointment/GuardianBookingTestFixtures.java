package com.bodeul.core.appointment;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentService.AppointmentDraft;
import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;

final class GuardianBookingTestFixtures {
    private GuardianBookingTestFixtures() { }

    static CreateAppointmentCommand command(UUID request, Instant appointmentAt) {
        return new CreateAppointmentCommand(request, draft(appointmentAt, "검증 병원", "ON_SITE"),
                AppointmentPricePolicy.VERSION, AppointmentPricePolicy.BASE_PRICE);
    }

    static AppointmentDraft draft(Instant appointmentAt, String hospital, String payment) {
        return new AppointmentDraft("테스트 환자", "01012345678", "patient@example.test",
                "검증용 상태", "검증용 복약", hospital, "내과", 37.5, 127.0,
                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").format(appointmentAt.atZone(ZoneId.of("Asia/Seoul"))),
                "1층", "검증용 요청", "INDEPENDENT", "ONE_WAY", "ANY", payment, "NONE");
    }
}
