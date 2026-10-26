package com.bodeul.core.consent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseAccountStatus;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;
import com.bodeul.core.consent.GuardianBookingApprovalRepository.RequestKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DefaultGuardianBookingApprovalServiceTests {
    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final AppUser PATIENT = new AppUser(UUID.randomUUID(), "test-patient", AppUserRole.PATIENT);
    private static final AppUser GUARDIAN = new AppUser(UUID.randomUUID(), "test-guardian", AppUserRole.GUARDIAN);
    private static final UUID REQUEST = UUID.randomUUID();
    private static final RequestKey KEY = new RequestKey(PATIENT.id(), GUARDIAN.id(), REQUEST);
    private static final String HASH = "a".repeat(64);
    private GuardianBookingApprovalRepository repository;
    private AppUserRepository users;
    private FirebaseAccountStatus accounts;
    private DefaultGuardianBookingApprovalService service;

    @BeforeEach
    void setUp() {
        repository = mock(GuardianBookingApprovalRepository.class);
        users = mock(AppUserRepository.class);
        accounts = mock(FirebaseAccountStatus.class);
        when(users.findById(PATIENT.id())).thenReturn(Optional.of(PATIENT));
        when(users.findById(GUARDIAN.id())).thenReturn(Optional.of(GUARDIAN));
        when(accounts.isActive(any())).thenReturn(true);
        when(repository.findCurrent(KEY)).thenReturn(pending());
        when(repository.lockCurrent(KEY)).thenReturn(pending());
        when(repository.save(any(), anyLong())).thenAnswer(call -> call.getArgument(0));
        service = service(true, NOW);
    }

    @Test
    void disabledGateDoesNotAccessAnyExternalState() {
        service = service(false, NOW);
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW.plusSeconds(10)),
                "guardian_booking_disabled");
        verifyNoInteractions(repository, users, accounts);
    }

    @Test
    void grantUsesServerPolicyAndTwentyFourHourMaximum() {
        var view = service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW.plusSeconds(172800));
        assertThat(view.version()).isEqualTo(1);
        assertThat(view.grantedAt()).isEqualTo(NOW);
        assertThat(view.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(view.policyVersion()).isEqualTo(DefaultGuardianBookingApprovalService.POLICY_VERSION);
        assertThat(view.active()).isTrue();
        assertThat(view.toString()).doesNotContain(HASH, PATIENT.firebaseUid(), GUARDIAN.firebaseUid());
    }

    @Test
    void approvalExpiresNoLaterThanAppointment() {
        var view = service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW.plusSeconds(600));
        assertThat(view.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void guardianCannotGrantPatientApproval() {
        assertError(() -> service.grant(GUARDIAN, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW.plusSeconds(10)),
                "guardian_booking_permission_denied");
        verifyNoInteractions(repository);
    }

    @Test
    void adultConfirmationAndFutureAppointmentAreRequired() {
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, false, HASH, NOW.plusSeconds(10)),
                "invalid_guardian_booking_request");
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW),
                "invalid_guardian_booking_request");
        verify(repository, never()).save(any(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1, Long.MAX_VALUE})
    void staleOrInvalidVersionDoesNotOverwrite(long version) {
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, version, true, HASH, NOW.plusSeconds(10)),
                "guardian_booking_approval_conflict");
        verify(repository, never()).save(any(), anyLong());
    }

    @Test
    void databaseCompareAndSetConflictIsNotExposedAsServerError() {
        when(repository.save(any(), anyLong())).thenThrow(new OptimisticLockingFailureException("private SQL"));
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true, HASH, NOW.plusSeconds(10)),
                "guardian_booking_approval_conflict");
    }

    @Test
    void missingOrChangedIdentityFailsClosed() {
        when(users.findById(GUARDIAN.id())).thenReturn(Optional.empty());
        assertDeniedParticipants();
        when(users.findById(GUARDIAN.id())).thenReturn(Optional.of(GUARDIAN));
        when(users.findById(PATIENT.id())).thenReturn(Optional.of(new AppUser(PATIENT.id(), "other-uid", AppUserRole.PATIENT)));
        assertDeniedParticipants();
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = {"PATIENT"}, mode = EnumSource.Mode.EXCLUDE)
    void changedPatientRoleFailsClosed(AppUserRole changedRole) {
        when(users.findById(PATIENT.id())).thenReturn(Optional.of(new AppUser(PATIENT.id(), PATIENT.firebaseUid(), changedRole)));
        assertDeniedParticipants();
    }

    @Test
    void missingOrDisabledFirebaseAccountOfEitherParticipantBlocks() {
        when(accounts.isActive(PATIENT.firebaseUid())).thenReturn(false);
        assertDeniedParticipants();
        when(accounts.isActive(PATIENT.firebaseUid())).thenReturn(true);
        when(accounts.isActive(GUARDIAN.firebaseUid())).thenReturn(false);
        assertDeniedParticipants();
        verifyNoInteractions(repository);
    }

    @Test
    void firebaseOutageIsNotTreatedAsAnActiveAccount() {
        when(accounts.isActive(any())).thenThrow(new FirebaseAccountStatus.UnavailableException());
        assertError(() -> service.requireParticipants(PATIENT, PATIENT.id(), GUARDIAN.id()), "guardian_booking_unavailable");
    }

    @Test
    void unrelatedActorAndWrongGuardianRoleCannotReadMetadata() {
        AppUser outsider = new AppUser(UUID.randomUUID(), "outsider", AppUserRole.PATIENT);
        assertError(() -> service.get(outsider, PATIENT.id(), GUARDIAN.id(), REQUEST), "guardian_booking_permission_denied");
        when(users.findById(GUARDIAN.id())).thenReturn(Optional.of(new AppUser(GUARDIAN.id(), "test-guardian", AppUserRole.MANAGER)));
        assertDeniedParticipants();
        verifyNoInteractions(repository);
    }

    @Test
    void patientCanRevokeEvenIfGuardianIsInactiveAndRepeatIsIdempotent() {
        ApprovalState granted = granted();
        when(repository.lockCurrent(KEY)).thenReturn(granted);
        when(accounts.isActive(GUARDIAN.firebaseUid())).thenReturn(false);
        var view = service.revoke(PATIENT, GUARDIAN.id(), REQUEST, 1);
        assertThat(view.version()).isEqualTo(2);
        assertThat(view.active()).isFalse();
        assertThat(view.revokedAt()).isEqualTo(NOW);
        var revoked = AdultPatientGuardianBookingPolicy.revokeByPatient(granted, 1, PATIENT.id(), AppUserRole.PATIENT, NOW);
        when(repository.lockCurrent(KEY)).thenReturn(revoked);
        assertThat(service.revoke(PATIENT, GUARDIAN.id(), REQUEST, 2).version()).isEqualTo(2);
        verify(repository, times(1)).save(any(), anyLong());
        verify(accounts, never()).isActive(GUARDIAN.firebaseUid());
    }

    @Test
    void creationUsesOnlyLatestLockedGrantAndExactBody() {
        ApprovalState granted = granted();
        when(repository.lockCurrent(KEY)).thenReturn(granted);
        UUID grantId = granted.grant().orElseThrow().id();
        assertThatCode(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, grantId, 1, HASH)).doesNotThrowAnyException();
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, UUID.randomUUID(), 1, HASH), "guardian_booking_approval_conflict");
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, grantId, 0, HASH), "guardian_booking_approval_conflict");
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, grantId, 1, "b".repeat(64)), "guardian_booking_approval_conflict");
        verify(repository, never()).findCurrent(any());
    }

    @Test
    void revokedExpiredAndSupersededGrantsCannotCreate() {
        ApprovalState granted = granted();
        UUID oldGrant = granted.grant().orElseThrow().id();
        when(repository.lockCurrent(KEY)).thenReturn(AdultPatientGuardianBookingPolicy.revokeByPatient(granted, 1, PATIENT.id(), AppUserRole.PATIENT, NOW));
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, oldGrant, 1, HASH), "guardian_booking_approval_conflict");
        when(repository.lockCurrent(KEY)).thenReturn(granted);
        service = service(true, NOW.plusSeconds(3600));
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, oldGrant, 1, HASH), "guardian_booking_approval_conflict");
        service = service(true, NOW);
        when(repository.lockCurrent(KEY)).thenReturn(AdultPatientGuardianBookingPolicy.grantByPatient(granted, 1,
                PATIENT.id(), AppUserRole.PATIENT, true, AppUserRole.GUARDIAN, HASH, NOW, NOW.plusSeconds(600), DefaultGuardianBookingApprovalService.POLICY_VERSION));
        assertError(() -> service.lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, oldGrant, 1, HASH), "guardian_booking_approval_conflict");
    }

    @Test
    void clockRegressionFailsAsConflictWithoutWriting() {
        when(repository.findCurrent(KEY)).thenReturn(granted());
        when(repository.lockCurrent(KEY)).thenReturn(granted());
        service = service(true, NOW.minusSeconds(60));
        assertError(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 1, true, HASH, NOW.plusSeconds(600)), "guardian_booking_approval_conflict");
        assertError(() -> service.revoke(PATIENT, GUARDIAN.id(), REQUEST, 1), "guardian_booking_approval_conflict");
        verify(repository, never()).save(any(), anyLong());
    }

    private ApprovalState pending() { return ApprovalState.pending(PATIENT.id(), GUARDIAN.id(), REQUEST); }
    private ApprovalState granted() {
        return AdultPatientGuardianBookingPolicy.grantByPatient(pending(), 0, PATIENT.id(), AppUserRole.PATIENT,
                true, AppUserRole.GUARDIAN, HASH, NOW, NOW.plusSeconds(3600), DefaultGuardianBookingApprovalService.POLICY_VERSION);
    }
    private DefaultGuardianBookingApprovalService service(boolean enabled, Instant now) {
        return new DefaultGuardianBookingApprovalService(repository, users, accounts, enabled, Clock.fixed(now, ZoneOffset.UTC));
    }
    private void assertDeniedParticipants() {
        assertError(() -> service.requireParticipants(PATIENT, PATIENT.id(), GUARDIAN.id()), "guardian_booking_permission_denied");
    }
    private void assertError(Runnable action, String error) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(GuardianBookingException.class,
                exception -> assertThat(exception.error()).isEqualTo(error)).hasMessageNotContaining("private SQL");
    }
}
