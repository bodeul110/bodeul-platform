package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AdminActionContract;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionDeliveryChannel;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionDeliveryStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryTrigger;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionNotificationMutationPolicy;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.AdminAuditLogEntry;
import com.example.bodeul.domain.model.AdminEmergencyIssueRecord;
import com.example.bodeul.domain.model.AdminRequestActionOverview;
import com.example.bodeul.domain.model.AdminSettlementRecord;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.UserProfileSanitizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Firebase 설정이 없는 환경에서도 화면을 확인할 수 있도록 제공하는 인메모리 저장소다.
 */
public class MockBodeulRepository implements BodeulRepository {
    private final List<User> users = new ArrayList<>();
    private final List<AppointmentRequest> appointmentRequests = new ArrayList<>();
    private final List<CompanionSession> companionSessions = new ArrayList<>();
    private final List<HospitalGuide> hospitalGuides = new ArrayList<>();
    private final List<SessionReport> sessionReports = new ArrayList<>();
    private final Map<String, String> passwordsByEmail = new HashMap<>();
    private final Map<String, String> managerDocumentSummariesByUserId = new HashMap<>();
    private final Map<String, String> managerAvailabilitySummariesByUserId = new HashMap<>();
    private final Map<String, ManagerDocumentStatus> managerDocumentStatusesByUserId = new HashMap<>();
    private final Map<String, String> managerDocumentReviewNotesByUserId = new HashMap<>();
    private final Map<String, Long> managerDocumentUpdatedAtByUserId = new HashMap<>();
    private final Map<String, Long> managerDocumentReviewedAtByUserId = new HashMap<>();
    private final Map<String, String> managerDocumentReviewedByNameByUserId = new HashMap<>();
    private final Map<String, Map<ManagerDocumentFileType, ManagerDocumentFileMetadata>> managerDocumentFilesByUserId =
            new HashMap<>();
    private final Map<String, List<ManagerDocumentHistoryEntry>> managerDocumentHistoriesByUserId = new HashMap<>();
    private final Map<String, AppointmentFollowUpRecord> followUpRecordsByRequestId = new HashMap<>();
    private final Map<String, AdminSettlementRecord> settlementRecordsByRequestId = new HashMap<>();
    private final Map<String, AdminEmergencyIssueRecord> emergencyIssuesByRequestId = new HashMap<>();
    private final List<AdminActionNotification> adminActionNotifications = new ArrayList<>();
    private final List<AdminActionDeliveryRecord> adminActionDeliveries = new ArrayList<>();
    private final List<AdminAuditLogEntry> adminAuditLogs = new ArrayList<>();
    private final List<SupportInquiry> supportInquiries = new ArrayList<>();
    private final List<ClientSupportRequest> clientSupportRequests = new ArrayList<>();

    public MockBodeulRepository() {
        seedUsers();
        seedAppointmentRequests();
        seedCompanionSessions();
        seedHospitalGuides();
        seedSupportInquiries();
    }

    @Override
    public synchronized List<User> getUsers() {
        return Collections.unmodifiableList(new ArrayList<>(users));
    }

    @Override
    public synchronized List<AppointmentRequest> getAppointmentRequests() {
        return Collections.unmodifiableList(new ArrayList<>(appointmentRequests));
    }

    List<AppointmentRequest> getMutableAppointmentRequests() {
        return appointmentRequests;
    }

    List<CompanionSession> getMutableCompanionSessions() {
        return companionSessions;
    }

    List<SessionReport> getMutableSessionReports() {
        return sessionReports;
    }

    List<SupportInquiry> getMutableSupportInquiries() {
        return supportInquiries;
    }

    List<ClientSupportRequest> getMutableClientSupportRequests() {
        return clientSupportRequests;
    }

