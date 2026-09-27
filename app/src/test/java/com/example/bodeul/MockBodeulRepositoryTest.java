package com.example.bodeul;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAdminRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.AdminActionNotification;
import com.example.bodeul.domain.model.AdminActionNotificationFilterKey;
import com.example.bodeul.domain.model.AdminActionNotificationLevel;
import com.example.bodeul.domain.model.AdminActionNotificationPriority;
import com.example.bodeul.domain.model.AdminActionNotificationState;
import com.example.bodeul.domain.model.AdminActionDeliveryChannel;
import com.example.bodeul.domain.model.AdminActionDeliveryFilterKey;
import com.example.bodeul.domain.model.AdminActionDeliveryPriority;
import com.example.bodeul.domain.model.AdminActionDeliveryRecord;
import com.example.bodeul.domain.model.AdminActionDeliverySlaStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryState;
import com.example.bodeul.domain.model.AdminActionDeliveryStatus;
import com.example.bodeul.domain.model.AdminActionDeliveryTrigger;
import com.example.bodeul.domain.model.AdminActionSourceType;
import com.example.bodeul.domain.model.AdminAuditLogEntry;
import com.example.bodeul.domain.model.AdminDashboard;
import com.example.bodeul.domain.model.AdminRequestActionOverview;
import com.example.bodeul.domain.model.AdminSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.BookingHospitalOption;
import com.example.bodeul.domain.model.BookingManagerGenderPreference;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPaymentApproval;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.BookingTripType;
import com.example.bodeul.domain.model.ClientSupportCategory;
import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 목업 저장소가 현재 화면 구조와 같은 예약 객체를 안정적으로 저장하는지 검증한다.
 */
public class MockBodeulRepositoryTest {
    @Test
    public void seededAppointment_usesMatchingSeoulNationalUniversityHospitalLocation() {
        MockBodeulRepository repository = new MockBodeulRepository();

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail("request-1");

        assertNotNull(detail);
        assertNotNull(detail.getSession());
        assertEquals("서울대학교병원", detail.getAppointmentRequest().getHospitalName());
        assertEquals(37.579617, detail.getSession().getSharedLatitude(), 0.000001);
        assertEquals(126.999016, detail.getSession().getSharedLongitude(), 0.000001);
        assertTrue(detail.getSession().getSharedLocationHistory().stream().allMatch(location ->
                Math.abs(location.getLatitude() - 37.5796) < 0.001
                        && Math.abs(location.getLongitude() - 126.9990) < 0.001));
    }

