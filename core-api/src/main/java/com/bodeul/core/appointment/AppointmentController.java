package com.bodeul.core.appointment;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
@Profile({"database", "appointment-test"})
class AppointmentController {

    private final AppointmentService appointmentService;

    AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping
    ResponseEntity<AppointmentsResponse> getMyAppointments(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AppointmentsResponse(appointmentService.getMyAppointments(appUser)));
    }

    @GetMapping("/{appointmentId}")
    ResponseEntity<AppointmentService.AppointmentView> getAppointment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId) {
        return noStore(appointmentService.getAppointment(appUser, appointmentId));
    }

    @PostMapping({"", "/price-confirmed"})
    ResponseEntity<AppointmentService.AppointmentView> createAppointment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @RequestBody CreateAppointmentRequest request) {
        AppointmentService.AppointmentView created = appointmentService.createAppointment(
                appUser,
                new AppointmentService.CreateAppointmentCommand(
                        request == null ? null : request.clientRequestId(),
                        request == null ? null : request.toDraft(),
                        request == null ? null : request.pricePolicyVersion(),
                        request == null ? null : request.confirmedFinalPrice()));
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PutMapping("/{appointmentId}")
    ResponseEntity<AppointmentService.AppointmentView> updateAppointment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId,
            @RequestBody UpdateAppointmentRequest request) {
        return noStore(appointmentService.updateAppointment(
                appUser,
                appointmentId,
                new AppointmentService.UpdateAppointmentCommand(
                        request == null || request.version() == null ? -1 : request.version(),
                        request == null ? null : request.toDraft())));
    }

    @PostMapping("/{appointmentId}/cancel")
    ResponseEntity<AppointmentService.AppointmentView> cancelAppointment(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId,
            @RequestBody CancelAppointmentRequest request) {
        long version = request == null || request.version() == null ? -1 : request.version();
        return noStore(appointmentService.cancelAppointment(appUser, appointmentId, version));
    }

    @GetMapping("/{appointmentId}/follow-up")
    ResponseEntity<AppointmentService.AppointmentFollowUpView> getAppointmentFollowUp(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId) {
        return noStore(appointmentService.getAppointmentFollowUp(appUser, appointmentId));
    }

    @PatchMapping("/{appointmentId}/follow-up")
    ResponseEntity<AppointmentService.AppointmentFollowUpView> updateAppointmentFollowUp(
            @AuthenticationPrincipal AppUserRepository.AppUser appUser,
            @PathVariable UUID appointmentId,
            @RequestBody UpdateAppointmentFollowUpRequest request) {
        return noStore(appointmentService.updateAppointmentFollowUp(
                appUser,
                appointmentId,
                request == null ? null : request.toCommand()));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    record AppointmentsResponse(List<AppointmentService.AppointmentView> appointments) {
    }

    record CancelAppointmentRequest(Long version) {
    }

    record CreateAppointmentRequest(
            UUID clientRequestId,
            String pricePolicyVersion,
            JsonNode expectedFinalPrice,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            String patientConditionSummary,
            String medicationSummary,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode) {

        Integer confirmedFinalPrice() {
            if (expectedFinalPrice == null || expectedFinalPrice.isNull()) {
                return null;
            }
            if (!expectedFinalPrice.isIntegralNumber() || !expectedFinalPrice.canConvertToInt()) {
                throw AppointmentException.invalidRequest("확인한 예약 금액은 정수로 보내 주세요.");
            }
            return expectedFinalPrice.intValue();
        }

        AppointmentService.AppointmentDraft toDraft() {
            return new AppointmentService.AppointmentDraft(
                    linkedParticipantName,
                    linkedParticipantPhone,
                    linkedParticipantEmail,
                    patientConditionSummary,
                    medicationSummary,
                    hospitalName,
                    departmentName,
                    hospitalLatitude,
                    hospitalLongitude,
                    appointmentAt,
                    meetingPlace,
                    specialNotes,
                    mobilitySupportCode,
                    tripTypeCode,
                    managerGenderPreferenceCode,
                    paymentMethodCode,
                    couponCode);
        }
    }

    record UpdateAppointmentRequest(
            Long version,
            String linkedParticipantName,
            String linkedParticipantPhone,
            String linkedParticipantEmail,
            String patientConditionSummary,
            String medicationSummary,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String paymentMethodCode,
            String couponCode) {

        AppointmentService.AppointmentDraft toDraft() {
            return new AppointmentService.AppointmentDraft(
                    linkedParticipantName,
                    linkedParticipantPhone,
                    linkedParticipantEmail,
                    patientConditionSummary,
                    medicationSummary,
                    hospitalName,
                    departmentName,
                    hospitalLatitude,
                    hospitalLongitude,
                    appointmentAt,
                    meetingPlace,
                    specialNotes,
                    mobilitySupportCode,
                    tripTypeCode,
                    managerGenderPreferenceCode,
                    paymentMethodCode,
                    couponCode);
        }
    }

    record UpdateAppointmentFollowUpRequest(
            Long version,
            String reviewRatingCode,
            String settlementFollowUpStatus,
            String settlementFollowUpNote,
            String supportEscalationStatus) {

        AppointmentService.UpdateAppointmentFollowUpCommand toCommand() {
            return new AppointmentService.UpdateAppointmentFollowUpCommand(
                    version == null ? -1 : version,
                    reviewRatingCode,
                    settlementFollowUpStatus,
                    settlementFollowUpNote,
                    supportEscalationStatus);
        }
    }
}
