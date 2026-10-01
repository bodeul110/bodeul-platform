package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

import com.bodeul.core.appointment.AppointmentDraftNormalizer.NormalizedDraft;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.ParticipantSnapshot;
import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingApprovalService.ApprovalView;
import com.bodeul.core.consent.GuardianBookingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class GuardianBookingService {
    private final GuardianBookingApprovalService approvals;
    private final AppointmentRepository appointments;
    private final AppUserProfileRepository profiles;
    private final Clock clock;
    private final Supplier<String> publicCodes;

    @Autowired
    GuardianBookingService(GuardianBookingApprovalService approvals, AppointmentRepository appointments,
            AppUserProfileRepository profiles, AppointmentPublicCodeGenerator publicCodes) {
        this(approvals, appointments, profiles, Clock.systemUTC(), publicCodes::nextCode);
    }

    GuardianBookingService(GuardianBookingApprovalService approvals, AppointmentRepository appointments,
            AppUserProfileRepository profiles, Clock clock, Supplier<String> publicCodes) {
        this.approvals = approvals;
        this.appointments = appointments;
        this.profiles = profiles;
        this.clock = clock;
        this.publicCodes = publicCodes;
    }

    @Transactional(readOnly = true)
    public CreateAppointmentCommand preview(AppUser patient, UUID guardianId, CreateAppointmentCommand command) {
        requireRole(patient, AppUserRole.PATIENT);
        approvals.requireParticipants(patient, patient.id(), guardianId);
        NormalizedDraft draft = normalize(command);
        requireNewRequest(command, draft);
        requirePatientProfile(patient.id(), draft);
        requireProfile(guardianId, AppUserRole.GUARDIAN);
        return new CreateAppointmentCommand(command.clientRequestId(), draft.toDraft(),
                AppointmentPricePolicy.VERSION, AppointmentPricePolicy.BASE_PRICE);
    }

    @Transactional
    public ApprovalView grant(AppUser patient, UUID guardianId, CreateAppointmentCommand command,
            long expectedVersion, boolean adultPatientConfirmed) {
        requireRole(patient, AppUserRole.PATIENT);
        approvals.requireParticipants(patient, patient.id(), guardianId);
        NormalizedDraft draft = normalize(command);
        requireNewRequest(command, draft);
        requirePatientProfile(patient.id(), draft);
        requireProfile(guardianId, AppUserRole.GUARDIAN);
        if (appointments.findByClientRequestId(guardianId, command.clientRequestId()).isPresent()) {
            throw GuardianBookingException.conflict();
        }
        ApprovalView granted = approvals.grant(patient, guardianId, command.clientRequestId(), expectedVersion,
                adultPatientConfirmed, fingerprint(guardianId, command, draft), draft.appointmentAt());
        // 승인 UPDATE가 기다리는 동안 예약이 먼저 커밋됐다면 승인·감사 갱신도 함께 되돌린다.
        if (appointments.findByClientRequestId(guardianId, command.clientRequestId()).isPresent()) {
            throw GuardianBookingException.conflict();
        }
        return granted;
    }

    @Transactional
    public Receipt create(AppUser guardian, UUID patientId, UUID grantId, long approvalVersion,
            CreateAppointmentCommand command) {
        requireRole(guardian, AppUserRole.GUARDIAN);
        approvals.requireParticipants(guardian, patientId, guardian.id());
        NormalizedDraft draft = normalize(command);
        String fingerprint = fingerprint(guardian.id(), command, draft);
        var existing = appointments.findByClientRequestId(guardian.id(), command.clientRequestId());
        if (existing.isPresent()) return retryReceipt(existing.get(), patientId, guardian.id(), fingerprint);

        requireNewRequest(command, draft);
        ParticipantSnapshot patient = requirePatientProfile(patientId, draft);
        ParticipantSnapshot guardianProfile = requireProfile(guardian.id(), AppUserRole.GUARDIAN);
        // 이 호출이 얻은 승인 행 잠금은 아래 INSERT와 트랜잭션 종료까지 유지된다.
        approvals.lockForCreation(guardian, patientId, command.clientRequestId(), grantId, approvalVersion, fingerprint);
        var mutation = mutation(patientId, guardian.id(), command, draft, patient, guardianProfile);
        for (int attempt = 0; attempt < 5; attempt++) {
            var inserted = appointments.insert(mutation, publicCodes.get(), fingerprint);
            if (inserted.isPresent()) return receipt(inserted.get());
            var retried = appointments.findByClientRequestId(guardian.id(), command.clientRequestId());
            if (retried.isPresent()) return retryReceipt(retried.get(), patientId, guardian.id(), fingerprint);
        }
        throw AppointmentException.publicCodeUnavailable();
    }

    private NormalizedDraft normalize(CreateAppointmentCommand command) {
        approvals.requireEnabled();
        if (command == null || command.clientRequestId() == null) {
            throw GuardianBookingException.invalid("예약 요청 ID가 필요합니다.");
        }
        return AppointmentDraftNormalizer.normalize(command.draft());
    }

    private void requireNewRequest(CreateAppointmentCommand command, NormalizedDraft draft) {
        AppointmentPricePolicy.requireConfirmation(command);
        if (!draft.appointmentAt().isAfter(clock.instant())) {
            throw GuardianBookingException.invalid("예약 일시는 현재보다 이후여야 합니다.");
        }
        if (!"NONE".equals(draft.couponCode())) {
            throw GuardianBookingException.invalid("현재 신규 예약에는 쿠폰을 적용할 수 없습니다.");
        }
    }

    private void requireRole(AppUser actor, AppUserRole role) {
        approvals.requireEnabled();
        if (actor == null || actor.role() != role) throw GuardianBookingException.denied();
    }

    private ParticipantSnapshot requirePatientProfile(UUID patientId, NormalizedDraft draft) {
        ParticipantSnapshot profile = requireProfile(patientId, AppUserRole.PATIENT);
        if (!profile.name().equals(draft.linkedParticipantName())
                || !profile.phone().equals(draft.linkedParticipantPhone())
                || !draft.linkedParticipantEmail().isEmpty() && !profile.email().equals(draft.linkedParticipantEmail())) {
            throw GuardianBookingException.invalid("환자 본인의 이름과 연락처를 확인해 주세요.");
        }
        return profile;
    }

    private ParticipantSnapshot requireProfile(UUID id, AppUserRole role) {
        var profile = profiles.findById(id).filter(value -> value.role() == role)
                .orElseThrow(GuardianBookingException::denied);
        String name = AppointmentDraftNormalizer.normalizeName(profile.name());
        String phone = AppointmentDraftNormalizer.normalizePhone(profile.phone());
        String email = AppointmentDraftNormalizer.normalizeEmail(profile.email());
        if (name.isEmpty() || phone.isEmpty()) throw AppointmentException.profileNotReady();
        return new ParticipantSnapshot(name, phone, email);
    }

    private String fingerprint(UUID guardianId, CreateAppointmentCommand command, NormalizedDraft draft) {
        return AppointmentCreateFingerprint.forGuardianApproval(AppointmentDraftNormalizer.toFingerprintRequest(
                new AppUser(guardianId, "", AppUserRole.GUARDIAN), command.clientRequestId(), draft),
                command.pricePolicyVersion(), command.expectedFinalPrice());
    }

    private Receipt retryReceipt(AppointmentRecord record, UUID patientId, UUID guardianId, String fingerprint) {
        if (!patientId.equals(record.patientUserId()) || !guardianId.equals(record.guardianUserId())
                || !guardianId.equals(record.requesterUserId()) || record.requesterRole() != AppUserRole.GUARDIAN
                || !appointments.findCreateRequestFingerprint(record.id()).filter(fingerprint::equals).isPresent()) {
            throw AppointmentException.idempotencyConflict();
        }
        return receipt(record);
    }

    private Receipt receipt(AppointmentRecord record) {
        // 생성 승인은 정보 열람 동의가 아니므로 상태·병원·개인정보 DTO를 반환하지 않는다.
        return new Receipt(record.id(), record.publicCode());
    }

    private AppointmentRepository.AppointmentMutation mutation(UUID patientId, UUID guardianId,
            CreateAppointmentCommand command, NormalizedDraft draft,
            ParticipantSnapshot patient, ParticipantSnapshot guardian) {
        return new AppointmentRepository.AppointmentMutation(command.clientRequestId(), patientId, guardianId,
                guardianId, AppUserRole.GUARDIAN, patient, guardian, guardian,
                draft.hospitalName(), draft.departmentName(), draft.hospitalLatitude(), draft.hospitalLongitude(),
                draft.appointmentAt(), draft.appointmentAt().toEpochMilli(),
                draft.appointmentAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate().toString(),
                draft.meetingPlace(), draft.specialNotes(), draft.patientConditionSummary(), draft.medicationSummary(),
                draft.mobilitySupportCode(), draft.tripTypeCode(), draft.managerGenderPreferenceCode(),
                AppointmentPricePolicy.BASE_PRICE, 0, 0, AppointmentPricePolicy.BASE_PRICE,
                draft.paymentMethodCode(), draft.couponCode(), switch (draft.paymentMethodCode()) {
                    case "ON_SITE" -> "DEFERRED";
                    case "BANK_TRANSFER" -> "AWAITING_DEPOSIT";
                    default -> "PENDING";
                });
    }

    record Receipt(UUID appointmentId, String publicCode) { }
}
