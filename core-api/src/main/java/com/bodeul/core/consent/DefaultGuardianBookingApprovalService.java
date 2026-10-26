package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseAccountStatus;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;
import com.bodeul.core.consent.GuardianBookingApprovalRepository.RequestKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("database")
class DefaultGuardianBookingApprovalService implements GuardianBookingApprovalService {
    static final String POLICY_VERSION = "adult-patient-guardian-booking-v1";
    private static final Duration VALIDITY = Duration.ofHours(24);
    private final GuardianBookingApprovalRepository repository;
    private final AppUserRepository users;
    private final FirebaseAccountStatus accountStatus;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    DefaultGuardianBookingApprovalService(GuardianBookingApprovalRepository repository,
            AppUserRepository users, FirebaseAccountStatus accountStatus,
            @Value("${bodeul.guardian-booking.enabled:false}") boolean enabled) {
        this(repository, users, accountStatus, enabled, Clock.systemUTC());
    }

    DefaultGuardianBookingApprovalService(GuardianBookingApprovalRepository repository,
            AppUserRepository users, FirebaseAccountStatus accountStatus, boolean enabled, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.accountStatus = accountStatus;
        this.enabled = enabled;
        this.clock = clock;
    }

    @Override
    public void requireEnabled() {
        if (!enabled) throw GuardianBookingException.disabled();
    }

