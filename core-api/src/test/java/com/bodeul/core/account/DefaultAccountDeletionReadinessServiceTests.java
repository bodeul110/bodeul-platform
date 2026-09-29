package com.bodeul.core.account;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAccountDeletionReadinessServiceTests {

    private static final UUID USER_ID = UUID.fromString("89f9b085-0e02-4a22-9399-f2d019a5d1ba");
    private static final String FIREBASE_UID = "firebase-user-1";

    @Test
    void successfulPostgresInventoryStillDoesNotDecideDeletion() {
        AccountDeletionImpactRepository repository = new AccountDeletionImpactRepository() {
            public PostgreSqlImpact inspect(UUID userId) { return impact(1, 2, 1, 1); }
            public Optional<BookingApprovalImpact> inspectBookingApprovals(UUID userId) {
                return Optional.of(new BookingApprovalImpact(2, 0, 4));
            }
        };
        FirebaseAccountDeletionImpactRepository firebaseRepository = firebaseUid -> firestoreImpact();
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(repository),
                Optional.of(firebaseRepository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(result.readOnly()).isTrue();
        assertThat(result.deletionExecuted()).isFalse();
        assertThat(result.decision()).isEqualTo(AccountDeletionReadinessService.Decision.NOT_EVALUATED);
        assertThat(result.complete()).isFalse();
        assertThat(result.sources()).hasSize(5);
        assertThat(result.sources().getFirst().source())
                .isEqualTo(AccountDeletionReadinessService.Source.POSTGRESQL);
        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.COMPLETE);
        assertThat(result.sources().getFirst().counts())
                .containsEntry("appointments", 4L)
                .containsEntry("activeAppointments", 2L)
                .containsEntry("bankTransferPayments", 5L)
                .containsEntry("paymentEvents", 6L)
                .containsEntry("guardianBookingApprovals", 2L)
                .containsEntry("activeGuardianBookingApprovals", 0L)
                .containsEntry("guardianBookingApprovalAudits", 4L)
                .doesNotContainKey("activeLegalHolds");
        assertThat(result.sources().get(1).source())
                .isEqualTo(AccountDeletionReadinessService.Source.FIRESTORE);
        assertThat(result.sources().get(1).status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.PARTIAL);
        assertThat(result.sources().get(1).counts())
                .containsOnlyKeys(
                        "userDocuments",
                        "notificationTokens",
                        "notificationTokenEntries",
                        "notificationTokenEntryMismatches",
                        "managerDocumentMetadataEntries",
                        "managerDocumentReferences",
                        "clientSupportRequests",
                        "supportInquiries",
                        "appointmentRequestsAsPatient",
                        "appointmentRequestsAsGuardian",
                        "appointmentRequestsAsManager",
                        "appointmentRequestsAsRequester",
                        "companionSessionsAsPatient",
                        "companionSessionsAsGuardian",
                        "companionSessionsAsManager")
                .containsEntry("userDocuments", 1L)
                .containsEntry("notificationTokens", 2L)
                .containsEntry("notificationTokenEntries", 2L)
                .containsEntry("notificationTokenEntryMismatches", 4L)
                .containsEntry("managerDocumentMetadataEntries", 3L)
                .containsEntry("managerDocumentReferences", 3L)
                .containsEntry("clientSupportRequests", 4L)
                .containsEntry("supportInquiries", 1L)
                .containsEntry("appointmentRequestsAsPatient", 11L)
                .containsEntry("appointmentRequestsAsGuardian", 12L)
                .containsEntry("appointmentRequestsAsManager", 13L)
                .containsEntry("appointmentRequestsAsRequester", 14L)
                .containsEntry("companionSessionsAsPatient", 15L)
                .containsEntry("companionSessionsAsGuardian", 16L)
                .containsEntry("companionSessionsAsManager", 17L)
                .doesNotContainKeys(
                        "appointmentRequests",
                        "companionSessions",
                        "directParticipantTotal");
        assertThat(result.observationCodes()).containsExactly(
                AccountDeletionReadinessService.ObservationCode.ACTIVE_APPOINTMENT_PRESENT,
                AccountDeletionReadinessService.ObservationCode.ACTIVE_SESSION_PRESENT);
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
    }

    @Test
    void missingBookingMigrationIsPartialInsteadOfZero() {
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(userId -> impact(1, 0, 0, 0)), Optional.empty());
        var result = service.inspect(USER_ID, FIREBASE_UID);
        assertThat(result.sources().getFirst().status()).isEqualTo(AccountDeletionReadinessService.SourceStatus.PARTIAL);
        assertThat(result.sources().getFirst().counts()).doesNotContainKeys(
                "guardianBookingApprovals", "activeGuardianBookingApprovals", "guardianBookingApprovalAudits");
        assertThat(result.deletionExecuted()).isFalse();
    }

    @Test
    void activeBookingApprovalIsInventoryObservationNotDeletionAuthorization() {
        AccountDeletionImpactRepository repository = new AccountDeletionImpactRepository() {
            public PostgreSqlImpact inspect(UUID userId) { return impact(1, 0, 0, 0); }
            public Optional<BookingApprovalImpact> inspectBookingApprovals(UUID userId) {
                return Optional.of(new BookingApprovalImpact(1, 1, 3));
            }
        };
        var result = new DefaultAccountDeletionReadinessService(Optional.of(repository), Optional.empty()).inspect(USER_ID, FIREBASE_UID);
        assertThat(result.observationCodes()).contains(AccountDeletionReadinessService.ObservationCode.ACTIVE_BOOKING_APPROVAL_PRESENT);
        assertThat(result.decision()).isEqualTo(AccountDeletionReadinessService.Decision.NOT_EVALUATED);
        assertThat(result.deletionExecuted()).isFalse();
    }

    @Test
    void bookingInventoryFailureDoesNotPretendPostgresInventoryIsComplete() {
        AccountDeletionImpactRepository repository = new AccountDeletionImpactRepository() {
            public PostgreSqlImpact inspect(UUID userId) { return impact(1, 0, 0, 0); }
            public Optional<BookingApprovalImpact> inspectBookingApprovals(UUID userId) {
                throw new DataAccessResourceFailureException("secret endpoint");
            }
        };
        var result = new DefaultAccountDeletionReadinessService(Optional.of(repository), Optional.empty()).inspect(USER_ID, FIREBASE_UID);
        assertThat(result.sources().getFirst().status()).isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.sources().getFirst().counts()).isEmpty();
        assertThat(result.toString()).doesNotContain("secret endpoint");
    }

    @Test
    void missingRepositoryFailsClosedWithoutIdentifiers() {
        var service = new DefaultAccountDeletionReadinessService(
                Optional.empty(),
                Optional.empty());

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(result.complete()).isFalse();
        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.sources().getFirst().counts()).isEmpty();
        assertThat(result.sources().get(1).status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.sources().get(1).counts()).isEmpty();
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.observationCodes()).isEmpty();
        assertThat(result.toString()).doesNotContain(USER_ID.toString());
    }

    @Test
    void databaseFailureFailsClosedWithoutRawError() {
        AccountDeletionImpactRepository repository = userId -> {
            throw new DataAccessResourceFailureException("secret database endpoint");
        };
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(repository),
                Optional.of(firebaseUid -> firestoreImpact()));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(result.sources().getFirst().status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.blockerCodes()).contains(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.toString()).doesNotContain("secret database endpoint");
    }

    @Test
    void missingPostgresProfileIsAnObjectiveBlocker() {
        AccountDeletionImpactRepository repository = userId -> impact(0, 0, 0, 0);
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(repository),
                Optional.of(firebaseUid -> firestoreImpact()));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(result.observationCodes()).containsExactly(
                AccountDeletionReadinessService.ObservationCode.POSTGRES_PROFILE_MISSING);
        assertThat(result.blockerCodes()).containsExactly(
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
    }

    @Test
    void firestoreLookupUsesOnlyTheAuthenticatedFirebaseUid() {
        AtomicReference<String> inspectedUid = new AtomicReference<>();
        FirebaseAccountDeletionImpactRepository firebaseRepository = firebaseUid -> {
            inspectedUid.set(firebaseUid);
            return firestoreImpact();
        };
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(userId -> impact(1, 0, 0, 0)),
                Optional.of(firebaseRepository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(inspectedUid).hasValue(FIREBASE_UID);
        assertThat(result.toString())
                .doesNotContain(FIREBASE_UID)
                .doesNotContain(USER_ID.toString());
    }

    @Test
    void firestoreFailureFailsClosedWithoutRawError() {
        FirebaseAccountDeletionImpactRepository firebaseRepository = firebaseUid -> {
            throw new FirebaseAccountDeletionImpactRepository.SourceAccessException(
                    new IllegalStateException("secret firestore endpoint"));
        };
        var service = new DefaultAccountDeletionReadinessService(
                Optional.of(userId -> impact(1, 0, 0, 0)),
                Optional.of(firebaseRepository));

        AccountDeletionReadinessService.ReadinessResult result = service.inspect(USER_ID, FIREBASE_UID);

        assertThat(result.sources().get(1).status())
                .isEqualTo(AccountDeletionReadinessService.SourceStatus.ERROR);
        assertThat(result.sources().get(1).counts()).isEmpty();
        assertThat(result.blockerCodes()).contains(
                AccountDeletionReadinessService.BlockerCode.SOURCE_UNAVAILABLE,
                AccountDeletionReadinessService.BlockerCode.INVENTORY_INCOMPLETE);
        assertThat(result.toString()).doesNotContain("secret firestore endpoint");
    }

    private FirebaseAccountDeletionImpactRepository.FirestoreImpact firestoreImpact() {
        return new FirebaseAccountDeletionImpactRepository.FirestoreImpact(
                1,
                2,
                2,
                4,
                3,
                3,
                4,
                1,
                new FirebaseAccountDeletionImpactRepository.FirestoreDirectReferenceImpact(
                        11, 12, 13, 14, 15, 16, 17));
    }

    private AccountDeletionImpactRepository.PostgreSqlImpact impact(
            long profileCount,
            long activeAppointmentCount,
            long activeSessionCount,
            long legalHoldCount) {
        return new AccountDeletionImpactRepository.PostgreSqlImpact(
                profileCount,
                4,
                activeAppointmentCount,
                3,
                activeSessionCount,
                2,
                2,
                1,
                8,
                3,
                2,
                1,
                4,
                legalHoldCount,
                5,
                6);
    }
}
