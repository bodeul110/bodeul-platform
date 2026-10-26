package com.bodeul.core.appointment;

import java.util.List;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;

public interface AppointmentService {

    List<AppointmentView> getMyAppointments(AppUserRepository.AppUser appUser);

    AppointmentView getAppointment(AppUserRepository.AppUser appUser, UUID appointmentId);

    AppointmentView createAppointment(
            AppUserRepository.AppUser appUser,
            CreateAppointmentCommand command);

    AppointmentView updateAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentCommand command);

    AppointmentView cancelAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            long version);

    AppointmentFollowUpView getAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId);

    AppointmentFollowUpView updateAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentFollowUpCommand command);

    record AppointmentDraft(
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
    }

    record CreateAppointmentCommand(
            UUID clientRequestId,
            AppointmentDraft draft,
            String pricePolicyVersion,
            Integer expectedFinalPrice) {

        public CreateAppointmentCommand(UUID clientRequestId, AppointmentDraft draft) {
            this(clientRequestId, draft, null, null);
        }
    }

    record UpdateAppointmentCommand(long version, AppointmentDraft draft) {
    }

    record UpdateAppointmentFollowUpCommand(
            long version,
            String reviewRatingCode,
            String settlementStatus,
            String settlementNote,
            String supportEscalationStatus) {
    }

    record AppointmentView(
            UUID id,
            String legacyFirestoreId,
            String publicCode,
            UUID patientUserId,
            UUID guardianUserId,
            UUID managerUserId,
            String managerName,
            String managerPhone,
            String managerEmail,
            String patientName,
            String patientPhone,
            String patientEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail,
            String hospitalName,
            String departmentName,
            double hospitalLatitude,
            double hospitalLongitude,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String patientConditionSummary,
            String medicationSummary,
            String mobilitySupportCode,
            String tripTypeCode,
            String managerGenderPreferenceCode,
            String status,
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice,
            String paymentMethodCode,
            String couponCode,
            String paymentStatusCode,
            String paymentApprovalCode,
            String paymentApprovedAt,
            String paymentProviderLabel,
            long version) {
    }

    record AppointmentFollowUpView(
            UUID appointmentRequestId,
            String reviewRatingCode,
            String reviewSavedAt,
            String settlementFollowUpStatus,
            String settlementFollowUpNote,
            String settlementFollowUpSavedAt,
            String supportEscalationStatus,
            String supportEscalatedAt,
            long version) {
    }
}
