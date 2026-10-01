package com.bodeul.core.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentRepository.AppointmentMutation;
import com.bodeul.core.appointment.AppointmentRepository.AppointmentRecord;
import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static com.bodeul.core.appointment.GuardianBookingTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuardianBookingServiceTests {
    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00Z");
    private static final AppUser PATIENT = new AppUser(UUID.randomUUID(), "test-patient", AppUserRole.PATIENT);
    private static final AppUser GUARDIAN = new AppUser(UUID.randomUUID(), "test-guardian", AppUserRole.GUARDIAN);
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID GRANT = UUID.randomUUID();
    private GuardianBookingApprovalService approvals;
    private AppointmentRepository appointments;
    private AppUserProfileRepository profiles;
    private GuardianBookingService service;
    private CreateAppointmentCommand command;

    @BeforeEach
    void setUp() {
        approvals = mock(GuardianBookingApprovalService.class);
        appointments = mock(AppointmentRepository.class);
        profiles = mock(AppUserProfileRepository.class);
        when(profiles.findById(PATIENT.id())).thenReturn(Optional.of(new AppUserProfileRepository.AppUserProfile(
                PATIENT.id(), AppUserRole.PATIENT, "테스트 환자", "patient@example.test", "010-1234-5678")));
        when(profiles.findById(GUARDIAN.id())).thenReturn(Optional.of(new AppUserProfileRepository.AppUserProfile(
                GUARDIAN.id(), AppUserRole.GUARDIAN, "테스트 보호자", "guardian@example.test", "010-5555-6666")));
        command = command(REQUEST, NOW.plusSeconds(7200));
        service = new GuardianBookingService(approvals, appointments, profiles, Clock.fixed(NOW, ZoneOffset.UTC), () -> "TEST0001");
    }

    @Test
    void createChecksApprovalBeforeInsertAndReturnsOnlyReceipt() {
        var record = record();
        when(appointments.insert(any(), anyString(), anyString())).thenReturn(Optional.of(record));
        assertThat(service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command))
                .isEqualTo(new GuardianBookingService.Receipt(record.id(), "TEST0001"));
        var order = inOrder(approvals, appointments);
        order.verify(approvals).lockForCreation(eq(GUARDIAN), eq(PATIENT.id()), eq(REQUEST), eq(GRANT), eq(1L), anyString());
        var mutation = ArgumentCaptor.forClass(AppointmentMutation.class);
        order.verify(appointments).insert(mutation.capture(), eq("TEST0001"), matches("[0-9a-f]{64}"));
        assertThat(mutation.getValue().requesterRole()).isEqualTo(AppUserRole.GUARDIAN);
        assertThat(mutation.getValue().patientUserId()).isEqualTo(PATIENT.id());
        assertThat(mutation.getValue().guardianUserId()).isEqualTo(GUARDIAN.id());
        assertThat(mutation.getValue().paymentStatusCode()).isEqualTo("DEFERRED");
        assertThat(mutation.getValue().finalPrice()).isEqualTo(40000);
    }

    @Test
    void approvalDenialNeverInsertsBooking() {
        doThrow(GuardianBookingException.conflict()).when(approvals).lockForCreation(any(), any(), any(), any(), anyLong(), anyString());
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command)).isInstanceOf(GuardianBookingException.class);
        verify(appointments, never()).insert(any(), anyString(), anyString());
    }

    @Test
    void grantUsesSameServerFingerprintAsCreation() {
        service.grant(PATIENT, GUARDIAN.id(), command, 0, true);
        var hash = ArgumentCaptor.forClass(String.class);
        verify(approvals).grant(eq(PATIENT), eq(GUARDIAN.id()), eq(REQUEST), eq(0L), eq(true), hash.capture(), eq(NOW.plusSeconds(7200)));
        var record = record();
        when(appointments.insert(any(), anyString(), anyString())).thenReturn(Optional.of(record));
        service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command);
        verify(approvals).lockForCreation(GUARDIAN, PATIENT.id(), REQUEST, GRANT, 1, hash.getValue());
    }

    @Test
    void previewNormalizesBodyWithoutCreatingApprovalOrAppointment() {
        var normalized = service.preview(PATIENT, GUARDIAN.id(), command);
        assertThat(normalized.draft().linkedParticipantPhone()).isEqualTo("010-1234-5678");
        assertThat(normalized.pricePolicyVersion()).isEqualTo(AppointmentPricePolicy.VERSION);
        verifyNoInteractions(appointments);
        verify(approvals, never()).grant(any(), any(), any(), anyLong(), anyBoolean(), anyString(), any());
    }

    @Test
    void cannotCreateWithoutPatientProfileMatchOrWithChangedPrice() {
        when(profiles.findById(PATIENT.id())).thenReturn(Optional.of(new AppUserProfileRepository.AppUserProfile(
                PATIENT.id(), AppUserRole.PATIENT, "다른 환자", "patient@example.test", "01012345678")));
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command)).isInstanceOf(GuardianBookingException.class);
        var wrongPrice = new CreateAppointmentCommand(REQUEST, command.draft(), AppointmentPricePolicy.VERSION, 1);
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, wrongPrice)).isInstanceOf(AppointmentException.class);
        verify(appointments, never()).insert(any(), anyString(), anyString());
        verify(approvals, never()).lockForCreation(any(), any(), any(), any(), anyLong(), anyString());
    }

    @Test
    void patientAndManagerCannotUseGuardianCreation() {
        assertThatThrownBy(() -> service.create(PATIENT, PATIENT.id(), GRANT, 1, command)).isInstanceOf(GuardianBookingException.class);
        var manager = new AppUser(UUID.randomUUID(), "manager", AppUserRole.MANAGER);
        assertThatThrownBy(() -> service.create(manager, PATIENT.id(), GRANT, 1, command)).isInstanceOf(GuardianBookingException.class);
        verifyNoInteractions(appointments, profiles);
    }

    @Test
    void completedRetryOnlyReturnsReceiptEvenAfterApprovalRevocation() {
        var record = record();
        var hash = fingerprint(command);
        when(appointments.findByClientRequestId(GUARDIAN.id(), REQUEST)).thenReturn(Optional.of(record));
        when(appointments.findCreateRequestFingerprint(record.id())).thenReturn(Optional.of(hash));
        assertThat(service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command).appointmentId()).isEqualTo(record.id());
        verify(approvals).requireParticipants(GUARDIAN, PATIENT.id(), GUARDIAN.id());
        verify(approvals, never()).lockForCreation(any(), any(), any(), any(), anyLong(), anyString());
        verify(appointments, never()).insert(any(), anyString(), anyString());
        verifyNoInteractions(profiles);
    }

    @Test
    void changedBodyPriceOrPatientCannotReuseRequestId() {
        var record = record();
        when(appointments.findByClientRequestId(GUARDIAN.id(), REQUEST)).thenReturn(Optional.of(record));
        when(appointments.findCreateRequestFingerprint(record.id())).thenReturn(Optional.of(fingerprint(command)));
        var changedHospital = new CreateAppointmentCommand(REQUEST, draft(NOW.plusSeconds(7200), "변경 병원", "ON_SITE"), AppointmentPricePolicy.VERSION, 40000);
        var changedPrice = new CreateAppointmentCommand(REQUEST, command.draft(), "new-price-v2", 50000);
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, changedHospital)).isInstanceOf(AppointmentException.class);
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, changedPrice)).isInstanceOf(AppointmentException.class);
        assertThatThrownBy(() -> service.create(GUARDIAN, UUID.randomUUID(), GRANT, 1, command)).isInstanceOf(AppointmentException.class);
        verify(appointments, never()).insert(any(), anyString(), anyString());
    }

    @Test
    void publicCodeCollisionRetriesAreBounded() {
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, command)).isInstanceOf(AppointmentException.class);
        verify(appointments, times(5)).insert(any(), anyString(), anyString());
    }

    @Test
    void cannotGrantAfterBookingAlreadyExists() {
        var record = record();
        when(appointments.findByClientRequestId(GUARDIAN.id(), REQUEST)).thenReturn(Optional.of(record));
        assertThatThrownBy(() -> service.grant(PATIENT, GUARDIAN.id(), command, 0, true)).isInstanceOf(GuardianBookingException.class);
        verify(approvals, never()).grant(any(), any(), any(), anyLong(), anyBoolean(), anyString(), any());
    }

    @Test
    void expiredAppointmentAndMissingRequestIdAreRejected() {
        var past = command(REQUEST, NOW);
        assertThatThrownBy(() -> service.grant(PATIENT, GUARDIAN.id(), past, 0, true)).isInstanceOf(GuardianBookingException.class);
        assertThatThrownBy(() -> service.create(GUARDIAN, PATIENT.id(), GRANT, 1, null)).isInstanceOf(GuardianBookingException.class);
        verify(appointments, never()).insert(any(), anyString(), anyString());
    }

    private String fingerprint(CreateAppointmentCommand value) {
        return AppointmentCreateFingerprint.forGuardianApproval(AppointmentDraftNormalizer.toFingerprintRequest(
                GUARDIAN, value.clientRequestId(), AppointmentDraftNormalizer.normalize(value.draft())), value.pricePolicyVersion(), value.expectedFinalPrice());
    }

    private AppointmentRecord record() {
        var record = mock(AppointmentRecord.class);
        when(record.id()).thenReturn(UUID.randomUUID());
        when(record.publicCode()).thenReturn("TEST0001");
        when(record.patientUserId()).thenReturn(PATIENT.id());
        when(record.guardianUserId()).thenReturn(GUARDIAN.id());
        when(record.requesterUserId()).thenReturn(GUARDIAN.id());
        when(record.requesterRole()).thenReturn(AppUserRole.GUARDIAN);
        return record;
    }
}
