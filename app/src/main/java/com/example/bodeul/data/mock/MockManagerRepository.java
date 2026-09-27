package com.example.bodeul.data.mock;

import android.net.Uri;

import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.MockManagerStore;
import com.example.bodeul.data.MockSupportStore;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * 매니저 화면을 목업 데이터에 연결하는 저장소 구현이다.
 */
public class MockManagerRepository implements ManagerRepository {
    private final MockBodeulRepository repository;
    private final MockManagerStore managerStore;
    private final MockSupportStore supportStore;

    public MockManagerRepository(MockBodeulRepository repository) {
        this.repository = repository;
        this.managerStore = new MockManagerStore(repository);
        this.supportStore = new MockSupportStore(repository);
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = managerStore.getManagerDashboard(managerUserId);
        if (dashboard == null) {
            callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public synchronized void advanceCurrentStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard current = managerStore.getManagerDashboard(managerUserId);
        if (current == null) {
            callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        if (!ManagerRepository.matchesAdvanceExpectation(
                current.getSession(),
                expectedSessionId,
                expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.advanceManagerSession(managerUserId);
        if (dashboard == null) {
            callback.onError("다음 단계를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = managerStore.updateGuardianMessage(managerUserId, guardianUpdate);
        if (dashboard == null) {
            callback.onError("보호자 공유 메시지를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.appendManagerCompanionChatMessage(
                managerUserId,
                message,
                attachments
        );
        if (dashboard == null) {
            callback.onError("안심 채팅 메시지를 전송하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        managerStore.markManagerCompanionChatRead(managerUserId);
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        managerStore.saveCompanionLocationAlert(managerUserId, stage);
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.updateSharedLocation(
                managerUserId,
                latitude,
                longitude,
                locationSummary
        );
        if (dashboard == null) {
            callback.onError("실시간 위치를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.updateLiveLocationSharingState(managerUserId, active);
        if (dashboard == null) {
            callback.onError("실시간 위치 공유 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = managerStore.updateLocationSummary(managerUserId, locationSummary);
        if (dashboard == null) {
            callback.onError("위치 공유 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback) {
        ManagerDashboard dashboard = managerStore.updateFieldPhotoNote(managerUserId, fieldPhotoNote);
        if (dashboard == null) {
            callback.onError("현장 사진 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void saveVitalsNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String note,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard current = managerStore.getManagerDashboard(managerUserId);
        if (current == null || !ManagerRepository.matchesVitalsExpectation(
                current.getSession(), expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        saveFieldPhotoNote(managerUserId, note, callback);
    }

    @Override
    public synchronized void saveMedicationNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String medicationNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        if (!hasExpectedMedicationStep(managerUserId, expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.updateMedicationNote(managerUserId, medicationNote);
        if (dashboard == null) {
            callback.onError("복약 메모를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public synchronized void savePharmacySummary(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String pharmacySummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        if (!hasExpectedMedicationStep(managerUserId, expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.updatePharmacySummary(managerUserId, pharmacySummary);
        if (dashboard == null) {
            callback.onError("약국 진행 요약을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public void updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.updatePreConsultationConfirmed(
                managerUserId,
                preConsultationConfirmed);
        if (dashboard == null) {
            callback.onError("진료 전 확인 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public synchronized void updatePharmacyCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean pharmacyCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        if (!hasExpectedMedicationStep(managerUserId, expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.updatePharmacyCompleted(managerUserId, pharmacyCompleted);
        if (dashboard == null) {
            callback.onError("약국 단계 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public synchronized void updatePrescriptionCollected(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        if (!hasExpectedMedicationStep(managerUserId, expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.updatePrescriptionCollected(
                managerUserId,
                prescriptionCollected
        );
        if (dashboard == null) {
            callback.onError("처방전 수령 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    @Override
    public synchronized void updateMedicationGuidanceCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        if (!hasExpectedMedicationStep(managerUserId, expectedSessionId, expectedStepCode)) {
            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        ManagerDashboard dashboard = managerStore.updateMedicationGuidanceCompleted(
                managerUserId,
                medicationGuidanceCompleted
        );
        if (dashboard == null) {
            callback.onError("복약 안내 상태를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(dashboard);
    }

    private boolean hasExpectedMedicationStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode
    ) {
        ManagerDashboard current = managerStore.getManagerDashboard(managerUserId);
        return current != null && ManagerRepository.matchesMedicationExpectation(
                current.getSession(), expectedSessionId, expectedStepCode);
    }

    @Override
    public void replaceSessionArtifacts(
            String managerUserId,
            String purpose,
            String clientRequestId,
            List<Uri> fileUris,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.getManagerDashboard(managerUserId);
        if (dashboard == null || dashboard.getSession() == null) {
            callback.onError("동행 첨부를 저장할 세션이 없습니다.");
            return;
        }
        CompanionSession session = dashboard.getSession();
        List<CompanionSessionArtifact> artifacts = new ArrayList<>();
        for (CompanionSessionArtifact artifact : session.getArtifacts()) {
            if (!purpose.equals(artifact.getPurpose())) {
                artifacts.add(artifact);
            }
        }
        long createdAt = System.currentTimeMillis();
        for (int index = 0; index < fileUris.size(); index++) {
            Uri uri = fileUris.get(index);
            String fileName = uri == null || uri.getLastPathSegment() == null
                    ? "동행 첨부 " + (index + 1)
                    : uri.getLastPathSegment();
            artifacts.add(new CompanionSessionArtifact(
                    "mock-artifact-" + createdAt + "-" + index,
                    purpose,
                    fileName,
                    "image/jpeg",
                    0L,
                    createdAt));
        }
        replaceMockArtifacts(session, artifacts);
        callback.onSuccess(dashboard);
    }

    @Override
    public void clearSessionArtifacts(
            String managerUserId,
            String purpose,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        ManagerDashboard dashboard = managerStore.getManagerDashboard(managerUserId);
        if (dashboard == null || dashboard.getSession() == null) {
            callback.onError("동행 첨부를 삭제할 세션이 없습니다.");
            return;
        }
        CompanionSession session = dashboard.getSession();
        List<CompanionSessionArtifact> artifacts = new ArrayList<>();
        for (CompanionSessionArtifact artifact : session.getArtifacts()) {
            if (!purpose.equals(artifact.getPurpose())) {
                artifacts.add(artifact);
            }
        }
        replaceMockArtifacts(session, artifacts);
        callback.onSuccess(dashboard);
    }

    private void replaceMockArtifacts(
            CompanionSession session,
            List<CompanionSessionArtifact> artifacts
    ) {
        session.applyCompletionState(
                session.getCareEndedAtMillis(),
                session.getManagerJournal(),
                session.getReportGenerationStatus(),
                session.getReportGenerationAttempts(),
                session.getReportGenerationLastError(),
                session.getReportGenerationUpdatedAtMillis(),
                artifacts);
    }

    @Override
    public void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback) {
        ManagerHomeProfile profile = repository.getManagerHomeProfile(managerUserId);
        if (profile == null) {
            callback.onError("매니저 홈 요약 정보를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        User manager = repository.findUserById(managerUserId);
        ManagerHomeProfile profile = repository.getManagerHomeProfile(managerUserId);
        if (manager == null || profile == null) {
            callback.onError("매니저 정보를 불러오지 못했습니다.");
            return;
        }

        List<ManagerDocumentHistoryEntry> historyEntries = repository.getManagerDocumentHistory(managerUserId);
        callback.onSuccess(new ManagerDocumentOverview(manager, profile, historyEntries));
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        User manager = repository.findUserById(managerUserId);
        if (manager == null) {
            callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다.");
            return;
        }

        List<AppointmentRequestDetail> historyDetails = new ArrayList<>();
        for (CompanionSession session : repository.getManagerSessions(managerUserId)) {
            if (session.getStatus() != SessionStatus.COMPLETED) {
                continue;
            }

            AppointmentRequest request = findRequest(session.getAppointmentRequestId());
            if (request == null) {
                continue;
            }

            SessionReport report = repository.getSessionReport(session.getId());
            historyDetails.add(new AppointmentRequestDetail(
                    request,
                    repository.findUserById(request.getPatientUserId()),
                    repository.findUserById(request.getGuardianUserId()),
                    manager,
                    session,
                    report,
                    HospitalGuideFallbackFactory.fallbackIfMissing(
                            repository.getHospitalGuide(request.getHospitalName(), request.getDepartmentName()),
                            request.getHospitalName(),
                            request.getDepartmentName()
                    ),
                    repository.getAppointmentFollowUpRecord(request.getId())
            ));
        }
        callback.onSuccess(historyDetails);
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentSummary(managerUserId, documentSummary);
        if (profile == null) {
            callback.onError("서류 등록 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentFileMetadata(
                managerUserId,
                documentFileMetadata
        );
        if (profile == null) {
            callback.onError("원본 서류 파일 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerDocumentDraftFileMetadata(
                managerUserId,
                documentFileMetadata
        );
        if (profile == null) {
            callback.onError("원본 서류 파일 초안 정보를 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        ManagerHomeProfile profile = repository.saveManagerAvailabilitySummary(managerUserId, availabilitySummary);
        if (profile == null) {
            callback.onError("선호 가능 일정을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(profile);
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
        ManagerDashboard dashboard = managerStore.saveSessionReport(
                managerUserId,
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
        if (dashboard == null) {
            callback.onError("리포트를 저장하지 못했습니다.");
            return;
        }
        SessionReport report = dashboard.getSessionReport();
        if (report == null) {
            callback.onError("저장된 리포트를 불러오지 못했습니다.");
            return;
        }
        callback.onSuccess(report);
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        callback.onSuccess(supportStore.getSupportInquiries(managerUserId));
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        SupportInquiry inquiry = supportStore.saveSupportInquiry(
                managerUserId,
                category,
                title,
                body
        );
        if (inquiry == null) {
            callback.onError("문의 내용을 저장하지 못했습니다.");
            return;
        }
        callback.onSuccess(supportStore.getSupportInquiries(managerUserId));
    }

    @Override
    public boolean isFirebaseBacked() {
        return false;
    }

    private AppointmentRequest findRequest(String requestId) {
        for (AppointmentRequest request : repository.getAppointmentRequests()) {
            if (request.getId().equals(requestId)) {
                return request;
            }
        }
        return null;
    }
}
