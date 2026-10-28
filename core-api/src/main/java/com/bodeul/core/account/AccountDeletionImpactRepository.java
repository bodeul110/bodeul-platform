package com.bodeul.core.account;

import java.util.UUID;
import java.util.Optional;

interface AccountDeletionImpactRepository {

    PostgreSqlImpact inspect(UUID userId);

    default Optional<BookingApprovalImpact> inspectBookingApprovals(UUID userId) {
        return Optional.empty();
    }

    record BookingApprovalImpact(long approvalCount, long activeApprovalCount, long auditCount) { }

    default Optional<BookingDraftImpact> inspectBookingDrafts(UUID userId) {
        return Optional.empty();
    }

    record BookingDraftImpact(long draftCount, long originalCount, long activeDraftCount) { }

    record PostgreSqlImpact(
            long profileCount,
            long appointmentCount,
            long activeAppointmentCount,
            long companionSessionCount,
            long activeCompanionSessionCount,
            long sessionReportCount,
            long appointmentFollowUpCount,
            long assignmentAuditCount,
            long relatedChatMessageCount,
            long sentChatMessageCount,
            long relatedChatAttachmentCount,
            long relatedChatReadReceiptCount,
            long relatedLocationCount,
            long activeLegalHoldCount,
            long bankTransferPaymentCount,
            long paymentEventCount) {
    }
}