    @Test
    public void managerRepository_advanceCurrentStep_movesToNextMockGuideStep() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockManagerRepository managerRepository = new MockManagerRepository(repository);
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "mock-guide-manager",
                "mock-guide-manager@bodeul.app",
                "010-2222-3333",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest request = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "목업 가이드 병원",
                "가정의학과",
                "2026-09-02 09:00",
                "본관 안내 데스크",
                "목업 단계 전진 검증"
        );
        assertNotNull(request);
        assertNotNull(repository.assignManagerToRequest(request.getId(), manager.getId()));

        AtomicReference<ManagerDashboard> initialDashboardRef = new AtomicReference<>();
        managerRepository.getManagerDashboard(
                manager.getId(),
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        initialDashboardRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        ManagerDashboard initialDashboard = initialDashboardRef.get();
        assertNotNull(initialDashboard);
        assertEquals(1, initialDashboard.getSession().getCurrentStepOrder());
        assertEquals(
                "LEGACY_CORE_PATIENT_CONTACT",
                initialDashboard.getSession().getCurrentStepCode()
        );

        AtomicReference<ManagerDashboard> advancedDashboardRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        managerRepository.advanceCurrentStep(
                manager.getId(),
                initialDashboard.getSession().getId(),
                initialDashboard.getSession().getCurrentStepCode(),
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        advancedDashboardRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        errorRef.set(message);
                    }
                }
        );

        assertNull(errorRef.get());
        ManagerDashboard advancedDashboard = advancedDashboardRef.get();
        assertNotNull(advancedDashboard);
        assertEquals(2, advancedDashboard.getSession().getCurrentStepOrder());
        assertEquals(
                "LEGACY_CORE_RECEPTION_PREPARATION",
                advancedDashboard.getSession().getCurrentStepCode()
        );
    }

    @Test
    public void createAppointmentRequest_patientLinksExistingGuardianByEmail() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(patient);
        assertNotNull(guardian);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                createDraft(
                        "safe-hospital",
                        "orthopedics",
                        "2026-04-18 09:30",
                        "main-lobby",
                        guardian.getName(),
                        guardian.getPhone(),
                        guardian.getEmail()
                )
        );

        assertNotNull(created);
        assertEquals(AppointmentStatus.REQUESTED, created.getStatus());
        assertEquals(patient.getId(), created.getPatientUserId());
        assertEquals(guardian.getId(), created.getGuardianUserId());
        assertEquals(guardian.getName(), created.getGuardianName());
        assertEquals(guardian.getPhone(), created.getGuardianPhone());
        assertEquals("WHEELCHAIR", created.getMobilitySupportCode());
        assertEquals("ROUND_TRIP", created.getTripTypeCode());
        assertEquals("EASY_PAY", created.getPaymentMethodCode());
        assertEquals("FAMILY", created.getCouponCode());
        assertEquals(82000, created.getFinalPrice());

        List<AppointmentRequest> requests = repository.getAppointmentRequestsForUser(
                patient.getId(),
                UserRole.PATIENT
        );
        assertEquals(created.getId(), requests.get(0).getId());
    }

    @Test
    public void createAppointmentRequest_bankTransferStartsAwaitingDepositWithoutApprovalEvidence() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");

        assertNotNull(patient);

        BookingRequestDraft bankTransferDraft = createDraft(
                "bank-hospital",
                "internal",
                "2026-09-03 10:30",
                "main-lobby",
                "guardian-bank",
                "01012345678",
                ""
        ).toBuilder()
                .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                .paymentApproval(BookingPaymentApproval.simulated("MVP 무통장입금 합성 안내"))
                .build();

        AppointmentRequest created = repository.createAppointmentRequest(patient, bankTransferDraft);

        assertNotNull(created);
        assertEquals("BANK_TRANSFER", created.getPaymentMethodCode());
        assertEquals("AWAITING_DEPOSIT", created.getPaymentStatusCode());
        assertEquals("", created.getPaymentApprovalCode());
        assertEquals("", created.getPaymentApprovedAt());
    }

    @Test
    public void createAndUpdateAppointmentRequest_unknownPaymentMethodFailClosed() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");

        assertNotNull(patient);

        BookingRequestDraft validDraft = createDraft(
                "valid-hospital",
                "internal",
                "2026-09-04 11:00",
                "main-lobby",
                "guardian-valid",
                "01087654321",
                ""
        );
        BookingRequestDraft unknownDraft = validDraft.toBuilder()
                .paymentMethod(BookingPaymentMethod.UNKNOWN)
                .build();

        assertNull(repository.createAppointmentRequest(patient, unknownDraft));

        AppointmentRequest created = repository.createAppointmentRequest(patient, validDraft);
        assertNotNull(created);
        assertNull(repository.updateAppointmentRequest(patient, created.getId(), unknownDraft));
        AppointmentRequest persisted = repository.getAppointmentRequestsForUser(
                        patient.getId(),
                        UserRole.PATIENT
                ).stream()
                .filter(request -> created.getId().equals(request.getId()))
                .findFirst()
                .orElse(null);
        assertNotNull(persisted);
        assertEquals("valid-hospital", persisted.getHospitalName());
    }

    @Test
    public void createAppointmentRequest_guardianKeepsPatientSnapshotWhenAccountIsMissing() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(guardian);

        AppointmentRequest created = repository.createAppointmentRequest(
                guardian,
                createDraft(
                        "safe-hospital",
                        "orthopedics",
                        "2026-04-19 11:00",
                        "front-gate",
                        "new patient",
                        "01012341234",
                        ""
                )
        );

        assertNotNull(created);
        assertEquals("", created.getPatientUserId());
        assertEquals("new patient", created.getPatientName());
        assertEquals("010-1234-1234", created.getPatientPhone());
        assertEquals(guardian.getId(), created.getGuardianUserId());
        assertEquals(guardian.getName(), created.getGuardianName());
    }

    @Test
    public void updateAppointmentRequest_requestedOwnerCanRewriteScheduleAndContact() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");

        assertNotNull(patient);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                createDraft(
                        "first-hospital",
                        "internal",
                        "2026-04-24 11:20",
                        "gate-1",
                        "guardian-a",
                        "01011112222",
                        ""
                )
        );

        assertNotNull(created);

        AppointmentRequest updated = repository.updateAppointmentRequest(
                patient,
                created.getId(),
                createDraft(
                        "second-hospital",
                        "rehab",
                        "2026-04-25 15:40",
                        "gate-2",
                        "guardian-b",
                        "01022223333",
                        ""
                )
        );

        assertNotNull(updated);
        assertEquals("second-hospital", updated.getHospitalName());
        assertEquals("rehab", updated.getDepartmentName());
        assertEquals("2026-04-25 15:40", updated.getAppointmentAt());
        assertEquals("gate-2", updated.getMeetingPlace());
        assertEquals("guardian-b", updated.getGuardianName());
        assertEquals("010-2222-3333", updated.getGuardianPhone());
        assertEquals(AppointmentStatus.REQUESTED, updated.getStatus());
    }

    @Test
    public void cancelAppointmentRequest_requestedOwnerCanCancelOwnRequest() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");

        assertNotNull(patient);

        AppointmentRequest created = repository.createAppointmentRequest(
                patient,
                createDraft(
                        "cancel-hospital",
                        "surgery",
                        "2026-04-26 10:10",
                        "gate-3",
                        "guardian-c",
                        "01033334444",
                        ""
                )
        );

        assertNotNull(created);

        AppointmentRequest canceled = repository.cancelAppointmentRequest(patient, created.getId());

        assertNotNull(canceled);
        assertEquals(AppointmentStatus.CANCELED, canceled.getStatus());
    }

    @Test
    public void cancelAppointmentRequest_matchedOwnerCancelsLinkedSessionTogether() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "manager-cancel-test",
                "manager-cancel-test@bodeul.app",
                "010-9999-0002",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "linked-hospital",
                "rehab",
                "2026-04-27 13:20",
                "main-lobby",
                "manual-cancel-test"
        );

        assertNotNull(linkedRequest);

        AppointmentRequest matched = repository.assignManagerToRequest(linkedRequest.getId(), manager.getId());
        assertNotNull(matched);
        assertEquals(AppointmentStatus.MATCHED, matched.getStatus());

        AppointmentRequest canceled = repository.cancelAppointmentRequest(patient, linkedRequest.getId());
        CompanionSession session = repository.findSessionByRequestId(linkedRequest.getId());

        assertNotNull(canceled);
        assertNotNull(session);
        assertEquals(AppointmentStatus.CANCELED, canceled.getStatus());
        assertEquals(SessionStatus.CANCELED, session.getStatus());
    }

    @Test
    public void assignManagerToRequest_createsMatchedRequestAndSession() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "manager-two",
                "new-manager@bodeul.app",
                "010-9999-0001",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "seoul-internal",
                "neurology",
                "2026-04-20 14:00",
                "main-lobby",
                "manual-match-test"
        );

        assertNotNull(linkedRequest);

        AppointmentRequest assigned = repository.assignManagerToRequest(linkedRequest.getId(), manager.getId());
        assertNotNull(assigned);
        assertEquals(AppointmentStatus.MATCHED, assigned.getStatus());
        assertEquals(manager.getId(), assigned.getManagerUserId());
        assertNotNull(repository.findSessionByRequestId(linkedRequest.getId()));
    }

    @Test
    public void supportInquiry_submitAndRespond_updatesInquiryStatusForManager() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.registerUser(
                "support-manager",
                "support-manager@bodeul.app",
                "010-7777-6666",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(manager);

        SupportInquiry createdInquiry = repository.saveSupportInquiry(
                manager.getId(),
                SupportInquiryCategory.SETTLEMENT,
                "정산 문의",
                "정산 예정 시각을 확인하고 싶습니다."
        );

        assertNotNull(createdInquiry);
        assertEquals(SupportInquiryStatus.RECEIVED, createdInquiry.getStatus());

        SupportInquiry respondedInquiry = repository.respondSupportInquiry(
                createdInquiry.getId(),
                "오늘 오후 6시 이전에 순차 반영됩니다.",
                "관리자"
        );

        assertNotNull(respondedInquiry);
        assertEquals(SupportInquiryStatus.ANSWERED, respondedInquiry.getStatus());
        assertEquals("관리자", respondedInquiry.getRespondedByName());
        assertFalse(repository.getSupportInquiries(manager.getId()).isEmpty());
        assertEquals(
                SupportInquiryStatus.ANSWERED,
                repository.getSupportInquiries(manager.getId()).get(0).getStatus()
        );
    }

    @Test
    public void adminActionRecords_saveSettlement_recordAppearsInOverview() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(patient);
        assertNotNull(guardian);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "action-hospital",
                "rehab",
                "2026-04-29 10:00",
                "main-lobby",
                "action-overview-test"
        );

        assertNotNull(linkedRequest);
        assertNotNull(repository.saveSettlementRecord(
                linkedRequest.getId(),
                AdminSettlementStatus.CONFIRMED,
                "결제 승인과 금액을 확인했습니다.",
                "관리자"
        ));
        List<AdminRequestActionOverview> overviews = repository.getAdminRequestActionOverviews();

        assertEquals(1, overviews.size());
        assertEquals(linkedRequest.getId(), overviews.get(0).getRequestId());
        assertNotNull(overviews.get(0).getSettlementRecord());
        assertNull(overviews.get(0).getEmergencyIssueRecord());
        assertEquals(
                AdminSettlementStatus.CONFIRMED,
                overviews.get(0).getSettlementRecord().getStatus()
        );
    }

    @Test
    public void adminActionArtifacts_saveActions_createNotificationsAndAuditLogs() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "admin-action-manager",
                "admin-action-manager@bodeul.app",
                "010-2020-3030",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        int initialNotificationCount = repository.getAdminActionNotifications().size();
        int initialAuditCount = repository.getAdminAuditLogs().size();
        int initialDeliveryCount = repository.getAdminActionDeliveries().size();

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "action-center-hospital",
                "internal",
                "2026-05-06 13:20",
                "main-lobby",
                "admin-action-center-test"
        );
        assertNotNull(linkedRequest);

        SupportInquiry inquiry = repository.saveSupportInquiry(
                manager.getId(),
                SupportInquiryCategory.OTHER,
                "Action center support",
                "Need a follow-up answer."
        );
        assertNotNull(inquiry);

        assertNotNull(repository.saveSettlementRecord(
                linkedRequest.getId(),
                AdminSettlementStatus.NEEDS_REVIEW,
                "Settlement recheck note.",
                "관리자A"
        ));
        assertNotNull(repository.respondSupportInquiry(
                inquiry.getId(),
                "Support response note.",
                "관리자A"
        ));

        List<AdminActionNotification> notifications = repository.getAdminActionNotifications();
        List<AdminAuditLogEntry> auditLogs = repository.getAdminAuditLogs();
        List<AdminActionDeliveryRecord> deliveries = repository.getAdminActionDeliveries();
        AdminActionNotification supportNotification = null;
        AdminActionNotification settlementNotification = null;
        for (AdminActionNotification notification : notifications) {
            if (notification.getSourceType() == AdminActionSourceType.SUPPORT) {
                supportNotification = notification;
            } else if (notification.getSourceType() == AdminActionSourceType.SETTLEMENT) {
                settlementNotification = notification;
            }
        }

        assertEquals(initialNotificationCount + 2, notifications.size());
        assertEquals(initialAuditCount + 2, auditLogs.size());
        assertEquals(initialDeliveryCount + 4, deliveries.size());
        assertNotNull(supportNotification);
        assertNotNull(settlementNotification);
        assertEquals(AdminActionNotificationLevel.INFO, supportNotification.getLevel());
        assertEquals(AdminActionNotificationState.UNREAD, supportNotification.getState());
        assertEquals(AdminActionNotificationPriority.ACTION_REQUIRED, supportNotification.getPriority());
        assertTrue(supportNotification.hasFilterKey(AdminActionNotificationFilterKey.UNREAD));
        assertTrue(supportNotification.hasFilterKey(AdminActionNotificationFilterKey.UNRESOLVED));
        assertEquals(linkedRequest.getId(), settlementNotification.getRequestId());
        assertEquals(AdminActionNotificationPriority.IMMEDIATE, settlementNotification.getPriority());
        assertEquals(AdminActionSourceType.SUPPORT, auditLogs.get(0).getSourceType());
        assertEquals("Support response note.", auditLogs.get(0).getNote());
        boolean foundOperationsFeedDelivery = false;
        int previousPrioritySortOrder = Integer.MAX_VALUE;
        for (AdminActionDeliveryRecord delivery : deliveries) {
            if (delivery.getChannel() == AdminActionDeliveryChannel.OPERATIONS_FEED
                    && delivery.getStatus() == AdminActionDeliveryStatus.CONFIRMED
                    && delivery.getState() == AdminActionDeliveryState.DELIVERED
                    && delivery.getTrigger() == AdminActionDeliveryTrigger.NOTIFICATION_CREATED) {
                foundOperationsFeedDelivery = true;
            }
            assertTrue(delivery.getPriority().getSortOrder() <= previousPrioritySortOrder);
            previousPrioritySortOrder = delivery.getPriority().getSortOrder();
        }
        assertTrue(foundOperationsFeedDelivery);
    }

    @Test
    public void clientSupportRequest_saveRequest_filtersByCurrentUser() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(patient);
        assertNotNull(guardian);

        ClientSupportRequest request = repository.saveClientSupportRequest(
                patient.getId(),
                "request-seed-progress",
                ClientSupportCategory.PROGRESS,
                "진행 상태 문의",
                "현재 위치 갱신 시각을 다시 확인하고 싶습니다."
        );

        assertNotNull(request);
        assertEquals(ClientSupportStatus.RECEIVED, request.getStatus());
        assertEquals(patient.getId(), request.getUserId());
        assertEquals("request-seed-progress", request.getAppointmentRequestId());
        assertFalse(repository.getClientSupportRequests(patient.getId()).isEmpty());
        assertEquals(0, repository.getClientSupportRequests(guardian.getId()).size());
    }

    @Test
    public void adminDashboard_buildDashboard_includesSharedActionOverview() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockAdminRepository adminRepository = new MockAdminRepository(repository);
        User admin = repository.findUserByEmail("admin@bodeul.app");
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(admin);
        assertNotNull(patient);
        assertNotNull(guardian);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "action-overview-hospital",
                "internal",
                "2026-05-08 11:40",
                "main-lobby",
                "admin-action-overview-test"
        );
        assertNotNull(linkedRequest);

        assertNotNull(repository.saveSettlementRecord(
                linkedRequest.getId(),
                AdminSettlementStatus.NEEDS_REVIEW,
                "Shared overview settlement note.",
                "관리자B"
        ));
        final AdminDashboard[] dashboardHolder = new AdminDashboard[1];
        final String[] errorHolder = new String[1];
        adminRepository.getAdminDashboard(admin, new RepositoryCallback<AdminDashboard>() {
            @Override
            public void onSuccess(AdminDashboard result) {
                dashboardHolder[0] = result;
            }

            @Override
            public void onError(String message) {
                errorHolder[0] = message;
            }
        });

        assertNull(errorHolder[0]);
        assertNotNull(dashboardHolder[0]);
        assertNotNull(dashboardHolder[0].getActionOverview());
        assertEquals(
                dashboardHolder[0].getActionNotifications().size(),
                dashboardHolder[0].getActionOverview().getNotificationCount()
        );
        assertEquals(
                dashboardHolder[0].getAuditLogs().size(),
                dashboardHolder[0].getActionOverview().getAuditLogCount()
        );
        assertEquals(
                dashboardHolder[0].getActionDeliveries().size(),
                dashboardHolder[0].getActionOverview().getDeliveryCount()
        );
        assertTrue(dashboardHolder[0].getActionOverview().getUnreadNotificationCount() > 0);
        assertTrue(dashboardHolder[0].getActionOverview().getPendingDeliveryCount() > 0);
        assertTrue(dashboardHolder[0].getActionOverview().getAppPushDeliveryCount() > 0);

        int previousSortOrder = Integer.MAX_VALUE;
        for (AdminActionDeliveryRecord delivery : dashboardHolder[0].getActionDeliveries()) {
            assertTrue(delivery.getPriority().getSortOrder() <= previousSortOrder);
            previousSortOrder = delivery.getPriority().getSortOrder();
        }
    }

    @Test
    public void adminActionNotificationState_markReadAndResolve_updatesNotification() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(patient);
        assertNotNull(guardian);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "action-state-hospital",
                "rehab",
                "2026-05-07 09:10",
                "main-lobby",
                "admin-action-state-test"
        );
        assertNotNull(linkedRequest);
        assertNotNull(repository.saveSettlementRecord(
                linkedRequest.getId(),
                AdminSettlementStatus.NEEDS_REVIEW,
                "Need settlement review.",
                "관리자A"
        ));

        AdminActionNotification createdNotification = repository.getAdminActionNotifications().get(0);
        int initialAuditCount = repository.getAdminAuditLogs().size();
        int initialDeliveryCount = repository.getAdminActionDeliveries().size();

        AdminActionNotification readNotification =
                repository.markAdminActionNotificationRead(createdNotification.getId());
        assertNotNull(readNotification);
        assertTrue(readNotification.isRead());
        assertFalse(readNotification.isResolved());
        assertEquals(AdminActionNotificationState.READ, readNotification.getState());
        assertEquals(AdminActionNotificationPriority.MONITORING, readNotification.getPriority());
        assertFalse(readNotification.hasFilterKey(AdminActionNotificationFilterKey.UNREAD));
        assertTrue(readNotification.hasFilterKey(AdminActionNotificationFilterKey.UNRESOLVED));
        assertEquals(initialDeliveryCount + 2, repository.getAdminActionDeliveries().size());
        assertEquals(
                AdminActionDeliveryTrigger.NOTIFICATION_READ,
                repository.getAdminActionDeliveries().get(0).getTrigger()
        );
        AdminActionDeliveryRecord readPushDelivery = null;
        for (AdminActionDeliveryRecord delivery : repository.getAdminActionDeliveries()) {
            if (delivery.getTrigger() == AdminActionDeliveryTrigger.NOTIFICATION_READ
                    && delivery.getChannel() == AdminActionDeliveryChannel.APP_PUSH) {
                readPushDelivery = delivery;
                break;
            }
        }
        assertNotNull(readPushDelivery);
        assertEquals(AdminActionDeliveryStatus.CONFIRMED, readPushDelivery.getStatus());
        assertEquals(AdminActionDeliveryState.DELIVERED, readPushDelivery.getState());
        assertTrue(readPushDelivery.hasFilterKey(AdminActionDeliveryFilterKey.COMPLETED));

        AdminActionNotification resolvedNotification =
                repository.updateAdminActionNotificationResolved(
                        createdNotification.getId(),
                        true,
                        "관리자A"
                );
        assertNotNull(resolvedNotification);
        assertTrue(resolvedNotification.isRead());
        assertTrue(resolvedNotification.isResolved());
        assertEquals(AdminActionNotificationState.RESOLVED, resolvedNotification.getState());
        assertEquals(AdminActionNotificationPriority.ARCHIVED, resolvedNotification.getPriority());
        assertFalse(resolvedNotification.hasFilterKey(AdminActionNotificationFilterKey.UNRESOLVED));
        assertTrue(resolvedNotification.hasFilterKey(AdminActionNotificationFilterKey.RESOLVED));
        assertEquals("관리자A", resolvedNotification.getResolvedByName());
        assertEquals(initialAuditCount + 1, repository.getAdminAuditLogs().size());
        assertEquals(initialDeliveryCount + 4, repository.getAdminActionDeliveries().size());
        assertEquals(
                AdminActionDeliveryTrigger.NOTIFICATION_RESOLVED,
                repository.getAdminActionDeliveries().get(0).getTrigger()
        );

        AdminActionNotification reopenedNotification =
                repository.updateAdminActionNotificationResolved(
                        createdNotification.getId(),
                        false,
                        "관리자A"
                );
        assertNotNull(reopenedNotification);
        assertTrue(reopenedNotification.isRead());
        assertFalse(reopenedNotification.isResolved());
        assertEquals("", reopenedNotification.getResolvedByName());
        assertEquals(initialAuditCount + 2, repository.getAdminAuditLogs().size());
        assertEquals(initialDeliveryCount + 6, repository.getAdminActionDeliveries().size());
        assertEquals(
                AdminActionDeliveryTrigger.NOTIFICATION_REOPENED,
                repository.getAdminActionDeliveries().get(0).getTrigger()
        );
    }

    @Test
    public void adminActionDeliveryRecord_overduePushRequiresFollowUp() {
        long createdAtMillis = System.currentTimeMillis() - (2L * 60L * 60L * 1000L);
        AdminActionDeliveryRecord record = new AdminActionDeliveryRecord(
                "delivery-overdue",
                "notification-overdue",
                AdminActionSourceType.EMERGENCY,
                AdminActionDeliveryTrigger.NOTIFICATION_CREATED,
                AdminActionDeliveryChannel.APP_PUSH,
                AdminActionDeliveryStatus.SENT,
                "request-overdue",
                "",
                "긴급 알림",
                "긴급 이슈 확인 필요",
                "관리자 앱 푸시",
                "초기 푸시 전송",
                createdAtMillis,
                createdAtMillis
        );

        assertEquals(AdminActionDeliveryState.FOLLOW_UP_REQUIRED, record.getState());
        assertEquals(AdminActionDeliveryPriority.IMMEDIATE, record.getPriority());
        assertEquals(AdminActionDeliverySlaStatus.ATTENTION_REQUIRED, record.getSlaStatus());
        assertTrue(record.hasFilterKey(AdminActionDeliveryFilterKey.FOLLOW_UP_REQUIRED));
    }

    @Test
    public void getAppointmentRequestDetail_returnsParticipantsSessionGuideAndReport() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "detail-manager",
                "detail-manager@bodeul.app",
                "010-8888-7777",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest linkedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "서울중앙병원",
                "정형외과",
                "2026-04-28 10:30",
                "정문 앞",
                "보호자에게 출발 전 연락"
        );
        assertNotNull(linkedRequest);

        repository.saveHospitalGuide(
                "서울중앙병원",
                "정형외과",
                Arrays.asList(
                        "접수: 접수대 확인",
                        "진료: 진료실 이동"
                )
        );
        repository.assignManagerToRequest(linkedRequest.getId(), manager.getId());
        repository.updateLocationSummary(manager.getId(), "병원 정문 도착 후 접수 창구로 이동 중입니다.");
        repository.updateGuardianMessage(manager.getId(), "현재 접수 후 대기 중입니다.");
        repository.updateFieldPhotoNote(manager.getId(), "접수표와 진료 대기 화면을 확인했습니다.");
        repository.saveSessionReport(
                manager.getId(),
                "진료를 마치고 복약 안내를 받았습니다.",
                "다음 주 재진 권고",
                "식후 복용",
                "2026-05-05 11:00"
        );

        AppointmentRequestDetail detail = repository.getAppointmentRequestDetail(linkedRequest.getId());

        assertNotNull(detail);
        assertNotNull(detail.getPatient());
        assertNotNull(detail.getGuardian());
        assertNotNull(detail.getManager());
        assertNotNull(detail.getSession());
        assertNotNull(detail.getHospitalGuide());
        assertNotNull(detail.getSessionReport());
        assertEquals(linkedRequest.getId(), detail.getAppointmentRequest().getId());
        assertEquals("서울중앙병원", detail.getHospitalGuide().getHospitalName());
        assertEquals("병원 정문 도착 후 접수 창구로 이동 중입니다.", detail.getSession().getLocationSummary());
        assertEquals("접수표와 진료 대기 화면을 확인했습니다.", detail.getSession().getFieldPhotoNote());
        assertEquals("진료를 마치고 복약 안내를 받았습니다.", detail.getSessionReport().getSummary());
    }

    @Test
    public void getHospitalOptions_groupsDepartmentsByHospitalName() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockBookingRepository bookingRepository = new MockBookingRepository(repository);

        repository.saveHospitalGuide(
                "서울안심병원",
                "정형외과",
                Arrays.asList("접수: 본인 확인")
        );
        repository.saveHospitalGuide(
                "서울안심병원",
                "재활의학과",
                Arrays.asList("진료: 대기 순서 확인")
        );

        AtomicReference<List<BookingHospitalOption>> optionsRef = new AtomicReference<>();
        bookingRepository.getHospitalOptions(new RepositoryCallback<List<BookingHospitalOption>>() {
            @Override
            public void onSuccess(List<BookingHospitalOption> result) {
                optionsRef.set(result);
            }

            @Override
            public void onError(String message) {
            }
        });

        List<BookingHospitalOption> options = optionsRef.get();
        assertNotNull(options);
        assertTrue(options.size() >= 2);

        BookingHospitalOption option = null;
        for (BookingHospitalOption candidate : options) {
            if ("서울안심병원".equals(candidate.getHospitalName())) {
                option = candidate;
                break;
            }
        }

        assertNotNull(option);
        assertEquals(2, option.getDepartmentCount());
        assertEquals("재활의학과", option.getDepartmentNames().get(0));
        assertEquals("정형외과", option.getDepartmentNames().get(1));
    }

    @Test
    public void bookingRepository_followUpReviewPersistsForCompletedRequest() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockBookingRepository bookingRepository = new MockBookingRepository(repository);
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "follow-up-manager",
                "follow-up-manager@bodeul.app",
                "010-6666-5555",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest completedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "follow-up-hospital",
                "internal",
                "2026-04-30 10:20",
                "main-lobby",
                "follow-up-review-test"
        );
        assertNotNull(completedRequest);
        assertNotNull(repository.assignManagerToRequest(completedRequest.getId(), manager.getId()));
        repository.saveSessionReport(
                manager.getId(),
                "모든 진료와 수납이 정상적으로 마무리됐습니다.",
                "추가 처치는 없었습니다.",
                "기존 복약 일정을 유지합니다.",
                "2026-05-07 09:30"
        );
        assertEquals(AppointmentStatus.COMPLETED, completedRequest.getStatus());

        assertNotNull(completedRequest);

        AtomicReference<AppointmentFollowUpRecord> initialRecordRef = new AtomicReference<>();
        bookingRepository.getAppointmentFollowUp(
                patient,
                completedRequest.getId(),
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        initialRecordRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        assertNotNull(initialRecordRef.get());
        assertFalse(initialRecordRef.get().hasSavedReview());

        AtomicReference<AppointmentFollowUpRecord> savedRecordRef = new AtomicReference<>();
        bookingRepository.saveAppointmentFollowUpReview(
                patient,
                completedRequest.getId(),
                AppointmentFollowUpReviewRating.GOOD,
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        savedRecordRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        assertNotNull(savedRecordRef.get());
        assertTrue(savedRecordRef.get().hasSavedReview());
        assertEquals(AppointmentFollowUpReviewRating.GOOD, savedRecordRef.get().getReviewRating());
    }

    @Test
    public void bookingRepository_followUpSettlementPersistsForCompletedRequest() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockBookingRepository bookingRepository = new MockBookingRepository(repository);
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "follow-up-manager-2",
                "follow-up-manager-2@bodeul.app",
                "010-1234-9876",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest completedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "follow-up-hospital-2",
                "rehab",
                "2026-05-02 11:00",
                "south-gate",
                "follow-up-settlement-test"
        );
        assertNotNull(completedRequest);
        assertNotNull(repository.assignManagerToRequest(completedRequest.getId(), manager.getId()));
        repository.saveSessionReport(
                manager.getId(),
                "현장 동행과 복약 안내까지 모두 마무리했습니다.",
                "추가 처치는 없었습니다.",
                "식후 복용을 유지합니다.",
                "2026-05-09 10:00"
        );
        assertEquals(AppointmentStatus.COMPLETED, completedRequest.getStatus());

        AtomicReference<AppointmentFollowUpRecord> settlementRecordRef = new AtomicReference<>();
        bookingRepository.saveAppointmentFollowUpSettlement(
                patient,
                completedRequest.getId(),
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                "정산 내역과 결제 수단을 모두 확인했습니다.",
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        settlementRecordRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        assertNotNull(settlementRecordRef.get());
        assertTrue(settlementRecordRef.get().hasSavedSettlement());
        assertEquals(
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                settlementRecordRef.get().getSettlementStatus()
        );
        assertEquals(
                "정산 내역과 결제 수단을 모두 확인했습니다.",
                settlementRecordRef.get().getSettlementNote()
        );

        assertEquals(
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                settlementRecordRef.get().getSettlementStatus()
        );
    }

    @Test
    public void bookingRepository_followUpSettlementSupportsOvertimeReviewStatus() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockBookingRepository bookingRepository = new MockBookingRepository(repository);
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "follow-up-manager-4",
                "follow-up-manager-4@bodeul.app",
                "010-2222-9876",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest completedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "follow-up-hospital-4",
                "internal",
                "2026-05-05 09:40",
                "west-gate",
                "follow-up-overtime-test"
        );
        assertNotNull(completedRequest);
        assertNotNull(repository.assignManagerToRequest(completedRequest.getId(), manager.getId()));
        repository.saveSessionReport(
                manager.getId(),
                "진료와 수납까지 모두 마무리했습니다.",
                "추가 처치는 없었습니다.",
                "기존 복약 일정을 안내했습니다.",
                "2026-05-12 11:30"
        );
        assertEquals(AppointmentStatus.COMPLETED, completedRequest.getStatus());

        AtomicReference<AppointmentFollowUpRecord> settlementRecordRef = new AtomicReference<>();
        bookingRepository.saveAppointmentFollowUpSettlement(
                patient,
                completedRequest.getId(),
                AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW,
                "예상보다 대기 시간이 길어 초과 비용 확인이 필요합니다.",
                new RepositoryCallback<AppointmentFollowUpRecord>() {
                    @Override
                    public void onSuccess(AppointmentFollowUpRecord result) {
                        settlementRecordRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        assertNotNull(settlementRecordRef.get());
        assertTrue(settlementRecordRef.get().hasSavedSettlement());
        assertEquals(
                AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW,
                settlementRecordRef.get().getSettlementStatus()
        );
        assertTrue(settlementRecordRef.get().getSettlementStatus().requiresAdminFollowUp());
        assertEquals(
                "예상보다 대기 시간이 길어 초과 비용 확인이 필요합니다.",
                settlementRecordRef.get().getSettlementNote()
        );
    }

    @Test
    public void adminActionOverview_includesFollowUpRecordWithoutAdminActionWrite() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "follow-up-manager-3",
                "follow-up-manager-3@bodeul.app",
                "010-5555-4444",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest completedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "follow-up-hospital-3",
                "internal",
                "2026-05-03 14:10",
                "main-lobby",
                "follow-up-admin-overview-test"
        );
        assertNotNull(completedRequest);
        assertNotNull(repository.assignManagerToRequest(completedRequest.getId(), manager.getId()));
        repository.saveSessionReport(
                manager.getId(),
                "최종 리포트까지 모두 전송했습니다.",
                "특이사항은 없습니다.",
                "기존 복약을 유지합니다.",
                "2026-05-10 09:00"
        );
        assertEquals(AppointmentStatus.COMPLETED, completedRequest.getStatus());

        assertNotNull(repository.saveAppointmentFollowUpReview(
                completedRequest.getId(),
                AppointmentFollowUpReviewRating.EXCELLENT
        ));

        List<AdminRequestActionOverview> overviews = repository.getAdminRequestActionOverviews();

        assertEquals(1, overviews.size());
        assertEquals(completedRequest.getId(), overviews.get(0).getRequestId());
        assertNotNull(overviews.get(0).getFollowUpRecord());
        assertEquals(
                AppointmentFollowUpReviewRating.EXCELLENT,
                overviews.get(0).getFollowUpRecord().getReviewRating()
        );
    }

    @Test
    public void managerHistoryDetails_includeSavedFollowUpRecordForCompletedSession() {
        MockBodeulRepository repository = new MockBodeulRepository();
        MockManagerRepository managerRepository = new MockManagerRepository(repository);
        User patient = repository.findUserByEmail("patient@bodeul.app");
        User guardian = repository.findUserByEmail("guardian@bodeul.app");
        User manager = repository.registerUser(
                "history-follow-up-manager",
                "history-follow-up-manager@bodeul.app",
                "010-4321-6789",
                UserRole.MANAGER,
                "bodeul1234"
        );

        assertNotNull(patient);
        assertNotNull(guardian);
        assertNotNull(manager);

        AppointmentRequest completedRequest = repository.createLinkedAppointmentRequest(
                patient.getId(),
                guardian.getId(),
                "history-follow-up-hospital",
                "rehab",
                "2026-05-04 15:00",
                "east-gate",
                "manager-history-follow-up-test"
        );
        assertNotNull(completedRequest);
        assertNotNull(repository.assignManagerToRequest(completedRequest.getId(), manager.getId()));
        repository.saveSessionReport(
                manager.getId(),
                "All steps completed and report shared.",
                "No extra treatment notes.",
                "Medication guidance delivered.",
                "2026-05-11 13:00"
        );
        assertEquals(AppointmentStatus.COMPLETED, completedRequest.getStatus());
        assertNotNull(repository.saveAppointmentFollowUpReview(
                completedRequest.getId(),
                AppointmentFollowUpReviewRating.GOOD
        ));
        assertNotNull(repository.saveAppointmentFollowUpSettlement(
                completedRequest.getId(),
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                "Settlement verified."
        ));

        AtomicReference<List<AppointmentRequestDetail>> historyRef = new AtomicReference<>();
        managerRepository.getManagerHistoryDetails(
                manager.getId(),
                new RepositoryCallback<List<AppointmentRequestDetail>>() {
                    @Override
                    public void onSuccess(List<AppointmentRequestDetail> result) {
                        historyRef.set(result);
                    }

                    @Override
                    public void onError(String message) {
                    }
                }
        );

        assertNotNull(historyRef.get());

        AppointmentRequestDetail matchedDetail = null;
        for (AppointmentRequestDetail detail : historyRef.get()) {
            if (completedRequest.getId().equals(detail.getAppointmentRequest().getId())) {
                matchedDetail = detail;
                break;
            }
        }

        assertNotNull(matchedDetail);
        assertNotNull(matchedDetail.getFollowUpRecord());
        assertTrue(matchedDetail.getFollowUpRecord().hasSavedReview());
        assertTrue(matchedDetail.getFollowUpRecord().hasSavedSettlement());
        assertEquals(
                AppointmentFollowUpReviewRating.GOOD,
                matchedDetail.getFollowUpRecord().getReviewRating()
        );
        assertEquals(
                AppointmentFollowUpSettlementStatus.CONFIRMED,
                matchedDetail.getFollowUpRecord().getSettlementStatus()
        );
        assertEquals("Settlement verified.", matchedDetail.getFollowUpRecord().getSettlementNote());
    }

    @Test
    public void saveHospitalGuide_createsGuideFromStepLines() {
        MockBodeulRepository repository = new MockBodeulRepository();

        HospitalGuide savedGuide = repository.saveHospitalGuide(
                "safe-hospital",
                "orthopedics",
                Arrays.asList(
                        "check-id: verify patient and guardian ids",
                        "front-desk: confirm reservation info"
                )
        );

        assertNotNull(savedGuide);
        assertEquals(2, savedGuide.getSteps().size());
        assertEquals("check-id", savedGuide.getSteps().get(0).getTitle());
        assertEquals("orthopedics", repository.getHospitalGuide("safe-hospital", "orthopedics").getDepartmentName());
    }

    @Test
    public void deleteHospitalGuide_existingGuideRemovesItFromRepository() {
        MockBodeulRepository repository = new MockBodeulRepository();

        HospitalGuide savedGuide = repository.saveHospitalGuide(
                "delete-hospital",
                "rehab",
                Arrays.asList(
                        "reception: confirm reservation",
                        "treatment: share notes"
                )
        );

        assertNotNull(savedGuide);
        assertTrue(repository.deleteHospitalGuide(savedGuide.getId()));
        assertNull(repository.getHospitalGuide("delete-hospital", "rehab"));
    }

    @Test
    public void managerHomeProfile_saveQuickSummariesUpdatesStoredValues() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        ManagerHomeProfile initialProfile = repository.getManagerHomeProfile(manager.getId());
        assertNotNull(initialProfile);
        assertEquals(ManagerDocumentStatus.APPROVED, initialProfile.getDocumentStatus());
        assertNotNull(initialProfile.getDocumentFile(ManagerDocumentFileType.LICENSE));
        assertNull(initialProfile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE));

        ManagerHomeProfile updatedDocumentProfile = repository.saveManagerDocumentSummary(
                manager.getId(),
                "updated summary"
        );
        ManagerHomeProfile updatedAvailabilityProfile = repository.saveManagerAvailabilitySummary(
                manager.getId(),
                "weekday 10:00-17:00"
        );

        assertNotNull(updatedDocumentProfile);
        assertNotNull(updatedAvailabilityProfile);
        assertEquals(ManagerDocumentStatus.PENDING_REVIEW, updatedDocumentProfile.getDocumentStatus());
        assertEquals(
                "관리자 검토를 마쳤습니다. 이번 주 일정만 최신으로 유지해 주세요.",
                updatedDocumentProfile.getDocumentReviewNote()
        );
        assertTrue(updatedDocumentProfile.getDocumentUpdatedAtMillis() > 0L);
        assertTrue(updatedDocumentProfile.getDocumentReviewedAtMillis() > 0L);
        assertEquals("관리자", updatedDocumentProfile.getDocumentReviewedByName());
        assertEquals("updated summary", updatedDocumentProfile.getDocumentSummary());
        assertEquals("weekday 10:00-17:00", updatedAvailabilityProfile.getAvailabilitySummary());

        int submittedHistoryCount = repository.getManagerDocumentHistory(manager.getId()).size();
        long submittedAtMillis = updatedDocumentProfile.getDocumentUpdatedAtMillis();
        ManagerHomeProfile unchangedDocumentProfile = repository.saveManagerDocumentSummary(
                manager.getId(),
                "updated summary"
        );

        assertNotNull(unchangedDocumentProfile);
        assertEquals(submittedHistoryCount, repository.getManagerDocumentHistory(manager.getId()).size());
        assertEquals(submittedAtMillis, unchangedDocumentProfile.getDocumentUpdatedAtMillis());

        List<ManagerDocumentHistoryEntry> historyEntries = repository.getManagerDocumentHistory(manager.getId());
        assertEquals(3, historyEntries.size());
        assertEquals(ManagerDocumentHistoryEventType.SUBMITTED, historyEntries.get(0).getEventType());
        assertEquals(manager.getName(), historyEntries.get(0).getActorName());
    }

    @Test
    public void managerHomeProfile_saveDocumentFileMetadataStoresUploadedFileAndResetsReview() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        ManagerHomeProfile updatedProfile = repository.saveManagerDocumentFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.NURSING_LICENSE,
                        "manager-documents/" + manager.getId() + "/nursingLicense/1760500900000-nursing-license.jpg",
                        "nursing-license.jpg",
                        "image/jpeg",
                        1760500900000L,
                        "content://manager-documents/nursing-license"
                )
        );

        assertNotNull(updatedProfile);
        assertEquals(ManagerDocumentStatus.PENDING_REVIEW, updatedProfile.getDocumentStatus());
        assertEquals(
                "관리자 검토를 마쳤습니다. 이번 주 일정만 최신으로 유지해 주세요.",
                updatedProfile.getDocumentReviewNote()
        );
        assertTrue(updatedProfile.getDocumentReviewedAtMillis() > 0L);
        assertEquals("관리자", updatedProfile.getDocumentReviewedByName());
        assertNotNull(updatedProfile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE));
        assertEquals(
                "nursing-license.jpg",
                updatedProfile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE).getFileName()
        );
        assertEquals(
                "content://manager-documents/nursing-license",
                updatedProfile.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE).getPreviewUri()
        );

        List<ManagerDocumentHistoryEntry> historyEntries = repository.getManagerDocumentHistory(manager.getId());
        assertFalse(historyEntries.isEmpty());
        assertEquals(ManagerDocumentHistoryEventType.SUBMITTED, historyEntries.get(0).getEventType());
        assertEquals(manager.getName(), historyEntries.get(0).getActorName());
    }

    @Test
    public void managerDocumentDraft_matchesFirebaseSubmissionTransitions() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        int approvedHistoryCount = repository.getManagerDocumentHistory(manager.getId()).size();
        ManagerHomeProfile reviewedDraft = repository.saveManagerDocumentDraftFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.LICENSE,
                        "manager-documents/" + manager.getId() + "/license/reviewed-revision.jpg",
                        "reviewed-revision.jpg",
                        "image/jpeg",
                        1760500900100L,
                        "content://manager-documents/reviewed-revision"
                )
        );

        assertNotNull(reviewedDraft);
        assertEquals(ManagerDocumentStatus.PENDING_REVIEW, reviewedDraft.getDocumentStatus());
        assertEquals(approvedHistoryCount + 1, repository.getManagerDocumentHistory(manager.getId()).size());
        assertEquals(
                ManagerDocumentHistoryEventType.SUBMITTED,
                repository.getManagerDocumentHistory(manager.getId()).get(0).getEventType()
        );
        assertTrue(reviewedDraft.getDocumentReviewedAtMillis() > 0L);

        repository.saveManagerDocumentSummary(manager.getId(), "");
        int initialDraftHistoryCount = repository.getManagerDocumentHistory(manager.getId()).size();
        ManagerHomeProfile initialDraft = repository.saveManagerDocumentDraftFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.NURSING_LICENSE,
                        "manager-documents/" + manager.getId() + "/nursingLicense/initial-draft.png",
                        "initial-draft.png",
                        "image/png",
                        1760500900200L,
                        "content://manager-documents/initial-draft"
                )
        );

        assertNotNull(initialDraft);
        assertEquals(ManagerDocumentStatus.NOT_SUBMITTED, initialDraft.getDocumentStatus());
        assertEquals(initialDraftHistoryCount, repository.getManagerDocumentHistory(manager.getId()).size());
    }

    @Test
    public void managerDocumentDraft_replacesPreviousCanonicalQualificationAndRejectsLegacyWrite() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        ManagerHomeProfile jobQualificationDraft = repository.saveManagerDocumentDraftFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.LICENSE,
                        "manager-documents/" + manager.getId() + "/license/license.jpg",
                        "license.jpg",
                        "image/jpeg",
                        1760500900300L,
                        "content://manager-documents/license"
                )
        );
        assertNotNull(jobQualificationDraft);

        ManagerHomeProfile nursingDraft = repository.saveManagerDocumentDraftFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.NURSING_LICENSE,
                        "manager-documents/" + manager.getId() + "/nursingLicense/nursing.jpg",
                        "nursing.jpg",
                        "image/jpeg",
                        1760500900400L,
                        "content://manager-documents/nursing"
                )
        );

        assertNotNull(nursingDraft);
        assertNull(nursingDraft.getDocumentFile(ManagerDocumentFileType.LICENSE));
        assertNotNull(nursingDraft.getDocumentFile(ManagerDocumentFileType.NURSING_LICENSE));
        assertNull(repository.saveManagerDocumentDraftFileMetadata(
                manager.getId(),
                new ManagerDocumentFileMetadata(
                        ManagerDocumentFileType.HEALTH_CERTIFICATE,
                        "manager-documents/" + manager.getId() + "/healthCertificate/legacy.jpg",
                        "legacy.jpg",
                        "image/jpeg",
                        1760500900500L,
                        "content://manager-documents/legacy"
                )
        ));
    }

    @Test
    public void reviewManagerDocument_updatesStatusAndReviewNote() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        ManagerHomeProfile reviewedProfile = repository.reviewManagerDocument(
                manager.getId(),
                ManagerDocumentStatus.REJECTED,
                "please re-upload",
                "admin"
        );

        assertNotNull(reviewedProfile);
        assertEquals(ManagerDocumentStatus.REJECTED, reviewedProfile.getDocumentStatus());
        assertEquals("please re-upload", reviewedProfile.getDocumentReviewNote());
        assertEquals("admin", reviewedProfile.getDocumentReviewedByName());
        assertTrue(reviewedProfile.getDocumentReviewedAtMillis() > 0L);

        List<ManagerDocumentHistoryEntry> historyEntries = repository.getManagerDocumentHistory(manager.getId());
        assertEquals(3, historyEntries.size());
        assertEquals(ManagerDocumentHistoryEventType.REJECTED, historyEntries.get(0).getEventType());
        assertEquals(reviewedProfile.getDocumentReviewedByName(), historyEntries.get(0).getActorName());
    }

    @Test
    public void appendBookingCompanionChatMessage_addsGuardianMessageToSession() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User guardian = repository.findUserByEmail("guardian@bodeul.app");

        assertNotNull(guardian);

        String expectedMessage = "\uBCF4\uD638\uC790\uAC00 \uB3D9\uD589 \uC9C4\uD589 \uC0C1\uD669\uC744 \uD655\uC778\uD588\uC2B5\uB2C8\uB2E4.";
        AppointmentRequestDetail updatedDetail = repository.appendBookingCompanionChatMessage(
                guardian,
                "request-1",
                expectedMessage,
                null
        );

        assertNotNull(updatedDetail);
        assertNotNull(updatedDetail.getSession());
        assertFalse(updatedDetail.getSession().getChatMessages().isEmpty());
        assertEquals(
                expectedMessage,
                updatedDetail.getSession().getChatMessages().get(
                        updatedDetail.getSession().getChatMessages().size() - 1
                ).getBody()
        );
    }

    @Test
    public void appendManagerCompanionChatMessage_addsManagerMessageToDashboardSession() {
        MockBodeulRepository repository = new MockBodeulRepository();
        User manager = repository.findUserByEmail("manager@bodeul.app");

        assertNotNull(manager);

        String expectedMessage = "\uB9E4\uB2C8\uC800\uAC00 \uBCD1\uC6D0 \uB3C4\uCC29 \uC608\uC815 \uC2DC\uAC04\uC744 \uACF5\uC720\uD588\uC2B5\uB2C8\uB2E4.";
        ManagerDashboard updatedDashboard = repository.appendManagerCompanionChatMessage(
                manager.getId(),
                expectedMessage,
                null
        );

        assertNotNull(updatedDashboard);
        assertFalse(updatedDashboard.getSession().getChatMessages().isEmpty());
        assertEquals(
                expectedMessage,
                updatedDashboard.getSession().getChatMessages().get(
                        updatedDashboard.getSession().getChatMessages().size() - 1
                ).getBody()
        );
    }

    private BookingRequestDraft createDraft(
            String hospitalName,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String linkedName,
            String linkedPhone,
            String linkedEmail
    ) {
        return BookingRequestDraft.builder()
                .hospitalName(hospitalName)
                .departmentName(departmentName)
                .appointmentAt(appointmentAt)
                .meetingPlace(meetingPlace)
                .specialNotes("call before arrival")
                .patientConditionSummary("needs support on stairs")
                .medicationSummary("bring current prescription")
                .linkedParticipantName(linkedName)
                .linkedParticipantPhone(linkedPhone)
                .linkedParticipantEmail(linkedEmail)
                .mobilitySupport(BookingMobilitySupport.WHEELCHAIR)
                .tripType(BookingTripType.ROUND_TRIP)
                .managerGenderPreference(BookingManagerGenderPreference.FEMALE)
                .paymentMethod(BookingPaymentMethod.EASY_PAY)
                .couponType(BookingCouponType.FAMILY)
                .priceSummary(new BookingPriceSummary(69000, 23000, 10000, 82000))
                .build();
    }
}