    public synchronized List<AppointmentRequest> getAppointmentRequestsForUser(String userId, UserRole role) {
        List<AppointmentRequest> result = new ArrayList<>();
        for (AppointmentRequest request : appointmentRequests) {
            if (matchesRequestOwner(request, userId, role)) {
                result.add(request);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Nullable
    public synchronized AppointmentRequestDetail getAppointmentRequestDetail(String requestId) {
        AppointmentRequest request = findAppointmentRequest(requestId);
        if (request == null) {
            return null;
        }

        User patient = findUserById(request.getPatientUserId());
        User guardian = findUserById(request.getGuardianUserId());
        User manager = findUserById(request.getManagerUserId());
        CompanionSession session = findSessionByRequestId(requestId);
        SessionReport report = session == null ? null : getSessionReport(session.getId());
        HospitalGuide guide = HospitalGuideFallbackFactory.fallbackIfMissing(
                getHospitalGuide(request.getHospitalName(), request.getDepartmentName()),
                request.getHospitalName(),
                request.getDepartmentName()
        );
        AppointmentFollowUpRecord followUpRecord = request.getStatus() == AppointmentStatus.COMPLETED
                ? getAppointmentFollowUpRecord(requestId)
                : null;
        return new AppointmentRequestDetail(
                request,
                patient,
                guardian,
                manager,
                session,
                report,
                guide,
                followUpRecord
        );
    }

    public synchronized List<User> getUsersByRole(UserRole role) {
        List<User> result = new ArrayList<>();
        for (User user : users) {
            if (user.getRole() == role) {
                result.add(user);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public synchronized List<CompanionSession> getManagerSessions(String managerUserId) {
        List<CompanionSession> result = new ArrayList<>();
        for (CompanionSession session : companionSessions) {
            if (session.getManagerUserId().equals(managerUserId)) {
                result.add(session);
            }
        }
        return result;
    }

    @Override
    public synchronized HospitalGuide getHospitalGuide(String hospitalName, String departmentName) {
        for (HospitalGuide guide : hospitalGuides) {
            if (guide.getHospitalName().equals(hospitalName)
                    && guide.getDepartmentName().equals(departmentName)) {
                return guide;
            }
        }
        return null;
    }

    @Override
    public synchronized SessionReport getSessionReport(String sessionId) {
        for (SessionReport report : sessionReports) {
            if (report.getSessionId().equals(sessionId)) {
                return report;
            }
        }
        return null;
    }

    public synchronized List<HospitalGuide> getHospitalGuides() {
        return Collections.unmodifiableList(new ArrayList<>(hospitalGuides));
    }

    @Nullable
    public synchronized ManagerHomeProfile getManagerHomeProfile(String managerUserId) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        return new ManagerHomeProfile(
                managerDocumentSummariesByUserId.getOrDefault(managerUserId, ""),
                managerAvailabilitySummariesByUserId.getOrDefault(managerUserId, ""),
                resolveManagerDocumentStatus(managerUserId),
                managerDocumentReviewNotesByUserId.getOrDefault(managerUserId, ""),
                managerDocumentUpdatedAtByUserId.getOrDefault(managerUserId, 0L),
                managerDocumentReviewedAtByUserId.getOrDefault(managerUserId, 0L),
                managerDocumentReviewedByNameByUserId.getOrDefault(managerUserId, ""),
                getManagerDocumentFiles(managerUserId)
        );
    }

    public synchronized List<ManagerDocumentHistoryEntry> getManagerDocumentHistory(String managerUserId) {
        List<ManagerDocumentHistoryEntry> historyEntries = managerDocumentHistoriesByUserId.get(managerUserId);
        if (historyEntries == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(historyEntries));
    }

    public synchronized List<ManagerDocumentFileMetadata> getManagerDocumentFiles(String managerUserId) {
        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileMap =
                managerDocumentFilesByUserId.get(managerUserId);
        if (fileMap == null || fileMap.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(fileMap.values()));
    }

    public synchronized List<AdminRequestActionOverview> getAdminRequestActionOverviews() {
        List<AdminRequestActionOverview> overviews = new ArrayList<>();
        for (AppointmentRequest request : appointmentRequests) {
            AdminSettlementRecord settlementRecord = settlementRecordsByRequestId.get(request.getId());
            AdminEmergencyIssueRecord emergencyIssueRecord = emergencyIssuesByRequestId.get(request.getId());
            AppointmentFollowUpRecord followUpRecord = followUpRecordsByRequestId.get(request.getId());
            if (settlementRecord == null && emergencyIssueRecord == null && followUpRecord == null) {
                continue;
            }
            overviews.add(new AdminRequestActionOverview(
                    request.getId(),
                    settlementRecord,
                    emergencyIssueRecord,
                    followUpRecord
            ));
        }
        return Collections.unmodifiableList(overviews);
    }

    public synchronized List<AdminActionNotification> getAdminActionNotifications() {
        return AdminActionContract.sortNotifications(adminActionNotifications);
    }

    public synchronized List<AdminAuditLogEntry> getAdminAuditLogs() {
        return AdminActionContract.sortAuditLogs(adminAuditLogs);
    }

    public synchronized List<AdminActionDeliveryRecord> getAdminActionDeliveries() {
        return AdminActionContract.sortDeliveries(adminActionDeliveries);
    }

    @Nullable
    public synchronized AdminActionNotification markAdminActionNotificationRead(
            String notificationId
    ) {
        for (int index = 0; index < adminActionNotifications.size(); index++) {
            AdminActionNotification notification = adminActionNotifications.get(index);
            if (!notification.getId().equals(notificationId)) {
                continue;
            }
            if (!AdminActionNotificationMutationPolicy.canMutate(notification.getSourceType())) {
                return null;
            }
            if (notification.isRead()) {
                return notification;
            }
            AdminActionNotification updatedNotification = new AdminActionNotification(
                    notification.getId(),
                    notification.getSourceType(),
                    notification.getLevel(),
                    notification.getRequestId(),
                    notification.getInquiryId(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getActorName(),
                    notification.getCreatedAtMillis(),
                    true,
                    System.currentTimeMillis(),
                    notification.isResolved(),
                    notification.getResolvedAtMillis(),
                    notification.getResolvedByName()
            );
            adminActionNotifications.set(index, updatedNotification);
            appendActionDeliveryRecords(
                    updatedNotification,
                    AdminActionDeliveryTrigger.NOTIFICATION_READ,
                    AdminActionDeliveryStatus.CONFIRMED,
                    AdminActionDeliveryStatus.CONFIRMED,
                    "관리자 앱 푸시 기준 읽음 확인이 완료됐습니다.",
                    "운영 피드에 읽음 상태 반영을 완료했습니다.",
                    System.currentTimeMillis()
            );
            return updatedNotification;
        }
        return null;
    }

    @Nullable
    public synchronized AdminActionNotification updateAdminActionNotificationResolved(
            String notificationId,
            boolean resolved,
            String handledByName
    ) {
        for (int index = 0; index < adminActionNotifications.size(); index++) {
            AdminActionNotification notification = adminActionNotifications.get(index);
            if (!notification.getId().equals(notificationId)) {
                continue;
            }
            if (!AdminActionNotificationMutationPolicy.canMutate(notification.getSourceType())) {
                return null;
            }

            long now = System.currentTimeMillis();
            long readAtMillis = notification.isRead() && notification.getReadAtMillis() > 0L
                    ? notification.getReadAtMillis()
                    : now;
            AdminActionNotification updatedNotification = new AdminActionNotification(
                    notification.getId(),
                    notification.getSourceType(),
                    notification.getLevel(),
                    notification.getRequestId(),
                    notification.getInquiryId(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getActorName(),
                    notification.getCreatedAtMillis(),
                    true,
                    readAtMillis,
                    resolved,
                    resolved ? now : 0L,
                    resolved ? normalizeText(handledByName) : ""
            );
            adminActionNotifications.set(index, updatedNotification);
            adminAuditLogs.add(0, new AdminAuditLogEntry(
                    "admin-audit-" + now + "-" + adminAuditLogs.size(),
                    notification.getSourceType(),
                    notification.getRequestId(),
                    notification.getInquiryId(),
                    resolved
                            ? "후속 알림 해결 처리"
                            : "후속 알림 다시 열기",
                    buildNotificationResolutionNote(notification, resolved),
                    normalizeText(handledByName),
                    now
            ));
            appendActionDeliveryRecords(
                    updatedNotification,
                    resolved
                            ? AdminActionDeliveryTrigger.NOTIFICATION_RESOLVED
                            : AdminActionDeliveryTrigger.NOTIFICATION_REOPENED,
                    resolved
                            ? AdminActionDeliveryStatus.SKIPPED
                            : AdminActionDeliveryStatus.SENT,
                    AdminActionDeliveryStatus.CONFIRMED,
                    resolved
                            ? "관리자 처리 결과라 추가 푸시를 보내지 않았습니다."
                            : "재오픈 알림을 관리자 앱 푸시 채널로 다시 전달했습니다.",
                    resolved
                            ? "운영 피드에 해결 완료 반영을 마쳤습니다."
                            : "운영 피드에 재오픈 상태 반영을 마쳤습니다.",
                    now
            );
            return updatedNotification;
        }
        return null;
    }

    public synchronized List<SupportInquiry> getSupportInquiries() {
        return Collections.unmodifiableList(new ArrayList<>(supportInquiries));
    }

    public synchronized List<SupportInquiry> getSupportInquiries(String managerUserId) {
        List<SupportInquiry> inquiries = new ArrayList<>();
        for (SupportInquiry inquiry : supportInquiries) {
            if (inquiry.getManagerUserId().equals(managerUserId)) {
                inquiries.add(inquiry);
            }
        }
        return Collections.unmodifiableList(inquiries);
    }

    public synchronized List<ClientSupportRequest> getClientSupportRequests(String userId) {
        List<ClientSupportRequest> requests = new ArrayList<>();
        for (ClientSupportRequest request : clientSupportRequests) {
            if (request.getUserId().equals(userId)) {
                requests.add(request);
            }
        }
        return Collections.unmodifiableList(requests);
    }

    public synchronized List<ClientSupportRequest> getClientSupportRequests() {
        return Collections.unmodifiableList(new ArrayList<>(clientSupportRequests));
    }

    public synchronized AppointmentFollowUpRecord getAppointmentFollowUpRecord(String requestId) {
        if (findAppointmentRequest(requestId) == null) {
            return AppointmentFollowUpRecord.empty(requestId);
        }
        AppointmentFollowUpRecord record = followUpRecordsByRequestId.get(requestId);
        return record == null ? AppointmentFollowUpRecord.empty(requestId) : record;
    }

    @Nullable
    public synchronized AppointmentFollowUpRecord saveAppointmentFollowUpReview(
            String requestId,
            AppointmentFollowUpReviewRating reviewRating
    ) {
        AppointmentRequest request = findAppointmentRequest(requestId);
        if (request == null || request.getStatus() != AppointmentStatus.COMPLETED || reviewRating == null) {
            return null;
        }
        AppointmentFollowUpRecord currentRecord = getAppointmentFollowUpRecord(requestId);
        AppointmentFollowUpRecord record = new AppointmentFollowUpRecord(
                requestId,
                reviewRating,
                System.currentTimeMillis(),
                currentRecord.getSettlementStatus(),
                currentRecord.getSettlementNote(),
                currentRecord.getSettlementSavedAtMillis(),
                currentRecord.getSupportEscalationStatus(),
                currentRecord.getSupportEscalatedAtMillis()
        );
        followUpRecordsByRequestId.put(requestId, record);
        return record;
    }

    @Nullable
    public synchronized AppointmentFollowUpRecord saveAppointmentFollowUpSettlement(
            String requestId,
            AppointmentFollowUpSettlementStatus settlementStatus,
            String settlementNote
    ) {
        AppointmentRequest request = findAppointmentRequest(requestId);
        if (request == null || request.getStatus() != AppointmentStatus.COMPLETED || settlementStatus == null) {
            return null;
        }
        AppointmentFollowUpRecord currentRecord = getAppointmentFollowUpRecord(requestId);
        AppointmentFollowUpRecord record = new AppointmentFollowUpRecord(
                requestId,
                currentRecord.getReviewRating(),
                currentRecord.getReviewSavedAtMillis(),
                settlementStatus,
                normalizeText(settlementNote),
                System.currentTimeMillis(),
                currentRecord.getSupportEscalationStatus(),
                currentRecord.getSupportEscalatedAtMillis()
        );
        followUpRecordsByRequestId.put(requestId, record);
        return record;
    }

    @Nullable
    public synchronized ManagerHomeProfile saveManagerDocumentSummary(String managerUserId, String documentSummary) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        String normalizedSummary = normalizeText(documentSummary);
        String currentSummary = normalizeText(managerDocumentSummariesByUserId.get(managerUserId));
        ManagerDocumentStatus currentStatus = resolveManagerDocumentStatus(managerUserId);
        ManagerDocumentStatus targetStatus = normalizedSummary.isEmpty()
                ? ManagerDocumentStatus.NOT_SUBMITTED
                : ManagerDocumentStatus.PENDING_REVIEW;
        if (currentSummary.equals(normalizedSummary) && currentStatus == targetStatus) {
            return getManagerHomeProfile(managerUserId);
        }
        if (normalizedSummary.isEmpty()) {
            managerDocumentSummariesByUserId.remove(managerUserId);
            managerDocumentStatusesByUserId.put(managerUserId, targetStatus);
        } else {
            managerDocumentSummariesByUserId.put(managerUserId, normalizedSummary);
            managerDocumentStatusesByUserId.put(managerUserId, targetStatus);
        }
        managerDocumentUpdatedAtByUserId.put(managerUserId, System.currentTimeMillis());
        if (!normalizedSummary.isEmpty()) {
            appendManagerDocumentSubmissionHistory(managerUserId, manager);
        }
        return getManagerHomeProfile(managerUserId);
    }

    @Nullable
    public synchronized ManagerHomeProfile saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata
    ) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        if (documentFileMetadata == null || documentFileMetadata.isEmpty()) {
            return null;
        }
        if (!ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                documentFileMetadata.getFileType()
        )) {
            return null;
        }
        if (normalizeText(managerDocumentSummariesByUserId.get(managerUserId)).isEmpty()) {
            return null;
        }

        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileMap =
                managerDocumentFilesByUserId.get(managerUserId);
        if (fileMap == null) {
            fileMap = new HashMap<>();
            managerDocumentFilesByUserId.put(managerUserId, fileMap);
        }
        replaceCanonicalQualification(fileMap, documentFileMetadata);
        managerDocumentStatusesByUserId.put(managerUserId, ManagerDocumentStatus.PENDING_REVIEW);
        managerDocumentUpdatedAtByUserId.put(
                managerUserId,
                documentFileMetadata.getUploadedAtMillis() > 0L
                        ? documentFileMetadata.getUploadedAtMillis()
                        : System.currentTimeMillis()
        );
        appendManagerDocumentSubmissionHistory(managerUserId, manager);
        return getManagerHomeProfile(managerUserId);
    }

    @Nullable
    public synchronized ManagerHomeProfile saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata
    ) {
        User manager = findUserById(managerUserId);
        if (manager == null
                || manager.getRole() != UserRole.MANAGER
                || documentFileMetadata == null
                || documentFileMetadata.isEmpty()
                || !ManagerDocumentUploadPolicy.isCanonicalQualificationType(
                        documentFileMetadata.getFileType()
                )) {
            return null;
        }

        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileMap =
                managerDocumentFilesByUserId.get(managerUserId);
        if (fileMap == null) {
            fileMap = new HashMap<>();
            managerDocumentFilesByUserId.put(managerUserId, fileMap);
        }
        ManagerDocumentStatus currentStatus = resolveManagerDocumentStatus(managerUserId);
        boolean isInitialDraft = currentStatus == ManagerDocumentStatus.NOT_SUBMITTED
                && normalizeText(managerDocumentSummariesByUserId.get(managerUserId)).isEmpty();
        replaceCanonicalQualification(fileMap, documentFileMetadata);
        managerDocumentStatusesByUserId.put(
                managerUserId,
                isInitialDraft
                        ? ManagerDocumentStatus.NOT_SUBMITTED
                        : ManagerDocumentStatus.PENDING_REVIEW
        );
        managerDocumentUpdatedAtByUserId.put(
                managerUserId,
                documentFileMetadata.getUploadedAtMillis() > 0L
                        ? documentFileMetadata.getUploadedAtMillis()
                        : System.currentTimeMillis()
        );
        if (!isInitialDraft) {
            appendManagerDocumentSubmissionHistory(managerUserId, manager);
        }
        return getManagerHomeProfile(managerUserId);
    }

    private static void replaceCanonicalQualification(
            Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileMap,
            ManagerDocumentFileMetadata documentFileMetadata
    ) {
        fileMap.remove(ManagerDocumentFileType.LICENSE);
        fileMap.remove(ManagerDocumentFileType.NURSING_LICENSE);
        fileMap.remove(ManagerDocumentFileType.HEALTH_CERTIFICATE);
        fileMap.put(documentFileMetadata.getFileType(), documentFileMetadata);
    }

    @Nullable
    public synchronized ManagerHomeProfile saveManagerAvailabilitySummary(String managerUserId, String availabilitySummary) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        String normalizedSummary = normalizeText(availabilitySummary);
        if (normalizedSummary.isEmpty()) {
            managerAvailabilitySummariesByUserId.remove(managerUserId);
        } else {
            managerAvailabilitySummariesByUserId.put(managerUserId, normalizedSummary);
        }
        return getManagerHomeProfile(managerUserId);
    }

    @Nullable
    public synchronized ManagerHomeProfile reviewManagerDocument(
            String managerUserId,
            ManagerDocumentStatus status,
            String reviewNote,
            String reviewerName
    ) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        if (status != ManagerDocumentStatus.APPROVED && status != ManagerDocumentStatus.REJECTED) {
            return null;
        }
        if (normalizeText(managerDocumentSummariesByUserId.get(managerUserId)).isEmpty()) {
            return null;
        }

        managerDocumentStatusesByUserId.put(managerUserId, status);
        String normalizedReviewNote = normalizeText(reviewNote);
        if (normalizedReviewNote.isEmpty()) {
            managerDocumentReviewNotesByUserId.remove(managerUserId);
        } else {
            managerDocumentReviewNotesByUserId.put(managerUserId, normalizedReviewNote);
        }
        managerDocumentReviewedAtByUserId.put(managerUserId, System.currentTimeMillis());
        String normalizedReviewerName = normalizeText(reviewerName);
        if (normalizedReviewerName.isEmpty()) {
            managerDocumentReviewedByNameByUserId.remove(managerUserId);
        } else {
            managerDocumentReviewedByNameByUserId.put(managerUserId, normalizedReviewerName);
        }
        appendManagerDocumentHistory(
                managerUserId,
                new ManagerDocumentHistoryEntry(
                        status == ManagerDocumentStatus.APPROVED
                                ? ManagerDocumentHistoryEventType.APPROVED
                                : ManagerDocumentHistoryEventType.REJECTED,
                        managerDocumentReviewedAtByUserId.get(managerUserId),
                        normalizedReviewerName,
                        managerDocumentSummariesByUserId.getOrDefault(managerUserId, ""),
                        normalizedReviewNote
                )
        );
        return getManagerHomeProfile(managerUserId);
    }

