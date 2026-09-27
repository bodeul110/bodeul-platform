package com.example.bodeul.data.coreapi;

import android.content.Context;
import android.net.Uri;

import com.example.bodeul.R;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.firebase.FirebaseManagerRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약과 동행 운영 데이터는 Core API를 사용하고, 매니저 서류 등 아직 이전하지 않은 기능만 Firebase에 유지한다.
 */
public final class CoreApiManagerRepository implements ManagerRepository {
    private final Context context;
    private final FirebaseManagerRepository firebaseRepository;
    private final CoreApiAppointmentClient appointmentClient;
    private final CoreApiCompanionSessionClient sessionClient;

    public CoreApiManagerRepository(
            Context context,
            FirebaseManagerRepository firebaseRepository
    ) {
        this.context = context.getApplicationContext();
        this.firebaseRepository = firebaseRepository;
        this.appointmentClient = new CoreApiAppointmentClient(this.context);
        this.sessionClient = new CoreApiCompanionSessionClient(this.context);
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        sessionClient.getSessions(new RepositoryCallback<List<CoreApiCompanionSessionClient.SessionSnapshot>>() {
            @Override
            public void onSuccess(List<CoreApiCompanionSessionClient.SessionSnapshot> sessions) {
                CoreApiCompanionSessionClient.SessionSnapshot activeSession = findActiveSession(sessions);
                if (activeSession == null) {
                    callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
                    return;
                }
                loadDashboard(activeSession, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void advanceCurrentStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> {
            CompanionSession session = dashboard.getSession();
            if (!ManagerRepository.matchesAdvanceExpectation(
                    session,
                    expectedSessionId,
                    expectedStepCode)) {
                callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                return;
            }
            if (session.hasServerAdvanceDecision() && !session.isServerAdvanceAllowed()) {
                callback.onError(toAdvanceBlockedMessage(session.getAdvanceBlockedReason()));
                return;
            }
            if ("CARE_COMPLETION".equals(session.getCurrentStepCode())) {
                sessionClient.endCare(
                        session.getId(),
                        expectedStepCode,
                        refreshCallback(managerUserId, callback));
            } else {
                sessionClient.advance(
                        session.getId(),
                        expectedStepCode,
                        refreshCallback(managerUserId, callback));
            }
        });
    }

    @Override
    public void saveGuardianUpdate(
            String managerUserId,
            String guardianUpdate,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "guardianUpdate", guardianUpdate, callback);
    }

    @Override
    public void saveConsultationGuardianUpdate(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String guardianUpdate,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateConsultationText(
                managerUserId,
                expectedSessionId,
                expectedStepCode,
                "guardianUpdate",
                guardianUpdate,
                callback);
    }

    @Override
    public void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard ->
                sessionClient.sendRealtimeMessage(
                        dashboard.getSession().getId(),
                        message,
                        attachments,
                        new RepositoryCallback<CoreApiCompanionSessionClient.RealtimeSnapshot>() {
                            @Override
                            public void onSuccess(
                                    CoreApiCompanionSessionClient.RealtimeSnapshot result
                            ) {
                                getManagerDashboard(managerUserId, callback);
                            }

                            @Override
                            public void onError(String errorMessage) {
                                callback.onError(errorMessage);
                            }
                        }));
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                sessionClient.markRealtimeRead(result.getSession().getId());
            }

