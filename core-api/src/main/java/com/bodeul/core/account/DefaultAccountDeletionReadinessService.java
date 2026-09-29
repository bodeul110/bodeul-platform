package com.bodeul.core.account;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
class DefaultAccountDeletionReadinessService implements AccountDeletionReadinessService {

    private final Optional<AccountDeletionImpactRepository> impactRepository;
    private final Optional<FirebaseAccountDeletionImpactRepository> firebaseImpactRepository;

    DefaultAccountDeletionReadinessService(
            Optional<AccountDeletionImpactRepository> impactRepository,
            Optional<FirebaseAccountDeletionImpactRepository> firebaseImpactRepository) {
        this.impactRepository = impactRepository;
        this.firebaseImpactRepository = firebaseImpactRepository;
    }

    @Override
    public ReadinessResult inspect(UUID userId, String firebaseUid) {
        Set<ObservationCode> observationCodes = new LinkedHashSet<>();
        Set<BlockerCode> blockerCodes = new LinkedHashSet<>();
        SourceInventory postgres = inspectPostgres(userId, observationCodes, blockerCodes);
        SourceInventory firestore = inspectFirestore(firebaseUid, blockerCodes);
        List<SourceInventory> sources = List.of(
                postgres,
                firestore,
                notEvaluated(Source.STORAGE),
                notEvaluated(Source.FIREBASE_AUTH),
                notEvaluated(Source.BACKUP));

        blockerCodes.add(BlockerCode.INVENTORY_INCOMPLETE);
        return new ReadinessResult(
                true,
                false,
                Decision.NOT_EVALUATED,
                false,
                sources,
                List.copyOf(observationCodes),
                List.copyOf(blockerCodes));
    }

    private SourceInventory inspectFirestore(
            String firebaseUid,
            Set<BlockerCode> blockerCodes) {
        if (firebaseImpactRepository.isEmpty()) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.FIRESTORE, SourceStatus.ERROR, Map.of());
        }

        final FirebaseAccountDeletionImpactRepository.FirestoreImpact impact;
        try {
            impact = firebaseImpactRepository.orElseThrow().inspect(firebaseUid);
        } catch (FirebaseAccountDeletionImpactRepository.SourceAccessException exception) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.FIRESTORE, SourceStatus.ERROR, Map.of());
        }

        return new SourceInventory(
                Source.FIRESTORE,
                SourceStatus.PARTIAL,
                Map.ofEntries(
                        Map.entry("userDocuments", impact.userDocumentCount()),
                        Map.entry("notificationTokens", impact.notificationTokenCount()),
                        Map.entry("notificationTokenEntries", impact.notificationTokenEntryCount()),
                        Map.entry(
                                "notificationTokenEntryMismatches",
                                impact.notificationTokenEntryMismatchCount()),
                        Map.entry(
                                "managerDocumentMetadataEntries",
                                impact.managerDocumentMetadataCount()),
                        Map.entry("managerDocumentReferences", impact.managerDocumentReferenceCount()),
                        Map.entry("clientSupportRequests", impact.clientSupportRequestCount()),
                        Map.entry("supportInquiries", impact.supportInquiryCount()),
                        Map.entry(
                                "appointmentRequestsAsPatient",
                                impact.directReferences().appointmentRequestPatientCount()),
                        Map.entry(
                                "appointmentRequestsAsGuardian",
                                impact.directReferences().appointmentRequestGuardianCount()),
                        Map.entry(
                                "appointmentRequestsAsManager",
                                impact.directReferences().appointmentRequestManagerCount()),
                        Map.entry(
                                "appointmentRequestsAsRequester",
                                impact.directReferences().appointmentRequestRequesterCount()),
                        Map.entry(
                                "companionSessionsAsPatient",
                                impact.directReferences().companionSessionPatientCount()),
                        Map.entry(
                                "companionSessionsAsGuardian",
                                impact.directReferences().companionSessionGuardianCount()),
                        Map.entry(
                                "companionSessionsAsManager",
                                impact.directReferences().companionSessionManagerCount())));
    }

    private SourceInventory inspectPostgres(
            UUID userId,
            Set<ObservationCode> observationCodes,
            Set<BlockerCode> blockerCodes) {
        if (impactRepository.isEmpty()) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.POSTGRESQL, SourceStatus.ERROR, Map.of());
        }

        final AccountDeletionImpactRepository.PostgreSqlImpact impact;
        final Optional<AccountDeletionImpactRepository.BookingApprovalImpact> booking;
        try {
            impact = impactRepository.orElseThrow().inspect(userId);
            booking = impactRepository.orElseThrow().inspectBookingApprovals(userId);
        } catch (DataAccessException exception) {
            blockerCodes.add(BlockerCode.SOURCE_UNAVAILABLE);
            return new SourceInventory(Source.POSTGRESQL, SourceStatus.ERROR, Map.of());
        }

        addPostgresObservations(impact, observationCodes);
        Map<String, Long> allCounts = new LinkedHashMap<>(counts(impact));
        booking.ifPresent(value -> {
            allCounts.put("guardianBookingApprovals", value.approvalCount());
            allCounts.put("activeGuardianBookingApprovals", value.activeApprovalCount());
            allCounts.put("guardianBookingApprovalAudits", value.auditCount());
            if (value.activeApprovalCount() > 0) observationCodes.add(ObservationCode.ACTIVE_BOOKING_APPROVAL_PRESENT);
        });
        return new SourceInventory(Source.POSTGRESQL,
                booking.isPresent() ? SourceStatus.COMPLETE : SourceStatus.PARTIAL, allCounts);
    }

    private void addPostgresObservations(
            AccountDeletionImpactRepository.PostgreSqlImpact impact,
            Set<ObservationCode> observationCodes) {
        if (impact.profileCount() == 0) {
            observationCodes.add(ObservationCode.POSTGRES_PROFILE_MISSING);
        }
        if (impact.activeAppointmentCount() > 0) {
            observationCodes.add(ObservationCode.ACTIVE_APPOINTMENT_PRESENT);
        }
        if (impact.activeCompanionSessionCount() > 0) {
            observationCodes.add(ObservationCode.ACTIVE_SESSION_PRESENT);
        }
    }

    private Map<String, Long> counts(AccountDeletionImpactRepository.PostgreSqlImpact impact) {
        return Map.ofEntries(
                Map.entry("profiles", impact.profileCount()),
                Map.entry("appointments", impact.appointmentCount()),
                Map.entry("activeAppointments", impact.activeAppointmentCount()),
                Map.entry("companionSessions", impact.companionSessionCount()),
                Map.entry("activeCompanionSessions", impact.activeCompanionSessionCount()),
                Map.entry("sessionReports", impact.sessionReportCount()),
                Map.entry("appointmentFollowUps", impact.appointmentFollowUpCount()),
                Map.entry("assignmentAudits", impact.assignmentAuditCount()),
                Map.entry("relatedChatMessages", impact.relatedChatMessageCount()),
                Map.entry("sentChatMessages", impact.sentChatMessageCount()),
                Map.entry("relatedChatAttachments", impact.relatedChatAttachmentCount()),
                Map.entry("relatedChatReadReceipts", impact.relatedChatReadReceiptCount()),
                Map.entry("relatedLocations", impact.relatedLocationCount()),
                Map.entry("bankTransferPayments", impact.bankTransferPaymentCount()),
                Map.entry("paymentEvents", impact.paymentEventCount()));
    }

    private SourceInventory notEvaluated(Source source) {
        return new SourceInventory(source, SourceStatus.NOT_EVALUATED, Map.of());
    }
}
