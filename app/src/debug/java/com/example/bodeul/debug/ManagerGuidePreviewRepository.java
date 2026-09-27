package com.example.bodeul.debug;

import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import java.util.List;

/** 서버 쓰기 없이 13단계 화면과 로컬 상태 변경만 제공한다. */
final class ManagerGuidePreviewRepository extends MockManagerRepository {
    static final String MANAGER_ID = "manager-1";

    private final PreviewDataRepository dataRepository;

    ManagerGuidePreviewRepository(String initialStepCode) {
        this(new PreviewDataRepository(), initialStepCode);
    }

    private ManagerGuidePreviewRepository(
            PreviewDataRepository dataRepository,
            String initialStepCode
    ) {
        super(dataRepository);
        this.dataRepository = dataRepository;
        GuideStep initialStep = ManagerGuidePreviewCatalog.resolve(initialStepCode);
        CompanionSession session = requirePreviewSession();
        session.setCurrentStepOrder(initialStep.getOrder());
        applyPreviewProgress(session);
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

    private void applyPreviewProgress(CompanionSession session) {
        GuideStep currentStep = ManagerGuidePreviewCatalog.findByOrder(
                session.getCurrentStepOrder());
        if (currentStep == null) {
            currentStep = ManagerGuidePreviewCatalog.steps().get(0);
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
        } else if (currentStep.getOrder() == ManagerGuidePreviewCatalog.steps().size()) {
            session.applyServerGuideProgress(
                    currentStep.getCode(),
                    true,
                    false,
                    "CARE_ENDED_PENDING_COMPLETION");
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

    private static final class PreviewDataRepository extends MockBodeulRepository {
        private static final String HOSPITAL_NAME = "서울내과병원";
        private static final String DEPARTMENT_NAME = "신경과";

        private final HospitalGuide previewGuide = new HospitalGuide(
                "debug-guide-preview",
                1L,
                HOSPITAL_NAME,
                DEPARTMENT_NAME,
                ManagerGuidePreviewCatalog.steps());

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
