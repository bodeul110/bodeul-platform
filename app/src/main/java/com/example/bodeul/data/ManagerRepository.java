package com.example.bodeul.data;

import android.net.Uri;

import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;

import java.util.List;

/**
 * 매니저 홈, 가이드, 과거 이력 화면에서 사용하는 기능 전용 저장소 계약이다.
 */
public interface ManagerRepository {
    String MESSAGE_NO_ACTIVE_SESSION = "현재 배정된 동행 일정이 없습니다.";
    String MESSAGE_STALE_GUIDE_STEP = "진행 단계가 변경되어 다시 확인해야 합니다.";

    void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback);

    void advanceCurrentStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            RepositoryCallback<ManagerDashboard> callback);

    static boolean matchesAdvanceExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode
    ) {
        if (session == null) {
            return false;
        }
        return normalize(expectedSessionId).equals(normalize(session.getId()))
                && normalize(expectedStepCode).equals(normalize(session.getCurrentStepCode()));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback);

    /** 진료 보조 화면에서 확인한 세션과 단계가 그대로일 때만 보호자 공유 내용을 저장한다. */
    default void saveConsultationGuardianUpdate(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String guardianUpdate,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard dashboard) {
                if (dashboard == null || !matchesConsultationExpectation(
                        dashboard.getSession(), expectedSessionId, expectedStepCode)) {
                    callback.onError(MESSAGE_STALE_GUIDE_STEP);
                    return;
                }
                saveGuardianUpdate(managerUserId, guardianUpdate, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    );

    void markCompanionChatRead(String managerUserId);

    void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage);

    void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    );

    void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    );

    void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback);

    void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback);

    /** 진료 보조 화면에서 확인한 세션과 단계가 그대로일 때만 현장 메모를 저장한다. */
    default void saveConsultationFieldNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String fieldPhotoNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard dashboard) {
                if (dashboard == null || !matchesConsultationExpectation(
                        dashboard.getSession(), expectedSessionId, expectedStepCode)) {
                    callback.onError(MESSAGE_STALE_GUIDE_STEP);
                    return;
                }
                saveFieldPhotoNote(managerUserId, fieldPhotoNote, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    static boolean matchesConsultationExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode
    ) {
        return "CONSULTATION_SUPPORT".equals(normalize(expectedStepCode))
                && matchesAdvanceExpectation(session, expectedSessionId, expectedStepCode);
    }

    /** 기초 측정값은 화면에서 확인한 세션과 단계가 그대로일 때만 저장한다. */
    default void saveVitalsNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String note,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        callback.onError("기초 측정 저장에는 Core API 연결이 필요합니다.");
    }

    static boolean matchesVitalsExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode
    ) {
        return "VITALS_CHECK".equals(normalize(expectedStepCode))
                && matchesAdvanceExpectation(session, expectedSessionId, expectedStepCode);
    }

    /** 수납 증빙 화면의 공유 메모는 확인한 세션과 8단계가 그대로일 때만 저장한다. */
    default void savePaymentEvidenceNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String note,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        callback.onError("수납 메모 저장에는 Core API 연결이 필요합니다.");
    }

    static boolean matchesPaymentExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode
    ) {
        return "PAYMENT_EVIDENCE".equals(normalize(expectedStepCode))
                && matchesAdvanceExpectation(session, expectedSessionId, expectedStepCode);
    }

    /** 첨부 용도와 현재 단계가 일치해야 선택 창을 연 시점의 세션에만 파일을 반영한다. */
    static boolean matchesArtifactExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode,
            String purpose
    ) {
        String normalizedPurpose = normalize(purpose);
        String requiredStepCode;
        if (CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE.equals(normalizedPurpose)) {
            requiredStepCode = "PAYMENT_EVIDENCE";
        } else if (CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE.equals(
                normalizedPurpose)) {
            requiredStepCode = "PRESCRIPTION_DOCUMENTS";
        } else {
            return false;
        }
        return requiredStepCode.equals(normalize(expectedStepCode))
                && matchesAdvanceExpectation(session, expectedSessionId, expectedStepCode);
    }

    /** 복약 확인 입력은 화면에서 확인한 세션과 11단계가 그대로일 때만 적용한다. */
    static boolean matchesMedicationExpectation(
            CompanionSession session,
            String expectedSessionId,
            String expectedStepCode
    ) {
        return "MEDICATION_CONFIRMATION".equals(normalize(expectedStepCode))
                && matchesAdvanceExpectation(session, expectedSessionId, expectedStepCode);
    }

    void saveMedicationNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String medicationNote,
            RepositoryCallback<ManagerDashboard> callback);

    void savePharmacySummary(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String pharmacySummary,
            RepositoryCallback<ManagerDashboard> callback);

    void updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed,
            RepositoryCallback<ManagerDashboard> callback
    );

    void updatePrescriptionCollected(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    );

    void updatePharmacyCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean pharmacyCompleted,
            RepositoryCallback<ManagerDashboard> callback);

    void updateMedicationGuidanceCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    );

    default void replaceSessionArtifacts(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String purpose,
            String clientRequestId,
            List<Uri> fileUris,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        callback.onError("현재 실행 모드에서는 동행 첨부를 저장할 수 없습니다.");
    }

    default void clearSessionArtifacts(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String purpose,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        callback.onError("현재 실행 모드에서는 동행 첨부를 삭제할 수 없습니다.");
    }

    void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback);

    void getManagerDocumentOverview(String managerUserId, RepositoryCallback<ManagerDocumentOverview> callback);

    void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    );

    void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    );

    void submitSessionReport(
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
    );

    void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    );

    boolean isFirebaseBacked();
}