    @Nullable
    public synchronized AdminSettlementRecord saveSettlementRecord(
            String requestId,
            AdminSettlementStatus status,
            String note,
            String handledByName
    ) {
        AppointmentRequest request = findAppointmentRequest(requestId);
        if (request == null) {
            return null;
        }
        AdminSettlementRecord record = new AdminSettlementRecord(
                requestId,
                status,
                normalizeText(note),
                normalizeText(handledByName),
                System.currentTimeMillis()
        );
        settlementRecordsByRequestId.put(requestId, record);
        appendAdminActionArtifacts(
                AdminActionSourceType.SETTLEMENT,
                status == AdminSettlementStatus.NEEDS_REVIEW
                        ? AdminActionNotificationLevel.WARNING
                        : AdminActionNotificationLevel.INFO,
                requestId,
                "",
                buildSettlementNotificationTitle(status),
                buildSettlementNotificationBody(request, status),
                buildSettlementAuditSummary(status),
                record.getNote(),
                record.getHandledByName(),
                record.getHandledAtMillis()
        );
        return record;
    }

    @Nullable
    public synchronized SupportInquiry saveSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body
    ) {
        User manager = findUserById(managerUserId);
        if (manager == null || manager.getRole() != UserRole.MANAGER) {
            return null;
        }
        long createdAtMillis = System.currentTimeMillis();
        SupportInquiry inquiry = new SupportInquiry(
                "support-" + createdAtMillis,
                managerUserId,
                manager.getName(),
                category,
                normalizeText(title),
                normalizeText(body),
                SupportInquiryStatus.RECEIVED,
                createdAtMillis,
                "",
                0L,
                ""
        );
        supportInquiries.add(0, inquiry);
        return inquiry;
    }

    @Nullable
    public synchronized ClientSupportRequest saveClientSupportRequest(
            String userId,
            String appointmentRequestId,
            ClientSupportCategory category,
            String title,
            String body
    ) {
        User user = findUserById(userId);
        if (user == null || (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN)) {
            return null;
        }
        long createdAtMillis = System.currentTimeMillis();
        ClientSupportRequest request = new ClientSupportRequest(
                "client-support-" + createdAtMillis,
                userId,
                user.getName(),
                user.getRole(),
                normalizeText(appointmentRequestId),
                category,
                normalizeText(title),
                normalizeText(body),
                ClientSupportStatus.RECEIVED,
                createdAtMillis,
                "",
                0L,
                "",
                false,
                0L,
                0,
                0L
        );
        clientSupportRequests.add(0, request);
        return request;
    }

    @Nullable
    public synchronized ClientSupportRequest respondClientSupportRequest(
            String supportRequestId,
            String response,
            String respondedByName
    ) {
        for (int index = 0; index < clientSupportRequests.size(); index++) {
            ClientSupportRequest request = clientSupportRequests.get(index);
            if (!request.getId().equals(supportRequestId)) {
                continue;
            }
            ClientSupportRequest updatedRequest = new ClientSupportRequest(
                    request.getId(),
                    request.getUserId(),
                    request.getUserName(),
                    request.getUserRole(),
                    request.getAppointmentRequestId(),
                    request.getCategory(),
                    request.getTitle(),
                    request.getBody(),
                    ClientSupportStatus.ANSWERED,
                    request.getCreatedAtMillis(),
                    normalizeText(response),
                    System.currentTimeMillis(),
                    normalizeText(respondedByName),
                    false,
                    0L,
                    0,
                    0L
            );
            clientSupportRequests.set(index, updatedRequest);
            return updatedRequest;
        }
        return null;
    }

    public synchronized void markClientSupportResponsesRead(String userId) {
        long readAtMillis = System.currentTimeMillis();
        for (int index = 0; index < clientSupportRequests.size(); index++) {
            ClientSupportRequest request = clientSupportRequests.get(index);
            if (!request.getUserId().equals(userId) || !request.hasUnreadResponse()) {
                continue;
            }
            clientSupportRequests.set(index, new ClientSupportRequest(
                    request.getId(),
                    request.getUserId(),
                    request.getUserName(),
                    request.getUserRole(),
                    request.getAppointmentRequestId(),
                    request.getCategory(),
                    request.getTitle(),
                    request.getBody(),
                    request.getStatus(),
                    request.getCreatedAtMillis(),
                    request.getResponseText(),
                    request.getRespondedAtMillis(),
                    request.getRespondedByName(),
                    true,
                    readAtMillis,
                    request.getResponseReminderCount(),
                    request.getResponseReminderSentAtMillis()
            ));
        }
    }

    @Nullable
    public synchronized SupportInquiry respondSupportInquiry(
            String inquiryId,
            String response,
            String respondedByName
    ) {
        for (int index = 0; index < supportInquiries.size(); index++) {
            SupportInquiry inquiry = supportInquiries.get(index);
            if (!inquiry.getId().equals(inquiryId)) {
                continue;
            }
            SupportInquiry updatedInquiry = new SupportInquiry(
                    inquiry.getId(),
                    inquiry.getManagerUserId(),
                    inquiry.getManagerName(),
                    inquiry.getCategory(),
                    inquiry.getTitle(),
                    inquiry.getBody(),
                    SupportInquiryStatus.ANSWERED,
                    inquiry.getCreatedAtMillis(),
                    normalizeText(response),
                    System.currentTimeMillis(),
                    normalizeText(respondedByName)
            );
            supportInquiries.set(index, updatedInquiry);
            appendAdminActionArtifacts(
                    AdminActionSourceType.SUPPORT,
                    AdminActionNotificationLevel.INFO,
                    "",
                    inquiry.getId(),
                    "문의 응답 저장",
                    buildSupportNotificationBody(inquiry),
                    "문의 응답 저장",
                    updatedInquiry.getResponseText(),
                    updatedInquiry.getRespondedByName(),
                    updatedInquiry.getRespondedAtMillis()
            );
            return updatedInquiry;
        }
        return null;
    }

    @Nullable
    public synchronized User findUserById(String userId) {
        for (User user : users) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        return null;
    }

    @Nullable
    public synchronized User findUserByEmail(String email) {
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        if (normalizedEmail.isEmpty()) {
            return null;
        }
        for (User user : users) {
            if (UserProfileSanitizer.normalizeEmail(user.getEmail()).equals(normalizedEmail)) {
                return user;
            }
        }
        return null;
    }

    @Nullable
    public synchronized User findUserByPhone(String phone) {
        String normalizedPhone = UserProfileSanitizer.normalizePhone(phone);
        if (normalizedPhone.isEmpty()) {
            return null;
        }
        for (User user : users) {
            if (UserProfileSanitizer.normalizePhone(user.getPhone()).equals(normalizedPhone)) {
                return user;
            }
        }
        return null;
    }

    public synchronized boolean isPasswordValid(String email, String password) {
        String savedPassword = passwordsByEmail.get(normalizeKey(email));
        return savedPassword != null && savedPassword.equals(password);
    }

    @Nullable
    public synchronized User registerUser(
            String name,
            String email,
            String phone,
            UserRole role,
            String password
    ) {
        if (findUserByEmail(email) != null) {
            return null;
        }

        String normalizedName = UserProfileSanitizer.normalizeName(name);
        String normalizedEmail = UserProfileSanitizer.normalizeEmail(email);
        String normalizedPhone = UserProfileSanitizer.normalizePhone(phone);
        String id = role.name().toLowerCase(Locale.ROOT) + "-" + (users.size() + 1);
        User user = new User(id, role, normalizedName, normalizedEmail, normalizedPhone);
        users.add(user);
        passwordsByEmail.put(normalizeKey(normalizedEmail), password);
        return user;
    }

    @Nullable
    public synchronized User updateUserProfile(String userId, String name, String phone) {
        String normalizedName = UserProfileSanitizer.normalizeName(name);
        String normalizedPhone = UserProfileSanitizer.normalizePhone(phone);
        for (int index = 0; index < users.size(); index++) {
            User existingUser = users.get(index);
            if (!existingUser.getId().equals(userId)) {
                continue;
            }

            User updatedUser = new User(
                    existingUser.getId(),
                    existingUser.getRole(),
                    normalizedName,
                    existingUser.getEmail(),
                    normalizedPhone
            );
            users.set(index, updatedUser);
            return updatedUser;
        }
        return null;
    }

    @Nullable
    public synchronized AppointmentRequest createAppointmentRequest(
            User currentUser,
            BookingRequestDraft bookingRequestDraft
    ) {
        if (!hasSelectablePaymentMethod(bookingRequestDraft)) {
            return null;
        }
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        String normalizedLinkedName = UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName());
        String normalizedLinkedPhone = UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone());
        String normalizedLinkedEmail = UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail());
        User linkedParticipant = resolveLinkedParticipant(
                resolveCounterpartRole(currentUser.getRole()),
                normalizedLinkedEmail,
                normalizedLinkedPhone
        );

        String patientUserId;
        String guardianUserId;
        String patientName;
        String patientPhone;
        String patientEmail;
        String guardianName;
        String guardianPhone;
        String guardianEmail;

        if (currentUser.getRole() == UserRole.PATIENT) {
            patientUserId = currentUser.getId();
            patientName = UserProfileSanitizer.normalizeName(currentUser.getName());
            patientPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            patientEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            guardianUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            guardianName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            guardianPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            guardianEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        } else {
            guardianUserId = currentUser.getId();
            guardianName = UserProfileSanitizer.normalizeName(currentUser.getName());
            guardianPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            guardianEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            patientUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            patientName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            patientPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            patientEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        }

        AppointmentRequest request = createSnapshotBackedRequest(
                "request-" + (appointmentRequests.size() + 1),
                bookingRequestDraft,
                patientUserId,
                guardianUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail
        );
        // 새로 만든 요청은 목록 상단에 쌓아 신청 직후 바로 보이게 한다.
        appointmentRequests.add(0, request);
        return request;
    }

    @Nullable
    public synchronized AppointmentRequest updateAppointmentRequest(
            User currentUser,
            String requestId,
            BookingRequestDraft bookingRequestDraft
    ) {
        if (!hasSelectablePaymentMethod(bookingRequestDraft)) {
            return null;
        }
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        AppointmentRequest existingRequest = findAppointmentRequest(requestId);
        if (existingRequest == null
                || existingRequest.getStatus() != AppointmentStatus.REQUESTED
                || !matchesRequestOwner(existingRequest, currentUser.getId(), currentUser.getRole())) {
            return null;
        }

        String normalizedLinkedName = UserProfileSanitizer.normalizeName(bookingRequestDraft.getLinkedParticipantName());
        String normalizedLinkedPhone = UserProfileSanitizer.normalizePhone(bookingRequestDraft.getLinkedParticipantPhone());
        String normalizedLinkedEmail = UserProfileSanitizer.normalizeEmail(bookingRequestDraft.getLinkedParticipantEmail());
        User linkedParticipant = resolveLinkedParticipant(
                resolveCounterpartRole(currentUser.getRole()),
                normalizedLinkedEmail,
                normalizedLinkedPhone
        );

        String patientUserId;
        String guardianUserId;
        String patientName;
        String patientPhone;
        String patientEmail;
        String guardianName;
        String guardianPhone;
        String guardianEmail;

        if (currentUser.getRole() == UserRole.PATIENT) {
            patientUserId = currentUser.getId();
            patientName = UserProfileSanitizer.normalizeName(currentUser.getName());
            patientPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            patientEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            guardianUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            guardianName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            guardianPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            guardianEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        } else {
            guardianUserId = currentUser.getId();
            guardianName = UserProfileSanitizer.normalizeName(currentUser.getName());
            guardianPhone = UserProfileSanitizer.normalizePhone(currentUser.getPhone());
            guardianEmail = UserProfileSanitizer.normalizeEmail(currentUser.getEmail());
            patientUserId = linkedParticipant == null ? "" : linkedParticipant.getId();
            patientName = linkedParticipant == null
                    ? normalizedLinkedName
                    : UserProfileSanitizer.normalizeName(linkedParticipant.getName());
            patientPhone = linkedParticipant == null
                    ? normalizedLinkedPhone
                    : UserProfileSanitizer.normalizePhone(linkedParticipant.getPhone());
            patientEmail = linkedParticipant == null
                    ? normalizedLinkedEmail
                    : UserProfileSanitizer.normalizeEmail(linkedParticipant.getEmail());
        }

        AppointmentRequest updatedRequest = createSnapshotBackedRequest(
                existingRequest.getId(),
                bookingRequestDraft,
                patientUserId,
                guardianUserId,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail
        );
        int requestIndex = appointmentRequests.indexOf(existingRequest);
        appointmentRequests.set(requestIndex, updatedRequest);
        return updatedRequest;
    }

    private boolean hasSelectablePaymentMethod(@Nullable BookingRequestDraft bookingRequestDraft) {
        if (bookingRequestDraft == null) {
            return false;
        }
        BookingPaymentMethod paymentMethod = bookingRequestDraft.getPaymentMethod();
        return paymentMethod != null && paymentMethod.isSelectableForRequest();
    }

    @Nullable
    public synchronized AppointmentRequest cancelAppointmentRequest(User currentUser, String requestId) {
        if (currentUser.getRole() != UserRole.PATIENT && currentUser.getRole() != UserRole.GUARDIAN) {
            return null;
        }

        AppointmentRequest existingRequest = findAppointmentRequest(requestId);
        if (existingRequest == null
                || !canCancelRequest(existingRequest)
                || !matchesRequestOwner(existingRequest, currentUser.getId(), currentUser.getRole())) {
            return null;
        }

        existingRequest.setStatus(AppointmentStatus.CANCELED);
        CompanionSession session = findSessionByRequestId(requestId);
        if (session != null && session.getStatus() != SessionStatus.COMPLETED) {
            session.setStatus(SessionStatus.CANCELED);
        }
        return existingRequest;
    }

    @Nullable
    public synchronized AppointmentRequest createLinkedAppointmentRequest(
            String patientUserId,
            String guardianUserId,
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes
    ) {
        User patient = findUserById(patientUserId);
        User guardian = findUserById(guardianUserId);
        if (patient == null || guardian == null) {
            return null;
        }

        AppointmentRequest request = new AppointmentRequest(
                "request-" + (appointmentRequests.size() + 1),
                normalizeText(patientUserId),
                normalizeText(guardianUserId),
                normalizeText(hospitalName),
                normalizeText(departmentName),
                normalizeText(appointmentAt),
                normalizeText(meetingPlace),
                normalizeText(specialNotes),
                AppointmentStatus.REQUESTED,
                null,
                UserProfileSanitizer.normalizeName(patient.getName()),
                UserProfileSanitizer.normalizePhone(patient.getPhone()),
                UserProfileSanitizer.normalizeEmail(patient.getEmail()),
                UserProfileSanitizer.normalizeName(guardian.getName()),
                UserProfileSanitizer.normalizePhone(guardian.getPhone()),
                UserProfileSanitizer.normalizeEmail(guardian.getEmail())
        );
        appointmentRequests.add(0, request);
        return request;
    }

    @Nullable
    public synchronized ManagerDashboard getManagerDashboard(String managerUserId) {
        // 매니저 홈과 가이드 화면이 한 번에 그려질 수 있도록 관련 데이터를 묶어 반환한다.
        User manager = findUserById(managerUserId);
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (manager == null || session == null) {
            return null;
        }

        AppointmentRequest request = findAppointmentRequest(session.getAppointmentRequestId());
        if (request == null) {
            return null;
        }

        User patient = findUserById(request.getPatientUserId());
        User guardian = findUserById(request.getGuardianUserId());
        HospitalGuide guide = HospitalGuideFallbackFactory.fallbackIfMissing(
                getHospitalGuide(request.getHospitalName(), request.getDepartmentName()),
                request.getHospitalName(),
                request.getDepartmentName()
        );
        SessionReport report = getSessionReport(session.getId());

        if (patient == null || guardian == null) {
            return null;
        }

        return new ManagerDashboard(manager, patient, guardian, request, session, guide, report);
    }

    @Nullable
    public synchronized ManagerDashboard advanceManagerSession(String managerUserId) {
        ManagerDashboard dashboard = getManagerDashboard(managerUserId);
        if (dashboard == null) {
            return null;
        }

        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        if (session.getCurrentStepOrder() >= totalSteps) {
            return dashboard;
        }

        int nextStep = session.getCurrentStepOrder() + 1;
        // 단계가 넘어가면 세션 상태와 요청 상태를 함께 진행 중으로 맞춘다.
        session.setCurrentStepOrder(nextStep);
        session.setStatus(resolveStepStatus(nextStep, totalSteps));
        dashboard.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateGuardianMessage(String managerUserId, String message) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setGuardianUpdate(message);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard appendManagerCompanionChatMessage(
            String managerUserId,
            String message,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        User manager = findUserById(managerUserId);
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (manager == null || session == null) {
            return null;
        }
        long sentAtMillis = System.currentTimeMillis();
        session.addChatMessage(new CompanionChatMessage(
                manager.getRole(),
                normalizeText(message),
                sentAtMillis,
                attachments
        ));
        session.markChatRead(manager.getRole(), sentAtMillis);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized AppointmentRequestDetail appendBookingCompanionChatMessage(
            User currentUser,
            String requestId,
            String message,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        AppointmentRequestDetail detail = getAppointmentRequestDetail(requestId);
        if (detail == null || !matchesRequestOwner(detail.getAppointmentRequest(), currentUser.getId(), currentUser.getRole())) {
            return null;
        }
        CompanionSession session = detail.getSession();
        if (session == null) {
            return null;
        }
        long sentAtMillis = System.currentTimeMillis();
        session.addChatMessage(new CompanionChatMessage(
                currentUser.getRole(),
                normalizeText(message),
                sentAtMillis,
                attachments
        ));
        session.markChatRead(currentUser.getRole(), sentAtMillis);
        return getAppointmentRequestDetail(requestId);
    }

    @Nullable
    public synchronized ManagerDashboard markManagerCompanionChatRead(String managerUserId) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.markChatRead(UserRole.MANAGER, System.currentTimeMillis());
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized AppointmentRequestDetail markBookingCompanionChatRead(
            User currentUser,
            String requestId
    ) {
        AppointmentRequestDetail detail = getAppointmentRequestDetail(requestId);
        if (detail == null
                || !matchesRequestOwner(detail.getAppointmentRequest(), currentUser.getId(), currentUser.getRole())) {
            return null;
        }
        CompanionSession session = detail.getSession();
        if (session == null) {
            return null;
        }
        session.markChatRead(currentUser.getRole(), System.currentTimeMillis());
        return getAppointmentRequestDetail(requestId);
    }

    @Nullable
    public synchronized ManagerDashboard saveCompanionLocationAlert(
            String managerUserId,
            CompanionLocationAlertStage stage
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null || stage == null) {
            return null;
        }
        if (!session.getLocationAlertStage().canAdvanceTo(stage)) {
            return getManagerDashboard(managerUserId);
        }
        session.setLocationAlertStage(stage);
        session.setLocationAlertSentAtMillis(System.currentTimeMillis());
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateLocationSummary(String managerUserId, String summary) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setLocationSummary(summary);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateSharedLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String summary
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        long capturedAtMillis = System.currentTimeMillis();
        session.setLocationSummary(summary);
        session.recordSharedLocation(latitude, longitude, summary, capturedAtMillis);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateLiveLocationSharingState(String managerUserId, boolean active) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        long startedAtMillis = session.getLiveLocationSharingStartedAtMillis();
        if (active && startedAtMillis <= 0L) {
            startedAtMillis = System.currentTimeMillis();
        }
        session.updateLiveLocationSharing(active, startedAtMillis);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateFieldPhotoNote(String managerUserId, String note) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setFieldPhotoNote(note);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateMedicationNote(String managerUserId, String note) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setMedicationNote(note);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updatePharmacySummary(String managerUserId, String summary) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPharmacySummary(summary);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPrescriptionCollected(prescriptionCollected);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updatePharmacyCompleted(String managerUserId, boolean pharmacyCompleted) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPharmacyCompleted(pharmacyCompleted);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setMedicationGuidanceCompleted(medicationGuidanceCompleted);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized ManagerDashboard saveSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String nextVisitAt
    ) {
        return saveSessionReport(
                managerUserId,
                summary,
                treatmentNotes,
                medicationNotes,
                "",
                "",
                "",
                null,
                "",
                nextVisitAt
        );
    }

    @Nullable
    public synchronized ManagerDashboard saveSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt
    ) {
        CompanionSession session = getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }

        SessionReport existingReport = getSessionReport(session.getId());
        if (existingReport != null) {
            sessionReports.remove(existingReport);
        }

        // 리포트 저장이 완료되면 세션과 요청 상태를 모두 종료 상태로 바꾼다.
        SessionReport report = new SessionReport(
                "report-" + session.getId(),
                session.getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisitAt
        );
        sessionReports.add(report);
        session.updateLiveLocationSharing(false, 0L);
        session.setStatus(SessionStatus.COMPLETED);

        AppointmentRequest request = findAppointmentRequest(session.getAppointmentRequestId());
        if (request != null) {
            request.setStatus(AppointmentStatus.COMPLETED);
        }

        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public synchronized CompanionSession findSessionByRequestId(String requestId) {
        for (CompanionSession session : companionSessions) {
            if (session.getAppointmentRequestId().equals(requestId)) {
                return session;
            }
        }
        return null;
    }

    public synchronized boolean isManagerAvailable(String managerUserId) {
        for (CompanionSession session : companionSessions) {
            if (!session.getManagerUserId().equals(managerUserId)) {
                continue;
            }
            if (isActiveSession(session)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public synchronized AppointmentRequest assignManagerToRequest(String requestId, String managerUserId) {
        AppointmentRequest request = findAppointmentRequest(requestId);
        if (request == null || request.getStatus() != AppointmentStatus.REQUESTED) {
            return null;
        }
        if (!hasLinkedParticipants(request) || !isManagerAvailable(managerUserId)) {
            return null;
        }
        if (findSessionByRequestId(requestId) != null) {
            return null;
        }

        request.assignManager(managerUserId);
        companionSessions.add(new CompanionSession(
                "session-" + requestId,
                requestId,
                managerUserId,
                1,
                SessionStatus.READY,
                "",
                "",
                "",
                "",
                "",
                false
        ));
        return request;
    }

    @Nullable
    public synchronized HospitalGuide saveHospitalGuide(
            String hospitalName,
            String departmentName,
            List<String> stepLines
    ) {
        List<GuideStep> steps = buildGuideSteps(stepLines);
        if (steps.isEmpty()) {
            return null;
        }

        String normalizedHospital = normalizeText(hospitalName);
        String normalizedDepartment = normalizeText(departmentName);
        HospitalGuide existingGuide = getHospitalGuide(normalizedHospital, normalizedDepartment);
        HospitalGuide updatedGuide = new HospitalGuide(
                existingGuide == null ? "guide-" + (hospitalGuides.size() + 1) : existingGuide.getId(),
                normalizedHospital,
                normalizedDepartment,
                steps
        );

        if (existingGuide == null) {
            hospitalGuides.add(updatedGuide);
        } else {
            int guideIndex = hospitalGuides.indexOf(existingGuide);
            hospitalGuides.set(guideIndex, updatedGuide);
        }
        return updatedGuide;
    }

    public synchronized boolean deleteHospitalGuide(String guideId) {
        HospitalGuide targetGuide = null;
        for (HospitalGuide guide : hospitalGuides) {
            if (guide.getId().equals(guideId)) {
                targetGuide = guide;
                break;
            }
        }
        if (targetGuide == null) {
            return false;
        }
        return hospitalGuides.remove(targetGuide);
    }

    @Nullable
    AppointmentRequest findAppointmentRequest(String appointmentRequestId) {
        for (AppointmentRequest request : appointmentRequests) {
            if (request.getId().equals(appointmentRequestId)) {
                return request;
            }
        }
        return null;
    }

    @Nullable
    CompanionSession getPrimaryManagerSession(String managerUserId) {
        for (CompanionSession session : companionSessions) {
            if (session.getManagerUserId().equals(managerUserId) && isActiveSession(session)) {
                return session;
            }
        }
        return null;
    }

    boolean canCancelRequest(AppointmentRequest request) {
        return request.getStatus() == AppointmentStatus.REQUESTED
                || request.getStatus() == AppointmentStatus.MATCHED;
    }

    boolean isActiveSession(CompanionSession session) {
        return session.getStatus() != SessionStatus.COMPLETED
                && session.getStatus() != SessionStatus.CANCELED;
    }

    void appendAdminActionArtifacts(
            AdminActionSourceType sourceType,
            AdminActionNotificationLevel level,
            String requestId,
            String inquiryId,
            String title,
            String body,
            String actionSummary,
            String note,
            String actorName,
            long createdAtMillis
    ) {
        AdminActionNotification notification = new AdminActionNotification(
                "admin-notification-" + createdAtMillis + "-" + adminActionNotifications.size(),
                sourceType,
                level,
                normalizeText(requestId),
                normalizeText(inquiryId),
                normalizeText(title),
                normalizeText(body),
                normalizeText(actorName),
                createdAtMillis,
                false,
                0L,
                false,
                0L,
                ""
        );
        adminActionNotifications.add(0, notification);
        appendActionDeliveryRecords(
                notification,
                AdminActionDeliveryTrigger.NOTIFICATION_CREATED,
                AdminActionDeliveryStatus.SENT,
                AdminActionDeliveryStatus.CONFIRMED,
                buildPushDeliveryNote(notification),
                "운영 피드 타임라인 반영을 완료했습니다.",
                createdAtMillis
        );
        adminAuditLogs.add(0, new AdminAuditLogEntry(
                "admin-audit-" + createdAtMillis + "-" + adminAuditLogs.size(),
                sourceType,
                normalizeText(requestId),
                normalizeText(inquiryId),
                normalizeText(actionSummary),
                normalizeText(note),
                normalizeText(actorName),
                createdAtMillis
        ));
    }

    private void appendActionDeliveryRecords(
            AdminActionNotification notification,
            AdminActionDeliveryTrigger trigger,
            AdminActionDeliveryStatus pushStatus,
            AdminActionDeliveryStatus feedStatus,
            String pushNote,
            String feedNote,
            long createdAtMillis
    ) {
        adminActionDeliveries.add(0, new AdminActionDeliveryRecord(
                "admin-delivery-" + createdAtMillis + "-push-" + adminActionDeliveries.size(),
                notification.getId(),
                notification.getSourceType(),
                trigger,
                AdminActionDeliveryChannel.APP_PUSH,
                pushStatus,
                notification.getRequestId(),
                notification.getInquiryId(),
                notification.getTitle(),
                notification.getBody(),
                "관리자 앱 푸시",
                normalizeText(pushNote),
                createdAtMillis,
                createdAtMillis
        ));
        adminActionDeliveries.add(0, new AdminActionDeliveryRecord(
                "admin-delivery-" + createdAtMillis + "-feed-" + adminActionDeliveries.size(),
                notification.getId(),
                notification.getSourceType(),
                trigger,
                AdminActionDeliveryChannel.OPERATIONS_FEED,
                feedStatus,
                notification.getRequestId(),
                notification.getInquiryId(),
                notification.getTitle(),
                notification.getBody(),
                "운영 피드",
                normalizeText(feedNote),
                createdAtMillis,
                createdAtMillis
        ));
    }

    private String buildPushDeliveryNote(AdminActionNotification notification) {
        if (notification.getPriority().getSortOrder() >= 120) {
            return "관리자 앱 푸시 채널로 우선 확인 알림을 전달했습니다.";
        }
        return "관리자 앱 푸시 채널로 운영 확인 알림을 전달했습니다.";
    }

    private String buildSettlementNotificationTitle(AdminSettlementStatus status) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return "정산 재확인 요청 저장";
        }
        return "정산 후속 확인 저장";
    }

    private String buildSettlementNotificationBody(
            AppointmentRequest request,
            AdminSettlementStatus status
    ) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return buildActionRequestLabel(request) + "을 정산 재확인 대상으로 표시했습니다.";
        }
        return buildActionRequestLabel(request) + "의 정산 확인 결과를 저장했습니다.";
    }

    private String buildNotificationResolutionNote(
            AdminActionNotification notification,
            boolean resolved
    ) {
        String sourceLabel = buildSourceLabel(notification.getSourceType());
        if (resolved) {
            return sourceLabel + " 알림을 해결 완료 상태로 정리했습니다.";
        }
        return sourceLabel + " 알림을 다시 확인 대상으로 되돌렸습니다.";
    }

    private String buildSourceLabel(AdminActionSourceType sourceType) {
        switch (sourceType) {
            case SETTLEMENT:
                return "정산";
            case EMERGENCY:
                return "긴급 대응";
            case SUPPORT:
            default:
                return "문의 응답";
        }
    }

    private String buildSettlementAuditSummary(AdminSettlementStatus status) {
        if (status == AdminSettlementStatus.NEEDS_REVIEW) {
            return "정산 후속 상태를 재확인으로 저장";
        }
        return "정산 후속 상태를 확인 완료로 저장";
    }

    String buildSupportNotificationBody(SupportInquiry inquiry) {
        return buildInquiryLabel(inquiry) + "에 관리자 응답을 남겼습니다.";
    }

    private String buildActionRequestLabel(AppointmentRequest request) {
        String patientName = normalizeText(request.getPatientName());
        if (!patientName.isEmpty()) {
            return patientName + " 예약";
        }
        return normalizeText(request.getId());
    }

    private String buildInquiryLabel(SupportInquiry inquiry) {
        String managerName = normalizeText(inquiry.getManagerName());
        if (!managerName.isEmpty()) {
            return managerName + " 문의";
        }
        return normalizeText(inquiry.getId());
    }

    SessionStatus resolveStepStatus(int stepOrder, int totalSteps) {
        if (stepOrder <= 1) {
            return SessionStatus.MEETING;
        }
        if (stepOrder == 2) {
            return SessionStatus.WAITING;
        }
        if (stepOrder <= 4) {
            return SessionStatus.IN_TREATMENT;
        }
        if (stepOrder < totalSteps) {
            return SessionStatus.PAYMENT;
        }
        return SessionStatus.PAYMENT;
    }

    boolean matchesRequestOwner(AppointmentRequest request, String userId, UserRole role) {
        if (role == UserRole.PATIENT) {
            return userId.equals(request.getPatientUserId());
        }
        if (role == UserRole.GUARDIAN) {
            return userId.equals(request.getGuardianUserId());
        }
        return false;
    }

    boolean hasLinkedParticipants(AppointmentRequest request) {
        return !normalizeText(request.getPatientUserId()).isEmpty()
                && !normalizeText(request.getGuardianUserId()).isEmpty();
    }

    AppointmentRequest createSnapshotBackedRequest(
            String requestId,
            BookingRequestDraft bookingRequestDraft,
            String patientUserId,
            String guardianUserId,
            String patientName,
            String patientPhone,
            String patientEmail,
            String guardianName,
            String guardianPhone,
            String guardianEmail
    ) {
        return new AppointmentRequest(
                requestId,
                patientUserId,
                guardianUserId,
                normalizeText(bookingRequestDraft.getHospitalName()),
                normalizeText(bookingRequestDraft.getDepartmentName()),
                bookingRequestDraft.getHospitalLatitude(),
                bookingRequestDraft.getHospitalLongitude(),
                normalizeText(bookingRequestDraft.getAppointmentAt()),
                normalizeText(bookingRequestDraft.getMeetingPlace()),
                normalizeText(bookingRequestDraft.getSpecialNotes()),
                AppointmentStatus.REQUESTED,
                null,
                patientName,
                patientPhone,
                patientEmail,
                guardianName,
                guardianPhone,
                guardianEmail,
                normalizeText(bookingRequestDraft.getPatientConditionSummary()),
                normalizeText(bookingRequestDraft.getMedicationSummary()),
                bookingRequestDraft.getMobilitySupport().name(),
                bookingRequestDraft.getTripType().name(),
                bookingRequestDraft.getManagerGenderPreference().name(),
                bookingRequestDraft.getPaymentMethod().name(),
                bookingRequestDraft.getCouponType().name(),
                bookingRequestDraft.getPriceSummary().getBasePrice(),
                bookingRequestDraft.getPriceSummary().getOptionSurchargePrice(),
                bookingRequestDraft.getPriceSummary().getCouponDiscountPrice(),
                bookingRequestDraft.getPriceSummary().getFinalPrice(),
                resolveInitialPaymentStatus(bookingRequestDraft),
                normalizeText(bookingRequestDraft.getPaymentApproval().getApprovalCode()),
                normalizeText(bookingRequestDraft.getPaymentApproval().getApprovedAt()),
                normalizeText(bookingRequestDraft.getPaymentApproval().getProviderLabel())
        );
    }

    private String resolveInitialPaymentStatus(BookingRequestDraft bookingRequestDraft) {
        return bookingRequestDraft.getPaymentMethod() == BookingPaymentMethod.BANK_TRANSFER
                ? BookingPaymentStatus.AWAITING_DEPOSIT.name()
                : bookingRequestDraft.getPaymentApproval().getStatus().name();
    }

    @Nullable
    User resolveLinkedParticipant(UserRole expectedRole, String email, String phone) {
        User matchedByEmail = findUserByEmail(email);
        if (matchedByEmail != null && matchedByEmail.getRole() == expectedRole) {
            return matchedByEmail;
        }

        User matchedByPhone = findUserByPhone(phone);
        if (matchedByPhone != null && matchedByPhone.getRole() == expectedRole) {
            return matchedByPhone;
        }
        return null;
    }

    UserRole resolveCounterpartRole(UserRole requesterRole) {
        return requesterRole == UserRole.PATIENT ? UserRole.GUARDIAN : UserRole.PATIENT;
    }

    private List<GuideStep> buildGuideSteps(List<String> stepLines) {
        List<GuideStep> steps = new ArrayList<>();
        int order = 1;
        for (String rawLine : stepLines) {
            String line = normalizeText(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = splitGuideLine(line, order);
            steps.add(new GuideStep(order, parts[0], parts[1]));
            order++;
        }
        return steps;
    }

    private String[] splitGuideLine(String line, int order) {
        int separatorIndex = findGuideSeparatorIndex(line);
        if (separatorIndex < 0) {
            return new String[]{"단계 " + order, line};
        }

        String title = normalizeText(line.substring(0, separatorIndex));
        String description = normalizeText(line.substring(separatorIndex + 1));
        if (title.isEmpty() || description.isEmpty()) {
            return new String[]{"단계 " + order, line};
        }
        return new String[]{title, description};
    }

    private int findGuideSeparatorIndex(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0) {
            return colonIndex;
        }
        int barIndex = line.indexOf('|');
        if (barIndex >= 0) {
            return barIndex;
        }
        return line.indexOf('-');
    }

    String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private ManagerDocumentStatus resolveManagerDocumentStatus(String managerUserId) {
        ManagerDocumentStatus savedStatus = managerDocumentStatusesByUserId.get(managerUserId);
        if (savedStatus != null) {
            return savedStatus;
        }
        if (!normalizeText(managerDocumentSummariesByUserId.get(managerUserId)).isEmpty()) {
            return ManagerDocumentStatus.PENDING_REVIEW;
        }
        return ManagerDocumentStatus.NOT_SUBMITTED;
    }

    private void appendManagerDocumentHistory(
            String managerUserId,
            ManagerDocumentHistoryEntry historyEntry
    ) {
        List<ManagerDocumentHistoryEntry> historyEntries = managerDocumentHistoriesByUserId.get(managerUserId);
        if (historyEntries == null) {
            historyEntries = new ArrayList<>();
            managerDocumentHistoriesByUserId.put(managerUserId, historyEntries);
        }
        historyEntries.add(0, historyEntry);
    }

    private void appendManagerDocumentSubmissionHistory(String managerUserId, User manager) {
        appendManagerDocumentHistory(
                managerUserId,
                new ManagerDocumentHistoryEntry(
                        ManagerDocumentHistoryEventType.SUBMITTED,
                        managerDocumentUpdatedAtByUserId.get(managerUserId),
                        manager.getName(),
                        managerDocumentSummariesByUserId.getOrDefault(managerUserId, ""),
                        ""
                )
        );
    }

    private String normalizeKey(String value) {
        // 이메일과 내부 키는 사용자 기기 로케일과 무관하게 동일한 규칙으로 정규화한다.
        return value.toLowerCase(Locale.ROOT);
    }

    private void seedUsers() {
        // 로그인과 화면 데모에 사용할 기본 사용자 계정을 미리 만든다.
        users.add(new User(
                "patient-1",
                UserRole.PATIENT,
                "이현우",
                "patient@bodeul.app",
                "010-0000-0001"
        ));
        users.add(new User(
                "guardian-1",
                UserRole.GUARDIAN,
                "김유나",
                "guardian@bodeul.app",
                "010-0000-0002"
        ));
        users.add(new User(
                "manager-1",
                UserRole.MANAGER,
                "김승민",
                "manager@bodeul.app",
                "010-0000-0003"
        ));
        users.add(new User(
                "admin-1",
                UserRole.ADMIN,
                "관리자",
                "admin@bodeul.app",
                "010-0000-0004"
        ));

        passwordsByEmail.put("patient@bodeul.app", "bodeul1234");
        passwordsByEmail.put("guardian@bodeul.app", "bodeul1234");
        passwordsByEmail.put("manager@bodeul.app", "bodeul1234");
        passwordsByEmail.put("admin@bodeul.app", "bodeul1234");

        managerDocumentSummariesByUserId.put(
                "manager-1",
                "직무 자격 증빙 원본 업로드 완료"
        );
        managerDocumentStatusesByUserId.put("manager-1", ManagerDocumentStatus.APPROVED);
        managerDocumentUpdatedAtByUserId.put("manager-1", 1760490000000L);
        managerDocumentReviewedAtByUserId.put("manager-1", 1760500800000L);
        managerDocumentReviewedByNameByUserId.put("manager-1", "관리자");
        managerDocumentReviewNotesByUserId.put(
                "manager-1",
                "관리자 검토를 마쳤습니다. 이번 주 일정만 최신으로 유지해 주세요."
        );
        appendManagerDocumentHistory(
                "manager-1",
                new ManagerDocumentHistoryEntry(
                        ManagerDocumentHistoryEventType.APPROVED,
                        1760500800000L,
                        "관리자",
                        managerDocumentSummariesByUserId.get("manager-1"),
                        managerDocumentReviewNotesByUserId.get("manager-1")
                )
        );
        appendManagerDocumentHistory(
                "manager-1",
                new ManagerDocumentHistoryEntry(
                        ManagerDocumentHistoryEventType.SUBMITTED,
                        1760490000000L,
                        "김승민",
                        managerDocumentSummariesByUserId.get("manager-1"),
                        ""
                )
        );
        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> defaultManagerDocumentFiles =
                new HashMap<>();
        defaultManagerDocumentFiles.put(
                ManagerDocumentFileType.LICENSE,
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.LICENSE,
                        "manager-documents/manager-1/license/mock-job-qualification.png",
                        "mock-job-qualification.png",
                        "image/png",
                        1760490000000L,
                        "android.resource://com.example.bodeul/drawable/ic_launcher_foreground"
                )
        );
        managerDocumentFilesByUserId.put("manager-1", defaultManagerDocumentFiles);
        managerAvailabilitySummariesByUserId.put(
                "manager-1",
                "평일 09:00-18:00, 토요일 오전 활동 가능"
        );
    }

    private void seedAppointmentRequests() {
        appointmentRequests.add(new AppointmentRequest(
                "request-1",
                "patient-1",
                "guardian-1",
                "서울대학교병원",
                "신경과",
                "2026-04-15 10:30",
                "본관 1층 안내 데스크",
                "어지럼 증상과 복용 중인 약 정보를 함께 확인해주세요.",
                AppointmentStatus.MATCHED,
                "manager-1",
                "이현우",
                "010-0000-0001",
                "patient@bodeul.app",
                "김유나",
                "010-0000-0002",
                "guardian@bodeul.app"
        ));
    }

    private void seedCompanionSessions() {
        CompanionSession activeSession = new CompanionSession(
                "session-1",
                "request-1",
                "manager-1",
                2,
                SessionStatus.MEETING,
                "환자분과 만나 접수를 진행하고 있습니다.",
                "서울대학교병원 본관 1층 안내 데스크 근처입니다.",
                "접수 번호표와 진료 안내를 확인했습니다.",
                "복용 중인 약이 있습니다.",
                "",
                false,
                37.579617,
                126.999016,
                1760503200000L,
                false,
                0L,
                Arrays.asList(
                        new CompanionLocationHistoryEntry(
                                37.579250,
                                126.998750,
                                "서울대학교병원 앞에 도착했습니다.",
                                1760502600000L
                        ),
                        new CompanionLocationHistoryEntry(
                                37.579617,
                                126.999016,
                                "본관 1층 안내 데스크로 이동했습니다.",
                                1760503200000L
                        )
                ),
                Collections.emptyList()
        );
        activeSession.setPrescriptionCollected(true);
        activeSession.setPharmacyCompleted(true);
        activeSession.setMedicationGuidanceCompleted(false);
        companionSessions.add(activeSession);
    }

    private void seedHospitalGuides() {
        hospitalGuides.add(new HospitalGuide(
                "guide-1",
                "서울대학교병원",
                "신경과",
                Arrays.asList(
                        new GuideStep(1, "환자 접촉", "환자분 도착 여부를 확인하고 보호자에게 출발 상황을 공유합니다."),
                        new GuideStep(2, "간편 등록", "접수 창구에서 예약 정보와 신분증을 확인합니다."),
                        new GuideStep(3, "진료 접수", "진료과와 대기 순서를 확인하고 필요한 서류를 제출합니다."),
                        new GuideStep(4, "진료 완료", "진료 결과와 다음 안내 사항을 메모합니다."),
                        new GuideStep(5, "수납 처리", "수납 및 검사 예약 여부를 확인합니다."),
                        new GuideStep(6, "약국 방문", "처방전을 수령하고 약 복용법을 정리합니다."),
                        new GuideStep(7, "환자 귀가(서비스 종료)", "귀가 동선을 확인하고 보호자에게 최종 상황을 전달합니다.")
                )
        ));
    }

    private void seedSupportInquiries() {
        supportInquiries.add(new SupportInquiry(
                "support-seed-2",
                "manager-1",
                "김승민",
                SupportInquiryCategory.SETTLEMENT,
                "처리 후 정산 확인 요청",
                "최종 수납 금액과 고객 결제 내역을 다시 확인해야 합니다.",
                SupportInquiryStatus.ANSWERED,
                1760655600000L,
                "운영 카드 정산 영역에서 승인 번호를 다시 입력해 주세요. 확인 뒤 고객에게 최종 안내를 전달해 주세요.",
                1760659200000L,
                "관리자"
        ));
        supportInquiries.add(new SupportInquiry(
                "support-seed-1",
                "manager-1",
                "김승민",
                SupportInquiryCategory.MATCHING,
                "매칭 시간 조정 문의",
                "다음 주 오전 시간대에 이동 가능한 시간을 확인하고 싶습니다.",
                SupportInquiryStatus.RECEIVED,
                1760662800000L,
                "",
                0L,
                ""
        ));
    }
}
