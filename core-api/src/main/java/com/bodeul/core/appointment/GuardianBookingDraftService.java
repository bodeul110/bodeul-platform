package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.appointment.GuardianBookingDraftRepository.DraftSummary;
import com.bodeul.core.appointment.GuardianBookingDraftRepository.StoredDraft;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingApprovalService.ApprovalView;
import com.bodeul.core.consent.GuardianBookingApprovalService.ParticipantPair;
import com.bodeul.core.consent.GuardianBookingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Profile("database")
class GuardianBookingDraftService {
    private final GuardianBookingDraftRepository drafts;
    private final GuardianBookingService booking;
    private final GuardianBookingApprovalService approvals;
    private final Clock clock;

    @Autowired
    GuardianBookingDraftService(GuardianBookingDraftRepository drafts, GuardianBookingService booking,
            GuardianBookingApprovalService approvals) {
        this(drafts, booking, approvals, Clock.systemUTC());
    }

    GuardianBookingDraftService(GuardianBookingDraftRepository drafts, GuardianBookingService booking,
            GuardianBookingApprovalService approvals, Clock clock) {
        this.drafts = drafts;
        this.booking = booking;
        this.approvals = approvals;
        this.clock = clock;
    }

    @Transactional
    public DraftSummary submit(AppUser guardian, UUID patientId, CreateAppointmentCommand command) {
        requireActor(guardian);
        if (guardian.role() != AppUserRole.GUARDIAN) throw GuardianBookingException.denied();
        var review = booking.reviewDraft(guardian, patientId, guardian.id(), command);
        CreateAppointmentCommand normalized = review.appointment();
        String fingerprint = fingerprint(guardian.id(), normalized);
        drafts.lockGuardian(guardian.id());
        var existing = drafts.findForActor(guardian.id(), normalized.clientRequestId(), guardian.id());
        if (existing.isPresent()) {
            StoredDraft stored = existing.get();
            if (!patientId.equals(stored.patientUserId()) || !fingerprint.equals(stored.fingerprint())) {
                throw GuardianBookingException.draftConflict();
            }
            requireUnexpired(stored);
            return stored.summary();
        }
        if (booking.receiptForRequest(guardian, normalized.clientRequestId()).isPresent()) {
            throw GuardianBookingException.draftConflict();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (drafts.countActive(guardian.id(), now) >= 20) throw GuardianBookingException.draftLimitExceeded();
        Instant appointmentAt = AppointmentDraftNormalizer.normalize(normalized.draft()).appointmentAt();
        Instant expires = now.plus(Duration.ofHours(24));
        if (appointmentAt.isBefore(expires)) expires = appointmentAt;
        if (!expires.isAfter(now)) throw GuardianBookingException.draftNotFound();
        StoredDraft stored = new StoredDraft(patientId, guardian.id(), normalized, fingerprint, now, expires);
        drafts.insert(stored);
        return stored.summary();
    }

    @Transactional(readOnly = true)
    public DraftView get(AppUser actor, UUID guardianId, UUID requestId) {
        StoredDraft stored = load(actor, guardianId, requestId);
        var review = booking.reviewDraft(actor, stored.patientUserId(), guardianId, stored.appointment());
        return new DraftView(stored.summary(), review.appointment(), review.patient(), review.guardian());
    }

    public List<DraftSummary> list(AppUser actor) {
        requireActor(actor);
        // 동기화만 활성화된 범위도 JDBC 연결을 유지하므로 목록은 두 범위 모두 거부한다.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalTransactionStateException("예약 초안 목록은 DB 트랜잭션 밖에서 조회해야 합니다.");
        }
        List<DraftSummary> candidates = drafts.listActive(actor, clock.instant());
        var eligible = approvals.eligibleParticipants(actor, candidates.stream()
                .map(summary -> new ParticipantPair(summary.patientUserId(), summary.guardianUserId())).toList());
        return candidates.stream().filter(summary -> eligible.contains(
                new ParticipantPair(summary.patientUserId(), summary.guardianUserId()))).toList();
    }

    @Transactional
    public ApprovalView grant(AppUser patient, UUID guardianId, UUID requestId, long version, boolean adultConfirmed) {
        requireActor(patient);
        if (patient.role() != AppUserRole.PATIENT) throw GuardianBookingException.denied();
        StoredDraft stored = load(patient, guardianId, requestId);
        return booking.grant(patient, guardianId, stored.appointment(), version, adultConfirmed);
    }

    @Transactional
    public GuardianBookingService.Receipt create(AppUser guardian, UUID guardianId, UUID requestId,
            UUID grantId, long approvalVersion) {
        requireActor(guardian);
        if (guardian.role() != AppUserRole.GUARDIAN || !guardian.id().equals(guardianId)) {
            throw GuardianBookingException.denied();
        }
        // 접수 응답 유실 후에는 원문이 만료·파기되어도 새 예약 대신 최소 접수증만 복구한다.
        var receipt = booking.receiptForRequest(guardian, requestId);
        if (receipt.isPresent()) return receipt.get();
        StoredDraft stored = load(guardian, guardianId, requestId);
        return booking.create(guardian, stored.patientUserId(), grantId, approvalVersion, stored.appointment());
    }

    private StoredDraft load(AppUser actor, UUID guardianId, UUID requestId) {
        requireActor(actor);
        StoredDraft stored = drafts.findForActor(guardianId, requestId, actor.id())
                .orElseThrow(GuardianBookingException::draftNotFound);
        requireUnexpired(stored);
        approvals.requireParticipants(actor, stored.patientUserId(), stored.guardianUserId());
        if (!stored.fingerprint().equals(fingerprint(guardianId, stored.appointment()))) {
            throw GuardianBookingException.unavailable();
        }
        return stored;
    }

    private void requireActor(AppUser actor) {
        approvals.requireEnabled();
        if (actor == null || (actor.role() != AppUserRole.PATIENT && actor.role() != AppUserRole.GUARDIAN)) {
            throw GuardianBookingException.denied();
        }
    }

    private void requireUnexpired(StoredDraft draft) {
        if (!draft.expiresAt().isAfter(clock.instant())) throw GuardianBookingException.draftNotFound();
    }

    private String fingerprint(UUID guardianId, CreateAppointmentCommand command) {
        return GuardianBookingService.fingerprint(guardianId, command, AppointmentDraftNormalizer.normalize(command.draft()));
    }

    record DraftView(DraftSummary request, CreateAppointmentCommand appointment,
                     GuardianBookingService.ParticipantDisplay patient, GuardianBookingService.ParticipantDisplay guardian) { }
}