    @Override
    public void requireParticipants(AppUser actor, UUID patientId, UUID guardianId) {
        requireEnabled();
        if (actor == null || patientId == null || guardianId == null || patientId.equals(guardianId)
                || !(actor.role() == AppUserRole.PATIENT && actor.id().equals(patientId)
                || actor.role() == AppUserRole.GUARDIAN && actor.id().equals(guardianId))) {
            throw GuardianBookingException.denied();
        }
        AppUser patient = currentUser(patientId, AppUserRole.PATIENT);
        AppUser guardian = currentUser(guardianId, AppUserRole.GUARDIAN);
        AppUser currentActor = actor.role() == AppUserRole.PATIENT ? patient : guardian;
        if (!currentActor.equals(actor)) throw GuardianBookingException.denied();
        try {
            if (!accountStatus.isActive(patient.firebaseUid()) || !accountStatus.isActive(guardian.firebaseUid())) {
                throw GuardianBookingException.denied();
            }
        } catch (FirebaseAccountStatus.UnavailableException exception) {
            throw GuardianBookingException.unavailable();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalView get(AppUser actor, UUID patientId, UUID guardianId, UUID requestId) {
        requireParticipants(actor, patientId, guardianId);
        return view(repository.findCurrent(key(patientId, guardianId, requestId)));
    }

    @Override
    @Transactional
    public ApprovalView grant(AppUser patient, UUID guardianId, UUID requestId, long expectedVersion,
            boolean adultPatientConfirmed, String serverFingerprint, Instant appointmentAt) {
        requirePatient(patient);
        requireParticipants(patient, patient.id(), guardianId);
        if (!adultPatientConfirmed) throw GuardianBookingException.invalid("성인 환자 본인 확인이 필요합니다.");
        Instant now = clock.instant();
        if (appointmentAt == null || !appointmentAt.isAfter(now)) {
            throw GuardianBookingException.invalid("예약 일시는 현재보다 이후여야 합니다.");
        }
        ApprovalState current = repository.findCurrent(key(patient.id(), guardianId, requestId));
        requireVersion(current, expectedVersion);
        requireMonotonicTime(current, now);
        Instant limit = now.plus(VALIDITY);
        Instant expiresAt = appointmentAt.isBefore(limit) ? appointmentAt : limit;
        ApprovalState next = AdultPatientGuardianBookingPolicy.grantByPatient(current, expectedVersion,
                patient.id(), patient.role(), true, AppUserRole.GUARDIAN,
                serverFingerprint, now, expiresAt, POLICY_VERSION);
        return view(save(next, expectedVersion));
    }

    @Override
    @Transactional
    public ApprovalView revoke(AppUser patient, UUID guardianId, UUID requestId, long expectedVersion) {
        requirePatient(patient);
        requireEnabled();
        // 비활성 보호자라도 환자는 자신의 기존 승인을 철회할 수 있다.
        AppUser currentPatient = currentUser(patient.id(), AppUserRole.PATIENT);
        if (!currentPatient.equals(patient)) throw GuardianBookingException.denied();
        try {
            if (!accountStatus.isActive(patient.firebaseUid())) throw GuardianBookingException.denied();
        } catch (FirebaseAccountStatus.UnavailableException exception) {
            throw GuardianBookingException.unavailable();
        }
        ApprovalState current = repository.lockCurrent(key(patient.id(), guardianId, requestId));
        requireVersion(current, expectedVersion);
        if (current.grant().isEmpty()) throw GuardianBookingException.conflict();
        Instant now = clock.instant();
        requireMonotonicTime(current, now);
        ApprovalState next = AdultPatientGuardianBookingPolicy.revokeByPatient(
                current, expectedVersion, patient.id(), patient.role(), now);
        return view(next == current ? current : save(next, expectedVersion));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForCreation(AppUser guardian, UUID patientId, UUID requestId,
            UUID grantId, long version, String serverFingerprint) {
        if (guardian == null || guardian.role() != AppUserRole.GUARDIAN) throw GuardianBookingException.denied();
        requireEnabled();
        ApprovalState current = repository.lockCurrent(key(patientId, guardian.id(), requestId));
        // 잠금을 기다린 시간도 고려해 실제 INSERT 직전에 참여 계정을 다시 확인한다.
        requireParticipants(guardian, patientId, guardian.id());
        // 클라이언트는 후보 ID·버전만 지정한다. 승인 내용은 잠근 DB 행에서만 가져온다.
        var candidate = current.grant().filter(value -> value.id().equals(grantId) && value.version() == version);
        if (!AdultPatientGuardianBookingPolicy.evaluateCreation(current, candidate,
                guardian.id(), guardian.role(), patientId, requestId, serverFingerprint,
                POLICY_VERSION, clock.instant()).allowed()) {
            throw GuardianBookingException.conflict();
        }
    }

    private AppUser currentUser(UUID id, AppUserRole role) {
        return users.findById(id).filter(user -> user.role() == role)
                .orElseThrow(GuardianBookingException::denied);
    }

    private void requirePatient(AppUser user) {
        if (user == null || user.role() != AppUserRole.PATIENT) throw GuardianBookingException.denied();
    }

    private RequestKey key(UUID patient, UUID guardian, UUID request) {
        if (patient == null || guardian == null || request == null || patient.equals(guardian)) {
            throw GuardianBookingException.invalid("환자·보호자·예약 요청 ID를 확인해 주세요.");
        }
        return new RequestKey(patient, guardian, request);
    }

    private void requireVersion(ApprovalState state, long expected) {
        if (expected < 0 || expected == Long.MAX_VALUE || state.version() != expected) {
            throw GuardianBookingException.conflict();
        }
    }

    private ApprovalState save(ApprovalState next, long expectedVersion) {
        try {
            return repository.save(next, expectedVersion);
        } catch (OptimisticLockingFailureException exception) {
            throw GuardianBookingException.conflict();
        }
    }

    private void requireMonotonicTime(ApprovalState state, Instant now) {
        state.grant().ifPresent(grant -> {
            Instant changedAt = grant.revokedAt() == null ? grant.grantedAt() : grant.revokedAt();
            if (now.isBefore(changedAt)) throw GuardianBookingException.conflict();
        });
    }

    private ApprovalView view(ApprovalState state) {
        Instant now = clock.instant();
        return state.grant().map(grant -> new ApprovalView(grant.id(), grant.version(), grant.policyVersion(),
                grant.grantedAt(), grant.expiresAt(), grant.revokedAt(), grant.revokedAt() == null
                && !now.isBefore(grant.grantedAt()) && now.isBefore(grant.expiresAt())
                && POLICY_VERSION.equals(grant.policyVersion())))
                .orElseGet(() -> new ApprovalView(null, 0, POLICY_VERSION, null, null, null, false));
    }
}