            @Override
            public void onError(String message) {
                // 읽음 표시는 화면 오류로 확장하지 않는다.
            }
        });
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                sessionClient.updateText(
                        result.getSession().getId(),
                        "locationAlertStage",
                        stage == null ? CompanionLocationAlertStage.NONE.getValue() : stage.getValue(),
                        new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                            @Override
                            public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot ignored) {
                                // 자동 알림 발송 상태는 다음 대시보드 조회에서 동기화한다.
                            }

                            @Override
                            public void onError(String message) {
                                // 알림 자체의 성공 흐름을 저장 실패로 되돌리지는 않는다.
                            }
                        });
            }

            @Override
            public void onError(String message) {
                // 활성 세션이 없으면 저장할 자동 알림 상태도 없다.
            }
        });
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateText(
                dashboard.getSession().getId(),
                "locationSummary",
                locationSummary,
                new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
                    @Override
                    public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot result) {
                        sessionClient.shareRealtimeLocation(
                                result.getCoreId(),
                                latitude,
                                longitude,
                                new RepositoryCallback<CoreApiCompanionSessionClient.RealtimeSnapshot>() {
                                    @Override
                                    public void onSuccess(
                                            CoreApiCompanionSessionClient.RealtimeSnapshot ignored
                                    ) {
                                        getManagerDashboard(managerUserId, callback);
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }));
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "liveLocationSharingActive",
                active,
                callback);
    }

    @Override
    public void saveLocationSummary(
            String managerUserId,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "locationSummary", locationSummary, callback);
    }

    @Override
    public void saveFieldPhotoNote(
            String managerUserId,
            String fieldPhotoNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "fieldPhotoNote", fieldPhotoNote, callback);
    }

    @Override
    public void saveConsultationFieldNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String fieldPhotoNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateConsultationText(
                managerUserId,
                expectedSessionId,
                expectedStepCode,
                "fieldPhotoNote",
                fieldPhotoNote,
                callback);
    }

    @Override
    public void saveVitalsNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String note,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> {
            CompanionSession session = dashboard.getSession();
            if (!ManagerRepository.matchesVitalsExpectation(
                    session, expectedSessionId, expectedStepCode)) {
                callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                return;
            }
            sessionClient.updateText(
                    session.getId(),
                    "fieldPhotoNote",
                    note,
                    expectedStepCode,
                    refreshCallback(managerUserId, callback));
        });
    }

    @Override
    public void saveMedicationNote(
            String managerUserId,
            String medicationNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "medicationNote", medicationNote, callback);
    }

    @Override
    public void savePharmacySummary(
            String managerUserId,
            String pharmacySummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionText(managerUserId, "pharmacySummary", pharmacySummary, callback);
    }

    @Override
    public void updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "preConsultationConfirmed",
                preConsultationConfirmed,
                callback);
    }

    @Override
    public void updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "prescriptionCollected",
                prescriptionCollected,
                callback);
    }

    @Override
    public void updatePharmacyCompleted(
            String managerUserId,
            boolean pharmacyCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(managerUserId, "pharmacyCompleted", pharmacyCompleted, callback);
    }

    @Override
    public void updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        updateSessionBoolean(
                managerUserId,
                "medicationGuidanceCompleted",
                medicationGuidanceCompleted,
                callback);
    }

    @Override
    public void replaceSessionArtifacts(
            String managerUserId,
            String purpose,
            String clientRequestId,
            List<Uri> fileUris,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.replaceArtifacts(
                dashboard.getSession().getId(),
                purpose,
                clientRequestId,
                fileUris,
                new RepositoryCallback<org.json.JSONObject>() {
                    @Override
                    public void onSuccess(org.json.JSONObject result) {
                        getManagerDashboard(managerUserId, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }));
    }

    @Override
    public void clearSessionArtifacts(
            String managerUserId,
            String purpose,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.clearArtifacts(
                dashboard.getSession().getId(),
                purpose,
                new RepositoryCallback<org.json.JSONObject>() {
                    @Override
                    public void onSuccess(org.json.JSONObject result) {
                        getManagerDashboard(managerUserId, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }));
    }

    @Override
    public void getManagerHomeProfile(
            String managerUserId,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.getManagerHomeProfile(managerUserId, callback);
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        firebaseRepository.getManagerDocumentOverview(managerUserId, callback);
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        sessionClient.getSessions(new RepositoryCallback<List<CoreApiCompanionSessionClient.SessionSnapshot>>() {
                    @Override
                    public void onSuccess(
                            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions
                    ) {
                        List<CoreApiCompanionSessionClient.SessionSnapshot> completed = new ArrayList<>();
                        for (CoreApiCompanionSessionClient.SessionSnapshot session : sessions) {
                            if (session.getStatus() == SessionStatus.COMPLETED) {
                                completed.add(session);
                            }
                        }
                        loadHistory(completed, 0, new ArrayList<>(), callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentSummary(managerUserId, documentSummary, callback);
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentFileMetadata(
                managerUserId,
                documentFileMetadata,
                callback);
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerDocumentDraftFileMetadata(
                managerUserId,
                documentFileMetadata,
                callback);
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        firebaseRepository.saveManagerAvailabilitySummary(
                managerUserId,
                availabilitySummary,
                callback);
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<SessionReport> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.submitReport(
                dashboard.getSession().getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisitAt,
                new CoreApiManagerReportCompletionCallback(
                        dashboard.getSession().getId(),
                        callback)));
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firebaseRepository.getSupportInquiries(managerUserId, callback);
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firebaseRepository.submitSupportInquiry(managerUserId, category, title, body, callback);
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void updateSessionText(
            String managerUserId,
            String field,
            String value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateText(
                dashboard.getSession().getId(),
                field,
                value,
                refreshCallback(managerUserId, callback)));
    }

    private void updateConsultationText(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String field,
            String value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> {
            CompanionSession session = dashboard.getSession();
            if (!ManagerRepository.matchesConsultationExpectation(
                    session, expectedSessionId, expectedStepCode)) {
                callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                return;
            }
            sessionClient.updateText(
                    session.getId(),
                    field,
                    value,
                    expectedStepCode,
                    refreshCallback(managerUserId, callback));
        });
    }

    private void updateSessionBoolean(
            String managerUserId,
            String field,
            boolean value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        withDashboard(managerUserId, callback, dashboard -> sessionClient.updateBoolean(
                dashboard.getSession().getId(),
                field,
                value,
                refreshCallback(managerUserId, callback)));
    }

    private <T> void withDashboard(
            String managerUserId,
            RepositoryCallback<T> callback,
            DashboardOperation operation
    ) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                operation.run(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot> refreshCallback(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<CoreApiCompanionSessionClient.SessionSnapshot>() {
            @Override
            public void onSuccess(CoreApiCompanionSessionClient.SessionSnapshot result) {
                getManagerDashboard(managerUserId, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private RepositoryCallback<ManagerDashboard> overlayCallback(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                getManagerDashboard(managerUserId, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private CoreApiCompanionSessionClient.SessionSnapshot findActiveSession(
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions
    ) {
        for (CoreApiCompanionSessionClient.SessionSnapshot session : sessions) {
            if (session.getStatus() != SessionStatus.COMPLETED
                    && session.getStatus() != SessionStatus.CANCELED) {
                return session;
            }
            if (session.getStatus() == SessionStatus.COMPLETED
                    && session.requiresReportRetry()) {
                return session;
            }
        }
        return null;
    }

    private void loadDashboard(
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        appointmentClient.getAppointment(
                sessionSnapshot.getCoreAppointmentId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        CompanionSession session = sessionSnapshot.merge(null, appointment.getId());
                        if (!shouldEnrichDashboardWithRealtime(sessionSnapshot)) {
                            callback.onSuccess(toDashboard(
                                    appointment,
                                    session,
                                    sessionSnapshot));
                            return;
                        }
                        sessionClient.enrichWithRealtime(
                                sessionSnapshot,
                                session,
                                new RepositoryCallback<CompanionSession>() {
                                    @Override
                                    public void onSuccess(CompanionSession realtimeSession) {
                                        callback.onSuccess(toDashboard(
                                                appointment,
                                                realtimeSession,
                                                sessionSnapshot));
                                    }

                                    @Override
                                    public void onError(String message) {
                                        callback.onError(message);
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    private void loadHistory(
            List<CoreApiCompanionSessionClient.SessionSnapshot> sessions,
            int index,
            List<AppointmentRequestDetail> output,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        if (index >= sessions.size()) {
            output.sort((left, right) -> right.getAppointmentRequest()
                    .getAppointmentAt()
                    .compareTo(left.getAppointmentRequest().getAppointmentAt()));
            callback.onSuccess(output);
            return;
        }
        CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot = sessions.get(index);
        appointmentClient.getAppointment(
                sessionSnapshot.getCoreAppointmentId(),
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest appointment) {
                        CompanionSession session = sessionSnapshot.merge(null, appointment.getId());
                        output.add(toHistoryDetail(
                                appointment,
                                session,
                                null,
                                sessionSnapshot));
                        loadHistory(sessions, index + 1, output, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
    }

    static boolean shouldEnrichDashboardWithRealtime(
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
    ) {
        return sessionSnapshot != null && !sessionSnapshot.hasCareEnded();
    }

    private ManagerDashboard toDashboard(
            AppointmentRequest appointment,
            CompanionSession session,
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
    ) {
        return new ManagerDashboard(
                toManager(appointment),
                toParticipant(
                        appointment.getPatientUserId(),
                        UserRole.PATIENT,
                        appointment.getPatientName(),
                        appointment.getPatientEmail(),
                        appointment.getPatientPhone()),
                toParticipant(
                        appointment.getGuardianUserId(),
                        UserRole.GUARDIAN,
                        appointment.getGuardianName(),
                        appointment.getGuardianEmail(),
                        appointment.getGuardianPhone()),
                appointment,
                session,
                resolveHospitalGuide(sessionSnapshot, appointment),
                null);
    }

    private AppointmentRequestDetail toHistoryDetail(
            AppointmentRequest appointment,
            CompanionSession session,
            SessionReport report,
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot
    ) {
        return new AppointmentRequestDetail(
                appointment,
                toParticipant(
                        appointment.getPatientUserId(),
                        UserRole.PATIENT,
                        appointment.getPatientName(),
                        appointment.getPatientEmail(),
                        appointment.getPatientPhone()),
                toParticipant(
                        appointment.getGuardianUserId(),
                        UserRole.GUARDIAN,
                        appointment.getGuardianName(),
                        appointment.getGuardianEmail(),
                        appointment.getGuardianPhone()),
                toManager(appointment),
                session,
                report,
                resolveHospitalGuide(sessionSnapshot, appointment),
                null);
    }

    private HospitalGuide resolveHospitalGuide(
            CoreApiCompanionSessionClient.SessionSnapshot sessionSnapshot,
            AppointmentRequest appointment
    ) {
        HospitalGuide guide = sessionSnapshot.toHospitalGuide(
                appointment.getHospitalName(),
                appointment.getDepartmentName());
        return guide == null
                ? HospitalGuideFallbackFactory.create(
                        appointment.getHospitalName(),
                        appointment.getDepartmentName())
                : guide;
    }

    private String toAdvanceBlockedMessage(String blockedReason) {
        switch (blockedReason) {
            case "GUIDE_NOT_READY":
                return context.getString(R.string.guide_empty_steps_server);
            case "STEP_CONTRACT_MISMATCH":
                return context.getString(R.string.guide_blocked_contract_mismatch);
            case "STEP_INPUT_REQUIRED":
                return context.getString(R.string.guide_blocked_input_required);
            case "LAST_STEP_REACHED":
                return context.getString(R.string.guide_blocked_last_step);
            case "SESSION_TERMINAL":
                return context.getString(R.string.guide_blocked_terminal);
            default:
                return context.getString(R.string.guide_blocked_unknown);
        }
    }

    private User toManager(AppointmentRequest appointment) {
        String name = appointment.getManagerName().isEmpty()
                ? "배정 매니저"
                : appointment.getManagerName();
        return toParticipant(
                appointment.getManagerUserId(),
                UserRole.MANAGER,
                name,
                appointment.getManagerEmail(),
                appointment.getManagerPhone());
    }

    private User toParticipant(
            String userId,
            UserRole role,
            String name,
            String email,
            String phone
    ) {
        return new User(userId, role, name, email, phone);
    }

    private interface DashboardOperation {
        void run(ManagerDashboard dashboard);
    }
}
