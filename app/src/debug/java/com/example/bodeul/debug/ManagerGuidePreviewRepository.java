package com.example.bodeul.debug;

import androidx.annotation.Nullable;

import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import java.util.Collections;
import java.util.List;

/** 서버 쓰기 없이 최신 13단계와 운영 7단계 화면의 로컬 상태 변경을 제공한다. */
final class ManagerGuidePreviewRepository extends MockManagerRepository {
    static final String MANAGER_ID = "manager-1";

    private final PreviewDataRepository dataRepository;
    private final List<GuideStep> previewSteps;

    ManagerGuidePreviewRepository(String initialStepCode) {
        this(initialStepCode, false);
    }

    ManagerGuidePreviewRepository(String initialStepCode, boolean seedPaymentEvidence) {
        this(initialStepCode, seedPaymentEvidence, false);
    }

    ManagerGuidePreviewRepository(
            String initialStepCode,
            boolean seedPaymentEvidence,
            boolean legacyMode
    ) {
        this(
                new PreviewDataRepository(stepsFor(legacyMode)),
                initialStepCode,
                seedPaymentEvidence,
                stepsFor(legacyMode));
    }

    private ManagerGuidePreviewRepository(
            PreviewDataRepository dataRepository,
            String initialStepCode,
            boolean seedPaymentEvidence,
            List<GuideStep> previewSteps
    ) {
        super(dataRepository);
        this.dataRepository = dataRepository;
        this.previewSteps = previewSteps;
        GuideStep initialStep = resolveStep(previewSteps, initialStepCode);
        CompanionSession session = requirePreviewSession();
        session.setCurrentStepOrder(initialStep.getOrder());
        applyPreviewProgress(session);
        if (seedPaymentEvidence) {
            seedPaymentEvidence(session);
        }
    }

    MockBodeulRepository dataRepository() {
        return dataRepository;
    }

    @Override
    public void getManagerDashboard(
            String managerUserId,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        super.getManagerDashboard(managerUserId, withPreviewProgress(callback));
    }

    @Override
    public void advanceCurrentStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        super.advanceCurrentStep(
                managerUserId,
                expectedSessionId,
                expectedStepCode,
                withPreviewProgress(callback));
    }

    @Override
    public void updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        super.updatePreConsultationConfirmed(
                managerUserId,
                preConsultationConfirmed,
                withPreviewProgress(callback));
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
        if (!MANAGER_ID.equals(managerUserId)) {
            callback.onError("미리보기 매니저 세션을 찾지 못했습니다.");
            return;
        }
        CompanionSession session = requirePreviewSession();
        SessionReport report = new SessionReport(
                "debug-preview-report",
                session.getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisitAt);
        session.setStatus(SessionStatus.COMPLETED);
        session.applyServerGuideProgress(
                session.getCurrentStepCode(),
                true,
                false,
                "SESSION_TERMINAL");
        callback.onSuccess(report);
    }

    private RepositoryCallback<ManagerDashboard> withPreviewProgress(
            RepositoryCallback<ManagerDashboard> callback
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                applyPreviewProgress(result.getSession());
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private CompanionSession requirePreviewSession() {
        List<CompanionSession> sessions = dataRepository.getManagerSessions(MANAGER_ID);
        if (sessions.isEmpty()) {
            throw new IllegalStateException("debug 가이드 미리보기 세션을 찾지 못했습니다.");
        }
        return sessions.get(0);
    }

    private void seedPaymentEvidence(CompanionSession session) {
        session.applyCompletionState(
                session.getCareEndedAtMillis(),
                session.getManagerJournal(),
                session.getReportGenerationStatus(),
                session.getReportGenerationAttempts(),
                session.getReportGenerationLastError(),
                session.getReportGenerationUpdatedAtMillis(),
                Collections.singletonList(new CompanionSessionArtifact(
                        "debug-payment-artifact",
                        CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                        "receipt.pdf",
                        "application/pdf",
                        2L * 1024L * 1024L,
                        1L)));
    }

    private void applyPreviewProgress(CompanionSession session) {
        GuideStep currentStep = findByOrder(previewSteps, session.getCurrentStepOrder());
        if (currentStep == null) {
            currentStep = previewSteps.get(0);
            session.setCurrentStepOrder(currentStep.getOrder());
        }
        session.setCurrentStepCode(currentStep.getCode());
        session.setStatus(statusFor(currentStep.getOrder()));
        if ("PRE_CONSULTATION".equals(currentStep.getCode())
                && !session.isPreConsultationConfirmed()) {
            session.applyServerGuideProgress(
                    currentStep.getCode(),
                    true,
                    false,
                    "STEP_INPUT_REQUIRED");
        } else if (currentStep.getOrder() == previewSteps.size()) {
            session.applyServerGuideProgress(
                    currentStep.getCode(),
                    true,
                    false,
                    previewSteps.size() == 7
                            ? "LAST_STEP_REACHED"
                            : "CARE_ENDED_PENDING_COMPLETION");
        } else {
            session.applyServerGuideProgress(currentStep.getCode(), true, true, "");
        }
    }

    private SessionStatus statusFor(int stepOrder) {
        if (stepOrder <= 1) {
            return SessionStatus.MEETING;
        }
        if (stepOrder == 2) {
            return SessionStatus.WAITING;
        }
        if (stepOrder <= 4) {
            return SessionStatus.IN_TREATMENT;
        }
        if (stepOrder <= 12) {
            return SessionStatus.PAYMENT;
        }
        return SessionStatus.CARE_ENDED;
    }

    private static List<GuideStep> stepsFor(boolean legacyMode) {
        return legacyMode
                ? ManagerGuideLegacyPreviewCatalog.steps()
                : ManagerGuidePreviewCatalog.steps();
    }

    private static GuideStep resolveStep(List<GuideStep> steps, String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        for (GuideStep step : steps) {
            if (step.getCode().equals(code)) {
                return step;
            }
        }
        return steps.get(0);
    }

    @Nullable
    private static GuideStep findByOrder(List<GuideStep> steps, int order) {
        for (GuideStep step : steps) {
            if (step.getOrder() == order) {
                return step;
            }
        }
        return null;
    }

    private static final class PreviewDataRepository extends MockBodeulRepository {
        private static final String HOSPITAL_NAME = "서울대학교병원";
        private static final String DEPARTMENT_NAME = "신경과";

        private final HospitalGuide previewGuide;

        PreviewDataRepository(List<GuideStep> steps) {
            previewGuide = new HospitalGuide(
                    "debug-guide-preview",
                    1L,
                    HOSPITAL_NAME,
                    DEPARTMENT_NAME,
                    steps);
        }

        @Override
        public synchronized HospitalGuide getHospitalGuide(
                String hospitalName,
                String departmentName
        ) {
            if (previewGuide != null
                    && HOSPITAL_NAME.equals(hospitalName)
                    && DEPARTMENT_NAME.equals(departmentName)) {
                return previewGuide;
            }
            return super.getHospitalGuide(hospitalName, departmentName);
        }
    }
}
