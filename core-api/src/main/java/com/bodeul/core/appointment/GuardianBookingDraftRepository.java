package com.bodeul.core.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.auth.AppUserRepository.AppUser;

interface GuardianBookingDraftRepository {
    void lockGuardian(UUID guardianId);
    Optional<StoredDraft> findForActor(UUID guardianId, UUID requestId, UUID actorId);
    long countActive(UUID guardianId, Instant now);
    void insert(StoredDraft draft);
    List<DraftSummary> listActive(AppUser actor, Instant now);

    record StoredDraft(UUID patientUserId, UUID guardianUserId, CreateAppointmentCommand appointment,
                       String fingerprint, Instant createdAt, Instant expiresAt) {
        DraftSummary summary() {
            return new DraftSummary(patientUserId, guardianUserId, appointment.clientRequestId(), createdAt, expiresAt);
        }
    }

    record DraftSummary(UUID patientUserId, UUID guardianUserId, UUID clientRequestId,
                        Instant createdAt, Instant expiresAt) { }
}
