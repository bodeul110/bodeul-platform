package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.appointment.GuardianBookingDraftRepository.StoredDraft;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuardianBookingDraftServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-28T01:00:00Z");
    private static final AppUser PATIENT = new AppUser(UUID.randomUUID(), "patient", AppUserRole.PATIENT);
    private static final AppUser GUARDIAN = new AppUser(UUID.randomUUID(), "guardian", AppUserRole.GUARDIAN);
    private static final UUID REQUEST = UUID.randomUUID();
    private final GuardianBookingDraftRepository repository = mock(GuardianBookingDraftRepository.class);
    private final GuardianBookingService booking = mock(GuardianBookingService.class);
    private final GuardianBookingApprovalService approvals = mock(GuardianBookingApprovalService.class);
    private GuardianBookingDraftService service;
    private CreateAppointmentCommand command;

    @BeforeEach
    void setUp() {
        service = new GuardianBookingDraftService(repository, booking, approvals, Clock.fixed(NOW, ZoneOffset.UTC));
        command = GuardianBookingTestFixtures.command(REQUEST, NOW.plusSeconds(172800));
        when(booking.reviewDraft(any(), any(), any(), any())).thenAnswer(invocation ->
                new GuardianBookingService.DraftReview(invocation.getArgument(3),
                        new GuardianBookingService.ParticipantDisplay("환자", "***-****-5678"),
                        new GuardianBookingService.ParticipantDisplay("보호자", "***-****-6666")));
    }

    @Test
    void disabledFeatureDoesNotTouchStorageOrBookingForAnyOperation() {
        doThrow(GuardianBookingException.disabled()).when(approvals).requireEnabled();
        for (Runnable action : List.<Runnable>of(
                () -> service.submit(GUARDIAN, PATIENT.id(), command),
                () -> service.get(PATIENT, GUARDIAN.id(), REQUEST),
                () -> service.list(PATIENT),
                () -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true),
                () -> service.create(GUARDIAN, GUARDIAN.id(), REQUEST, UUID.randomUUID(), 1))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(GuardianBookingException.class,
                    error -> assertThat(error.error()).isEqualTo("guardian_booking_disabled"));
        }
        verifyNoInteractions(repository, booking);
    }

    @Test
    void submitLocksGuardianAndKeepsServerOnlyExpiryAndFingerprint() {
        var summary = service.submit(GUARDIAN, PATIENT.id(), command);
        assertThat(summary.expiresAt()).isEqualTo(NOW.plusSeconds(86400));
        assertThat(summary.createdAt()).isEqualTo(NOW);
        var captured = ArgumentCaptor.forClass(StoredDraft.class);
        var order = inOrder(booking, repository);
        order.verify(booking).reviewDraft(GUARDIAN, PATIENT.id(), GUARDIAN.id(), command);
        order.verify(repository).lockGuardian(GUARDIAN.id());
        order.verify(repository).findForActor(GUARDIAN.id(), REQUEST, GUARDIAN.id());
        order.verify(repository).countActive(GUARDIAN.id(), NOW);
        order.verify(repository).insert(captured.capture());
        assertThat(captured.getValue().fingerprint()).matches("[0-9a-f]{64}");
        verify(booking, never()).grant(any(), any(), any(), anyLong(), anyBoolean());
        verify(booking, never()).create(any(), any(), any(), anyLong(), any());
    }

    @Test
    void draftExpiryCannotExceedAppointmentTime() {
        command = GuardianBookingTestFixtures.command(REQUEST, NOW.plusSeconds(3600));
        assertThat(service.submit(GUARDIAN, PATIENT.id(), command).expiresAt()).isEqualTo(NOW.plusSeconds(3600));
    }

    @Test
    void identicalRetryDoesNotExtendExpiryOrUseQuota() {
        StoredDraft stored = stored(command, NOW.plusSeconds(60));
        when(repository.findForActor(GUARDIAN.id(), REQUEST, GUARDIAN.id())).thenReturn(Optional.of(stored));
        assertThat(service.submit(GUARDIAN, PATIENT.id(), command)).isEqualTo(stored.summary());
        verify(repository, never()).insert(any());
        verify(repository, never()).countActive(any(), any());
    }

    @Test
    void sameKeyCannotReplaceBodyOrRecipient() {
        when(repository.findForActor(GUARDIAN.id(), REQUEST, GUARDIAN.id()))
                .thenReturn(Optional.of(stored(command, NOW.plusSeconds(60))));
        var changed = new CreateAppointmentCommand(REQUEST,
                GuardianBookingTestFixtures.draft(NOW.plusSeconds(172800), "다른 병원", "ON_SITE"), command.pricePolicyVersion(), 40000);
        for (Runnable action : List.<Runnable>of(() -> service.submit(GUARDIAN, PATIENT.id(), changed),
                () -> service.submit(GUARDIAN, UUID.randomUUID(), command))) {
            assertThatThrownBy(action::run).isInstanceOfSatisfying(GuardianBookingException.class,
                    error -> assertThat(error.error()).isEqualTo("guardian_booking_draft_conflict"));
        }
        verify(repository, never()).insert(any());
    }

    @Test
    void expiredDraftCannotBeRevivedOrRead() {
        when(repository.findForActor(eq(GUARDIAN.id()), eq(REQUEST), any()))
                .thenReturn(Optional.of(stored(command, NOW)));
        assertThatThrownBy(() -> service.submit(GUARDIAN, PATIENT.id(), command))
                .isInstanceOfSatisfying(GuardianBookingException.class, error -> assertThat(error.status().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.get(PATIENT, GUARDIAN.id(), REQUEST)).isInstanceOf(GuardianBookingException.class);
        verify(repository, never()).insert(any());
    }

    @Test
    void activeQuotaAndAlreadySubmittedRequestBlockNewStorage() {
        when(repository.countActive(GUARDIAN.id(), NOW)).thenReturn(20L);
        assertThatThrownBy(() -> service.submit(GUARDIAN, PATIENT.id(), command))
                .isInstanceOfSatisfying(GuardianBookingException.class, error -> assertThat(error.status().value()).isEqualTo(429));
        when(booking.receiptForRequest(GUARDIAN, REQUEST)).thenReturn(Optional.of(new GuardianBookingService.Receipt(UUID.randomUUID(), "TEST")));
        assertThatThrownBy(() -> service.submit(GUARDIAN, PATIENT.id(), command))
                .isInstanceOfSatisfying(GuardianBookingException.class, error -> assertThat(error.status().value()).isEqualTo(409));
        verify(repository, never()).insert(any());
    }

    @Test
    void nonGuardianCannotSubmitAndNonPatientCannotGrant() {
        assertThatThrownBy(() -> service.submit(PATIENT, PATIENT.id(), command)).isInstanceOf(GuardianBookingException.class);
        assertThatThrownBy(() -> service.grant(GUARDIAN, GUARDIAN.id(), REQUEST, 0, true)).isInstanceOf(GuardianBookingException.class);
        verifyNoInteractions(repository, booking);
    }

    @Test
    void unrelatedActorGetsNoDraftAndNoCounterpartLookup() {
        AppUser stranger = new AppUser(UUID.randomUUID(), "other", AppUserRole.PATIENT);
        assertThatThrownBy(() -> service.get(stranger, GUARDIAN.id(), REQUEST))
                .isInstanceOfSatisfying(GuardianBookingException.class, error -> assertThat(error.status().value()).isEqualTo(404));
        verify(repository).findForActor(GUARDIAN.id(), REQUEST, stranger.id());
        verify(approvals, never()).requireParticipants(any(), any(), any());
        verifyNoInteractions(booking);
    }

    @Test
    void inactiveParticipantsCannotReadOriginalOrGrant() {
        when(repository.findForActor(GUARDIAN.id(), REQUEST, PATIENT.id())).thenReturn(Optional.of(stored(command, NOW.plusSeconds(60))));
        doThrow(GuardianBookingException.denied()).when(approvals).requireParticipants(PATIENT, PATIENT.id(), GUARDIAN.id());
        assertThatThrownBy(() -> service.get(PATIENT, GUARDIAN.id(), REQUEST)).isInstanceOf(GuardianBookingException.class);
        assertThatThrownBy(() -> service.grant(PATIENT, GUARDIAN.id(), REQUEST, 0, true)).isInstanceOf(GuardianBookingException.class);
        verifyNoInteractions(booking);
    }

    @Test
    void corruptedStoredFingerprintFailsClosed() {
        var stored = stored(command, NOW.plusSeconds(60));
        when(repository.findForActor(GUARDIAN.id(), REQUEST, PATIENT.id())).thenReturn(Optional.of(
                new StoredDraft(stored.patientUserId(), stored.guardianUserId(), command, "0".repeat(64), stored.createdAt(), stored.expiresAt())));
        assertThatThrownBy(() -> service.get(PATIENT, GUARDIAN.id(), REQUEST))
                .isInstanceOfSatisfying(GuardianBookingException.class, error -> assertThat(error.status().value()).isEqualTo(503));
        verifyNoInteractions(booking);
    }

    @Test
    void readAndGrantUseStoredBodyAndCurrentParticipantChecks() {
        var stored = stored(command, NOW.plusSeconds(60));
        when(repository.findForActor(GUARDIAN.id(), REQUEST, PATIENT.id())).thenReturn(Optional.of(stored));
        assertThat(service.get(PATIENT, GUARDIAN.id(), REQUEST).appointment()).isEqualTo(command);
        service.grant(PATIENT, GUARDIAN.id(), REQUEST, 3, true);
        verify(booking).grant(PATIENT, GUARDIAN.id(), command, 3, true);
        verify(booking).reviewDraft(PATIENT, PATIENT.id(), GUARDIAN.id(), command);
    }

    @Test
    void createUsesStoredBodyAndApprovalButNeverAcceptsAnotherGuardianKey() {
        UUID grant = UUID.randomUUID();
        when(repository.findForActor(GUARDIAN.id(), REQUEST, GUARDIAN.id())).thenReturn(Optional.of(stored(command, NOW.plusSeconds(60))));
        service.create(GUARDIAN, GUARDIAN.id(), REQUEST, grant, 2);
        verify(booking).create(GUARDIAN, PATIENT.id(), grant, 2, command);
        assertThatThrownBy(() -> service.create(GUARDIAN, UUID.randomUUID(), REQUEST, grant, 2)).isInstanceOf(GuardianBookingException.class);
        assertThatThrownBy(() -> service.create(PATIENT, GUARDIAN.id(), REQUEST, grant, 2)).isInstanceOf(GuardianBookingException.class);
    }

    @Test
    void receiptRecoveryDoesNotRequireRetainedDraftOrRepeatInsert() {
        var receipt = new GuardianBookingService.Receipt(UUID.randomUUID(), "TEST");
        when(booking.receiptForRequest(GUARDIAN, REQUEST)).thenReturn(Optional.of(receipt));
        assertThat(service.create(GUARDIAN, GUARDIAN.id(), REQUEST, null, -1)).isEqualTo(receipt);
        verifyNoInteractions(repository);
        verify(booking, never()).create(any(), any(), any(), anyLong(), any());
    }

    @Test
    void listContainsOnlyMetadataForCurrentlyEligibleParticipantsAndDoesNotHideOutage() {
        var summary = stored(command, NOW.plusSeconds(60)).summary();
        when(repository.listActive(PATIENT, NOW)).thenReturn(List.of(summary));
        assertThat(service.list(PATIENT)).containsExactly(summary);
        doThrow(GuardianBookingException.denied()).when(approvals).requireParticipants(PATIENT, PATIENT.id(), GUARDIAN.id());
        assertThat(service.list(PATIENT)).isEmpty();
        doThrow(GuardianBookingException.unavailable()).when(approvals).requireParticipants(PATIENT, PATIENT.id(), GUARDIAN.id());
        assertThatThrownBy(() -> service.list(PATIENT)).isInstanceOfSatisfying(GuardianBookingException.class,
                error -> assertThat(error.status().value()).isEqualTo(503));
        verifyNoInteractions(booking);
    }

    private StoredDraft stored(CreateAppointmentCommand value, Instant expires) {
        return new StoredDraft(PATIENT.id(), GUARDIAN.id(), value,
                GuardianBookingService.fingerprint(GUARDIAN.id(), value, AppointmentDraftNormalizer.normalize(value.draft())),
                NOW.minusSeconds(60), expires);
    }
}
