package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.bodeul.core.appointment.AppUserProfileRepository.AppUserProfile;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentFollowUpRecord;
import com.bodeul.core.appointment.AppointmentRepository.ParticipantSnapshot;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianSharingPolicy.InformationScope;
import com.bodeul.core.consent.GuardianSharingConsentAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultAppointmentService implements AppointmentService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter APPOINTMENT_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm", Locale.KOREA)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final int BASE_PRICE = 40_000;
    private static final String PRICE_POLICY_VERSION = "mvp-fixed-40000-v1";
    private static final int PUBLIC_CODE_MAX_ATTEMPTS = 5;
    private static final Set<String> REVIEW_RATINGS = Set.of(
            "excellent", "good", "ok", "disappointing", "need_help");
    private static final Set<String> SETTLEMENT_STATUSES = Set.of(
            "CONFIRMED", "NEEDS_HELP", "OVERTIME_REVIEW", "REFUND_REVIEW");

    private final AppointmentRepository appointmentRepository;
    private final AppUserProfileRepository profileRepository;
    private final GuardianSharingConsentAccess consentAccess;
    private final Clock clock;
    private final Supplier<String> publicCodeSupplier;

    @Autowired
    DefaultAppointmentService(
            AppointmentRepository appointmentRepository,
            AppUserProfileRepository profileRepository,
            GuardianSharingConsentAccess consentAccess,
            AppointmentPublicCodeGenerator publicCodeGenerator) {
        this(
                appointmentRepository,
                profileRepository,
                consentAccess,
                Clock.systemUTC(),
                publicCodeGenerator::nextCode);
    }

    DefaultAppointmentService(
            AppointmentRepository appointmentRepository,
            AppUserProfileRepository profileRepository,
            GuardianSharingConsentAccess consentAccess,
            Clock clock) {
        this(
                appointmentRepository,
                profileRepository,
                consentAccess,
                clock,
                new AppointmentPublicCodeGenerator()::nextCode);
    }

    DefaultAppointmentService(
            AppointmentRepository appointmentRepository,
            AppUserProfileRepository profileRepository,
            GuardianSharingConsentAccess consentAccess,
            Clock clock,
            Supplier<String> publicCodeSupplier) {
        this.appointmentRepository = appointmentRepository;
        this.profileRepository = profileRepository;
        this.consentAccess = consentAccess;
        this.clock = clock;
        this.publicCodeSupplier = publicCodeSupplier;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> getMyAppointments(AppUserRepository.AppUser appUser) {
        requireReadableRole(appUser);
        return appointmentRepository.findAllForParticipant(appUser.id(), appUser.role())
                .stream()
                .filter(appointment -> guardianCanReadAppointment(appUser, appointment))
                .map(appointment -> toViewForReader(appUser, appointment))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentView getAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        requireReadableRole(appUser);
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireReader(appUser, appointment);
        return toViewForReader(appUser, appointment);
    }

    @Override
    @Transactional
    public AppointmentView createAppointment(
            AppUserRepository.AppUser appUser,
            CreateAppointmentCommand command) {
        requireSupportedRole(appUser);
        if (appUser.role() == AppUserRole.GUARDIAN) {
            throw AppointmentException.guardianCreationNotSupported();
        }
        if (command == null || command.clientRequestId() == null) {
            throw AppointmentException.invalidRequest("중복 생성 방지용 clientRequestId가 필요합니다.");
        }

        NormalizedDraft draft = normalizeDraft(command.draft());
        String createRequestFingerprint = AppointmentCreateFingerprint.from(
                toCreateFingerprintRequest(
                        appUser,
                        command.clientRequestId(),
                        draft));
        var existing = appointmentRepository.findByClientRequestId(
                appUser.id(),
                command.clientRequestId());
        if (existing.isPresent()) {
            requireIdempotentCreateFingerprintMatches(
                    existing.get().id(),
                    createRequestFingerprint);
            return toViewForReader(appUser, existing.get());
        }

        // 과거 요청의 재시도는 저장된 견적을 반환하고, 새 예약만 현재 가격 계약을 요구한다.
        if (!PRICE_POLICY_VERSION.equals(command.pricePolicyVersion())
                || command.expectedFinalPrice() == null
                || command.expectedFinalPrice() != BASE_PRICE) {
            throw AppointmentException.priceConfirmationRequired();
        }
        requireFutureAppointment(draft);
        if (!"NONE".equals(draft.couponCode())) {
            throw AppointmentException.invalidRequest("현재 신규 예약에는 쿠폰을 적용할 수 없습니다.");
        }
        ParticipantPair participants = resolveParticipants(appUser, draft);
        Price price = new Price(BASE_PRICE, 0, 0, BASE_PRICE);
        AppointmentMutation mutation = toMutation(
                command.clientRequestId(),
                appUser.id(),
                appUser.role(),
                participants,
                draft,
                price);

        for (int attempt = 0; attempt < PUBLIC_CODE_MAX_ATTEMPTS; attempt++) {
            var inserted = appointmentRepository.insert(
                    mutation,
                    publicCodeSupplier.get(),
                    createRequestFingerprint);
            if (inserted.isPresent()) {
                return toViewForReader(appUser, inserted.get());
            }

            var idempotentResult = appointmentRepository.findByClientRequestId(
                    appUser.id(), command.clientRequestId());
            if (idempotentResult.isPresent()) {
                requireIdempotentCreateFingerprintMatches(
                        idempotentResult.get().id(),
                        createRequestFingerprint);
                return toViewForReader(appUser, idempotentResult.get());
            }
        }
        throw AppointmentException.publicCodeUnavailable();
    }

    @Override
    @Transactional
    public AppointmentView updateAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentCommand command) {
        requireSupportedRole(appUser);
        if (appUser.role() == AppUserRole.GUARDIAN) {
            throw AppointmentException.guardianMutationNotSupported();
        }
        if (command == null || command.version() < 0) {
            throw AppointmentException.invalidRequest("예약 버전이 필요합니다.");
        }

        AppointmentRecord existing = findAppointment(appointmentId);
        requireParticipant(appUser, existing);
        if (!"REQUESTED".equals(existing.status())) {
            throw AppointmentException.stateConflict();
        }
        if (existing.version() != command.version()) {
            throw AppointmentException.versionConflict();
        }

        NormalizedDraft draft = normalizeDraft(command.draft());
        requireFutureAppointment(draft);
        ParticipantPair participants = resolveParticipants(appUser, draft);
        requireRequesterLink(existing, participants);
        // 수정은 기존 견적을 보존한다. 새 정책으로 과거 원장·입금액을 재계산하지 않는다.
        Price price = new Price(existing.basePrice(), existing.optionSurchargePrice(),
                existing.couponDiscountPrice(), existing.finalPrice());
        if ("BANK_TRANSFER".equals(existing.paymentMethodCode())
                || "BANK_TRANSFER".equals(draft.paymentMethodCode())) {
            if (!existing.paymentMethodCode().equals(draft.paymentMethodCode())
                    || !existing.mobilitySupportCode().equals(draft.mobilitySupportCode())
                    || !existing.tripTypeCode().equals(draft.tripTypeCode())
                    || !existing.couponCode().equals(draft.couponCode())) {
                throw AppointmentException.bankTransferTermsConflict();
            }
        }
        if (!existing.couponCode().equals(draft.couponCode())) {
            throw AppointmentException.invalidRequest("접수된 예약의 쿠폰은 변경할 수 없습니다.");
        }
        ParticipantSnapshot requester = existing.requesterRole() == AppUserRole.PATIENT
                ? participants.patient()
                : participants.guardian();
        AppointmentMutation mutation = toMutation(
                null,
                existing.requesterUserId(),
                existing.requesterRole(),
                participants,
                draft,
                price,
                requester);

        return appointmentRepository.update(appointmentId, command.version(), mutation)
                .map(appointment -> toViewForReader(appUser, appointment))
                .orElseThrow(AppointmentException::versionConflict);
    }

    @Override
    @Transactional
    public AppointmentView cancelAppointment(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            long version) {
        requireSupportedRole(appUser);
        if (appUser.role() == AppUserRole.GUARDIAN) {
            throw AppointmentException.guardianMutationNotSupported();
        }
        if (version < 0) {
            throw AppointmentException.invalidRequest("예약 버전이 필요합니다.");
        }

        AppointmentRecord existing = findAppointment(appointmentId);
        requireParticipant(appUser, existing);
        if (!"REQUESTED".equals(existing.status()) && !"MATCHED".equals(existing.status())) {
            throw AppointmentException.stateConflict();
        }
        if (existing.version() != version) {
            throw AppointmentException.versionConflict();
        }

        AppointmentRecord canceled = appointmentRepository.cancel(appointmentId, version)
                .orElseThrow(AppointmentException::versionConflict);
        if ("MATCHED".equals(existing.status())
                && !appointmentRepository.cancelActiveSession(appointmentId)) {
            throw AppointmentException.stateConflict();
        }
        Instant cancellationBoundary = appointmentRepository.findCancellationBoundary(appointmentId)
                .orElseThrow(AppointmentException::stateConflict);
        consentAccess.finalizeExpiryAfterCareBoundary(appointmentId, cancellationBoundary);
        return toViewForReader(appUser, canceled);
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentFollowUpView getAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId) {
        requireReadableRole(appUser);
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireParticipantRelationshipOrAssignedManager(appUser, appointment);
        requireGuardianScope(appUser, appointment, InformationScope.REPORT);
        return appointmentRepository.findFollowUpByAppointmentId(appointmentId)
                .map(this::toFollowUpView)
                .orElseGet(() -> emptyFollowUpView(appointmentId));
    }

    @Override
    @Transactional
    public AppointmentFollowUpView updateAppointmentFollowUp(
            AppUserRepository.AppUser appUser,
            UUID appointmentId,
            UpdateAppointmentFollowUpCommand command) {
        requireSupportedRole(appUser);
        if (appUser.role() == AppUserRole.GUARDIAN) {
            throw AppointmentException.guardianMutationNotSupported();
        }
        if (command == null || command.version() < 0) {
            throw AppointmentException.invalidRequest("후속 기록 버전이 필요합니다.");
        }
        AppointmentRecord appointment = findAppointment(appointmentId);
        requireParticipantRelationship(appUser, appointment);
        requireGuardianScope(appUser, appointment, InformationScope.REPORT);
        if (!"COMPLETED".equals(appointment.status())) {
            throw AppointmentException.stateConflict();
        }

        String reviewRatingCode = normalizeOptionalCode(
                command.reviewRatingCode(), REVIEW_RATINGS, false, "후기 만족도");
        String settlementStatus = normalizeOptionalCode(
                command.settlementStatus(), SETTLEMENT_STATUSES, true, "정산 확인 상태");
        if (!normalizeText(command.supportEscalationStatus()).isEmpty()) {
            throw AppointmentException.supportEscalationNotSupported();
        }
        if (reviewRatingCode == null
                && settlementStatus == null) {
            throw AppointmentException.invalidRequest("저장할 후속 기록이 필요합니다.");
        }
        String settlementNote = settlementStatus == null
                ? null
                : limitText(normalizeText(command.settlementNote()), "정산 확인 메모", 2_000);

        var existing = appointmentRepository.findFollowUpByAppointmentId(appointmentId);
        long currentVersion = existing.map(AppointmentFollowUpRecord::version).orElse(0L);
        if (currentVersion != command.version()) {
            throw AppointmentException.versionConflict();
        }
        AppointmentFollowUpMutation mutation = new AppointmentFollowUpMutation(
                appointmentId,
                appUser.id(),
                command.version(),
                reviewRatingCode,
                settlementStatus,
                settlementNote);
        return (existing.isEmpty()
                        ? appointmentRepository.insertFollowUp(mutation)
                        : appointmentRepository.updateFollowUp(mutation))
                .map(this::toFollowUpView)
                .orElseThrow(AppointmentException::versionConflict);
    }

    private AppointmentRecord findAppointment(UUID appointmentId) {
        if (appointmentId == null) {
            throw AppointmentException.invalidRequest("예약 ID가 필요합니다.");
        }
        return appointmentRepository.findById(appointmentId)
                .orElseThrow(AppointmentException::notFound);
    }

    private void requireSupportedRole(AppUserRepository.AppUser appUser) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT && appUser.role() != AppUserRole.GUARDIAN)) {
            throw AppointmentException.roleNotSupported();
        }
    }

    private void requireReadableRole(AppUserRepository.AppUser appUser) {
        if (appUser == null
                || (appUser.role() != AppUserRole.PATIENT
                && appUser.role() != AppUserRole.GUARDIAN
                && appUser.role() != AppUserRole.MANAGER)) {
            throw AppointmentException.readRoleNotSupported();
        }
    }

    private void requireReader(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        if (appUser.role() == AppUserRole.MANAGER) {
            if (!appUser.id().equals(appointment.managerUserId())) {
                throw AppointmentException.permissionDenied();
            }
            return;
        }
        requireParticipant(appUser, appointment);
    }

    private void requireParticipant(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        requireParticipantRelationship(appUser, appointment);
        if (!guardianCanReadAppointment(appUser, appointment)) {
            throw AppointmentException.permissionDenied();
        }
    }

    private void requireParticipantRelationshipOrAssignedManager(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        if (appUser.role() == AppUserRole.MANAGER) {
            if (!appUser.id().equals(appointment.managerUserId())) {
                throw AppointmentException.permissionDenied();
            }
            return;
        }
        requireParticipantRelationship(appUser, appointment);
    }

    private void requireParticipantRelationship(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        UUID participantId = appUser.role() == AppUserRole.PATIENT
                ? appointment.patientUserId()
                : appointment.guardianUserId();
        if (!appUser.id().equals(participantId)) {
            throw AppointmentException.permissionDenied();
        }
    }

    private boolean guardianCanReadAppointment(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        return appUser.role() != AppUserRole.GUARDIAN
                || consentAccess.isAllowed(
                appUser,
                appointment.id(),
                appointment.patientUserId(),
                appointment.guardianUserId(),
                InformationScope.APPOINTMENT);
    }

    private void requireGuardianScope(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment,
            InformationScope scope) {
        if (appUser.role() == AppUserRole.GUARDIAN
                && !consentAccess.isAllowed(
                appUser,
                appointment.id(),
                appointment.patientUserId(),
                appointment.guardianUserId(),
                scope)) {
            throw AppointmentException.permissionDenied();
        }
    }

    private void requireRequesterLink(
            AppointmentRecord existing,
            ParticipantPair participants) {
        UUID requesterParticipantId = existing.requesterRole() == AppUserRole.PATIENT
                ? participants.patientUserId()
                : participants.guardianUserId();
        if (!existing.requesterUserId().equals(requesterParticipantId)) {
            throw AppointmentException.requesterLinkConflict();
        }
    }

    private ParticipantPair resolveParticipants(
            AppUserRepository.AppUser appUser,
            NormalizedDraft draft) {
        AppUserProfile currentProfile = profileRepository.findById(appUser.id())
                .filter(profile -> profile.role() == appUser.role())
                .orElseThrow(AppointmentException::profileNotReady);
        ParticipantSnapshot currentSnapshot = requireCompleteProfile(currentProfile);

        AppUserRole linkedRole = appUser.role() == AppUserRole.PATIENT
                ? AppUserRole.GUARDIAN
                : AppUserRole.PATIENT;
        var linkedProfile = resolveLinkedProfile(
                linkedRole,
                draft.linkedParticipantEmail(),
                draft.linkedParticipantPhone());
        ParticipantSnapshot linkedSnapshot = linkedProfile
                .map(this::toSnapshot)
                .orElseGet(() -> new ParticipantSnapshot(
                        draft.linkedParticipantName(),
                        draft.linkedParticipantPhone(),
                        draft.linkedParticipantEmail()));

        if (appUser.role() == AppUserRole.PATIENT) {
            return new ParticipantPair(
                    appUser.id(),
                    linkedProfile.map(AppUserProfile::id).orElse(null),
                    currentSnapshot,
                    linkedSnapshot);
        }
        return new ParticipantPair(
                linkedProfile.map(AppUserProfile::id).orElse(null),
                appUser.id(),
                linkedSnapshot,
                currentSnapshot);
    }

    private java.util.Optional<AppUserProfile> resolveLinkedProfile(
            AppUserRole role,
            String email,
            String phone) {
        Map<UUID, AppUserProfile> candidates = new LinkedHashMap<>();
        if (!email.isEmpty()) {
            for (AppUserProfile profile : profileRepository.findByEmail(role, email)) {
                candidates.put(profile.id(), profile);
            }
        }
        if (!phone.isEmpty()) {
            for (AppUserProfile profile : profileRepository.findByPhone(role, phone)) {
                candidates.put(profile.id(), profile);
            }
        }
        if (candidates.size() > 1) {
            throw AppointmentException.participantAmbiguous();
        }
        return candidates.values().stream().findFirst();
    }

    private ParticipantSnapshot requireCompleteProfile(AppUserProfile profile) {
        ParticipantSnapshot snapshot = toSnapshot(profile);
        if (snapshot.name().isEmpty()
                || (snapshot.email().isEmpty() && snapshot.phone().isEmpty())) {
            throw AppointmentException.profileNotReady();
        }
        return snapshot;
    }

    private ParticipantSnapshot toSnapshot(AppUserProfile profile) {
        return new ParticipantSnapshot(
                normalizeName(profile.name()),
                normalizePhone(profile.phone()),
                normalizeEmail(profile.email()));
    }

    private NormalizedDraft normalizeDraft(AppointmentDraft draft) {
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

    private void requireFutureAppointment(NormalizedDraft draft) {
        if (!draft.appointmentAt().isAfter(clock.instant())) {
            throw AppointmentException.invalidRequest("예약 일시는 현재보다 이후여야 합니다.");
        }
    }

    private void requireIdempotentCreateFingerprintMatches(
            UUID appointmentId,
            String createRequestFingerprint) {
        String storedFingerprint = appointmentRepository
                .findCreateRequestFingerprint(appointmentId)
                .orElseThrow(AppointmentException::idempotencyConflict);
        if (!storedFingerprint.equals(createRequestFingerprint)) {
            throw AppointmentException.idempotencyConflict();
        }
    }

    private AppointmentCreateFingerprint.CreateRequest toCreateFingerprintRequest(
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

    private AppointmentMutation toMutation(
            UUID clientRequestId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantPair participants,
            NormalizedDraft draft,
            Price price) {
        ParticipantSnapshot requester = requesterRole == AppUserRole.PATIENT
                ? participants.patient()
                : participants.guardian();
        return toMutation(
                clientRequestId,
                requesterUserId,
                requesterRole,
                participants,
                draft,
                price,
                requester);
    }

    private AppointmentMutation toMutation(
            UUID clientRequestId,
            UUID requesterUserId,
            AppUserRole requesterRole,
            ParticipantPair participants,
            NormalizedDraft draft,
            Price price,
            ParticipantSnapshot requester) {
        return new AppointmentMutation(
                clientRequestId,
                participants.patientUserId(),
                participants.guardianUserId(),
                requesterUserId,
                requesterRole,
                participants.patient(),
                participants.guardian(),
                requester,
                draft.hospitalName(),
                draft.departmentName(),
                draft.hospitalLatitude(),
                draft.hospitalLongitude(),
                draft.appointmentAt(),
                draft.appointmentAt().toEpochMilli(),
                draft.appointmentAt().atZone(SEOUL).toLocalDate().toString(),
                draft.meetingPlace(),
                draft.specialNotes(),
                draft.patientConditionSummary(),
                draft.medicationSummary(),
                draft.mobilitySupportCode(),
                draft.tripTypeCode(),
                draft.managerGenderPreferenceCode(),
                price.basePrice(),
                price.optionSurchargePrice(),
                price.couponDiscountPrice(),
                price.finalPrice(),
                draft.paymentMethodCode(),
                draft.couponCode(),
                switch (draft.paymentMethodCode()) {
                    case "ON_SITE" -> "DEFERRED";
                    case "BANK_TRANSFER" -> "AWAITING_DEPOSIT";
                    default -> "PENDING";
                });
    }

    private AppointmentView toViewForReader(
            AppUserRepository.AppUser appUser,
            AppointmentRecord appointment) {
        boolean guardianView = appUser.role() == AppUserRole.GUARDIAN;
        boolean managerViewAfterCareEnded = appUser.role() == AppUserRole.MANAGER
                && appointmentRepository.hasCareEnded(appointment.id());
        boolean redactCareDetails = guardianView || managerViewAfterCareEnded;
        boolean canReadPublicCode = appUser.id().equals(appointment.requesterUserId())
                || (appUser.role() == AppUserRole.MANAGER
                && appUser.id().equals(appointment.managerUserId()));
        AppUserProfile manager = guardianView || appointment.managerUserId() == null
                ? null
                : profileRepository.findById(appointment.managerUserId()).orElse(null);
        return new AppointmentView(
                appointment.id(),
                guardianView ? "" : nullToEmpty(appointment.firestoreId()),
                canReadPublicCode ? nullToEmpty(appointment.publicCode()) : "",
                guardianView ? null : appointment.patientUserId(),
                guardianView ? null : appointment.guardianUserId(),
                guardianView ? null : appointment.managerUserId(),
                guardianView || manager == null ? "" : manager.name(),
                guardianView || manager == null ? "" : manager.phone(),
                guardianView || manager == null ? "" : manager.email(),
                guardianView ? "" : appointment.patient().name(),
                guardianView ? "" : appointment.patient().phone(),
                guardianView ? "" : appointment.patient().email(),
                guardianView ? "" : appointment.guardian().name(),
                guardianView ? "" : appointment.guardian().phone(),
                guardianView ? "" : appointment.guardian().email(),
                appointment.hospitalName(),
                appointment.departmentName(),
                appointment.hospitalLatitude(),
                appointment.hospitalLongitude(),
                APPOINTMENT_FORMATTER.format(appointment.appointmentAt().atZone(SEOUL)),
                guardianView ? "" : appointment.meetingPlace(),
                redactCareDetails ? "" : appointment.specialNotes(),
                redactCareDetails ? "" : appointment.patientConditionSummary(),
                redactCareDetails ? "" : appointment.medicationSummary(),
                redactCareDetails ? "" : appointment.mobilitySupportCode(),
                guardianView ? "" : appointment.tripTypeCode(),
                guardianView ? "" : appointment.managerGenderPreferenceCode(),
                appointment.status(),
                guardianView ? 0 : appointment.basePrice(),
                guardianView ? 0 : appointment.optionSurchargePrice(),
                guardianView ? 0 : appointment.couponDiscountPrice(),
                guardianView ? 0 : appointment.finalPrice(),
                guardianView ? "" : appointment.paymentMethodCode(),
                guardianView ? "" : appointment.couponCode(),
                guardianView ? "" : appointment.paymentStatusCode(),
                guardianView ? "" : appointment.paymentApprovalCode(),
                guardianView || appointment.paymentApprovedAt() == null
                        ? ""
                        : appointment.paymentApprovedAt().toString(),
                guardianView ? "" : appointment.paymentProviderLabel(),
                appointment.version());
    }

    private AppointmentFollowUpView toFollowUpView(AppointmentFollowUpRecord followUp) {
        return new AppointmentFollowUpView(
                followUp.appointmentId(),
                nullToEmpty(followUp.reviewRatingCode()),
                instantText(followUp.reviewSavedAt()),
                nullToEmpty(followUp.settlementStatus()),
                nullToEmpty(followUp.settlementNote()),
                instantText(followUp.settlementSavedAt()),
                nullToEmpty(followUp.supportEscalationStatus()),
                instantText(followUp.supportEscalatedAt()),
                followUp.version());
    }

    private AppointmentFollowUpView emptyFollowUpView(UUID appointmentId) {
        return new AppointmentFollowUpView(
                appointmentId, "", "", "", "", "", "", "", 0L);
    }

    private Instant parseAppointmentAt(String value) {
        try {
            return LocalDateTime.parse(value, APPOINTMENT_FORMATTER)
                    .atZone(SEOUL)
                    .toInstant();
        } catch (DateTimeParseException exception) {
            throw AppointmentException.invalidRequest("예약 일시는 yyyy-MM-dd HH:mm 형식이어야 합니다.");
        }
    }

    private String requireText(String value, String label, int maxLength) {
        String normalized = normalizeText(value);
        if (normalized.isEmpty()) {
            throw AppointmentException.invalidRequest(label + "이(가) 필요합니다.");
        }
        return limitText(normalized, label, maxLength);
    }

    private String limitText(String value, String label, int maxLength) {
        if (value.length() > maxLength) {
            throw AppointmentException.invalidRequest(label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return value;
    }

    private <T extends Enum<T>> String requireCode(String value, String label, T[] values) {
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

    private String normalizeOptionalCode(
            String value,
            Set<String> allowed,
            boolean uppercase,
            String label) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeText(value);
        normalized = uppercase
                ? normalized.toUpperCase(Locale.ROOT)
                : normalized.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw AppointmentException.invalidRequest(label + " 값을 확인해 주세요.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeName(String value) {
        return normalizeText(value).replaceAll("\\s+", " ");
    }

    private String normalizeEmail(String value) {
        return normalizeText(value).toLowerCase(Locale.ROOT);
    }

    private String normalizePhone(String value) {
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

    private boolean isValidPhone(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        return digits.startsWith("0") && digits.length() >= 9 && digits.length() <= 11;
    }

    private boolean isValidEmail(String value) {
        int at = value.indexOf('@');
        int dot = value.lastIndexOf('.');
        return at > 0 && dot > at + 1 && dot < value.length() - 1 && value.indexOf(' ') < 0;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String instantText(Instant value) {
        return value == null ? "" : value.toString();
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

    private record NormalizedDraft(
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

    private record ParticipantPair(
            UUID patientUserId,
            UUID guardianUserId,
            ParticipantSnapshot patient,
            ParticipantSnapshot guardian) {
    }

    private record Price(
            int basePrice,
            int optionSurchargePrice,
            int couponDiscountPrice,
            int finalPrice) {
    }
}
