package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ManagerGuidePreviewRepositoryTest {
    private static final List<String> EXPECTED_CODES = List.of(
            "MEETING_CONFIRMATION",
            "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE",
            "VITALS_CHECK",
            "PRE_CONSULTATION",
            "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY",
            "PAYMENT_EVIDENCE",
            "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS",
            "MEDICATION_CONFIRMATION",
            "CARE_COMPLETION",
            "MANAGER_JOURNAL");
    private static final List<String> EXPECTED_TITLES = List.of(
            "상봉 확인",
            "병원 이동",
            "접수와 대기",
            "기초 측정",
            "진료 전 확인",
            "진료 동행",
            "진료 요약",
            "수납 증빙",
            "약국 이동",
            "처방 자료",
            "복약 확인",
            "동행 종료",
            "매니저 일지");

    @Test
    public void catalog_matchesThirteenStepContract() {
        List<GuideStep> steps = ManagerGuidePreviewCatalog.steps();

        assertEquals(13, steps.size());
        for (int index = 0; index < steps.size(); index++) {
            GuideStep step = steps.get(index);
            assertEquals(index + 1, step.getOrder());
            assertEquals(EXPECTED_CODES.get(index), step.getCode());
            assertEquals(EXPECTED_TITLES.get(index), step.getTitle());
            assertFalse(step.getDescription().trim().isEmpty());
        }
    }

    @Test
    public void selectedStep_usesLocalCodedGuideAndServerDecision() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("VITALS_CHECK");

        ManagerDashboard dashboard = dashboard(repository);

        assertEquals(13, dashboard.getHospitalGuide().getSteps().size());
        assertEquals(4, dashboard.getSession().getCurrentStepOrder());
        assertEquals("VITALS_CHECK", dashboard.getSession().getCurrentStepCode());
        assertTrue(dashboard.getSession().hasServerAdvanceDecision());
        assertTrue(dashboard.getSession().isServerAdvanceAllowed());
    }

    @Test
    public void careCompletionAdvance_opensJournalCompletionState() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("CARE_COMPLETION");
        ManagerDashboard current = dashboard(repository);
        AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();

        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                callback(advanced));

        ManagerDashboard result = advanced.get();
        assertNotNull(result);
        assertEquals(13, result.getSession().getCurrentStepOrder());
        assertEquals("MANAGER_JOURNAL", result.getSession().getCurrentStepCode());
        assertEquals(SessionStatus.CARE_ENDED, result.getSession().getStatus());
        assertFalse(result.getSession().isServerAdvanceAllowed());
        assertEquals(
                "CARE_ENDED_PENDING_COMPLETION",
                result.getSession().getAdvanceBlockedReason());
    }

    @Test
    public void journalSelection_startsInCareEndedCompletionState() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("MANAGER_JOURNAL");

        ManagerDashboard dashboard = dashboard(repository);

        assertEquals(13, dashboard.getSession().getCurrentStepOrder());
        assertEquals("MANAGER_JOURNAL", dashboard.getSession().getCurrentStepCode());
        assertEquals(SessionStatus.CARE_ENDED, dashboard.getSession().getStatus());
        assertTrue(dashboard.getSession().hasServerAdvanceDecision());
        assertFalse(dashboard.getSession().isServerAdvanceAllowed());
        assertEquals(
                "CARE_ENDED_PENDING_COMPLETION",
                dashboard.getSession().getAdvanceBlockedReason());
    }

    @Test
    public void preConsultation_requiresConfirmationBeforeAdvance() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("PRE_CONSULTATION");
        ManagerDashboard initial = dashboard(repository);
        assertFalse(initial.getSession().isServerAdvanceAllowed());
        assertEquals("STEP_INPUT_REQUIRED", initial.getSession().getAdvanceBlockedReason());
        AtomicReference<ManagerDashboard> updated = new AtomicReference<>();

        repository.updatePreConsultationConfirmed(
                ManagerGuidePreviewRepository.MANAGER_ID,
                true,
                callback(updated));

        assertNotNull(updated.get());
        assertTrue(updated.get().getSession().isPreConsultationConfirmed());
        assertTrue(updated.get().getSession().isServerAdvanceAllowed());
        assertEquals("", updated.get().getSession().getAdvanceBlockedReason());
    }

    @Test
    public void prescriptionWithoutArtifacts_advancesToMedicationConfirmation() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("PRESCRIPTION_DOCUMENTS");
        ManagerDashboard current = dashboard(repository);
        assertTrue(current.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE).isEmpty());
        AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();

        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                callback(advanced));

        assertNotNull(advanced.get());
        assertEquals(11, advanced.get().getSession().getCurrentStepOrder());
        assertEquals(
                "MEDICATION_CONFIRMATION",
                advanced.get().getSession().getCurrentStepCode());
    }

    @Test
    public void paymentWithoutArtifacts_advancesToPharmacyRoute() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("PAYMENT_EVIDENCE");
        ManagerDashboard current = dashboard(repository);
        assertTrue(current.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).isEmpty());
        AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();

        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                callback(advanced));

        assertNotNull(advanced.get());
        assertEquals(9, advanced.get().getSession().getCurrentStepOrder());
        assertEquals("PHARMACY_ROUTE", advanced.get().getSession().getCurrentStepCode());
    }

    @Test
    public void consultationNotes_areRetainedAfterAdvanceToSummary() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("CONSULTATION_SUPPORT");
        AtomicReference<ManagerDashboard> guardianSaved = new AtomicReference<>();
        AtomicReference<ManagerDashboard> fieldNoteSaved = new AtomicReference<>();
        AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();
        ManagerDashboard current = dashboard(repository);

        repository.saveConsultationGuardianUpdate(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                "보호자 공유 진료 진행",
                callback(guardianSaved));
        assertNotNull(guardianSaved.get());
        assertEquals(
                "보호자 공유 진료 진행",
                guardianSaved.get().getSession().getGuardianUpdate());

        repository.saveConsultationFieldNote(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                "진료실 현장 메모",
                callback(fieldNoteSaved));
        assertNotNull(fieldNoteSaved.get());
        assertEquals(
                "진료실 현장 메모",
                fieldNoteSaved.get().getSession().getFieldPhotoNote());

        current = fieldNoteSaved.get();
        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                callback(advanced));

        assertNotNull(advanced.get());
        assertEquals(7, advanced.get().getSession().getCurrentStepOrder());
        assertEquals(
                "CONSULTATION_SUMMARY",
                advanced.get().getSession().getCurrentStepCode());
        assertEquals(
                "보호자 공유 진료 진행",
                advanced.get().getSession().getGuardianUpdate());
        assertEquals(
                "진료실 현장 메모",
                advanced.get().getSession().getFieldPhotoNote());
    }

    @Test
    public void consultationSave_rejectsStaleStepWithoutChangingValue() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("CONSULTATION_SUPPORT");
        ManagerDashboard current = dashboard(repository);
        String originalGuardianUpdate = current.getSession().getGuardianUpdate();
        AtomicReference<String> error = new AtomicReference<>();

        repository.saveConsultationGuardianUpdate(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                "CONSULTATION_SUMMARY",
                "저장되면 안 되는 오래된 입력",
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        throw new AssertionError("오래된 단계 쓰기가 성공했습니다.");
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                    }
                });

        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, error.get());
        assertEquals(
                originalGuardianUpdate,
                dashboard(repository).getSession().getGuardianUpdate());
    }

    @Test
    public void prescriptionArtifacts_replaceAndClearWithoutTouchingPaymentEvidence() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("PAYMENT_EVIDENCE");

        ManagerDashboard withPayment = replaceArtifacts(
                repository,
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                1);
        assertEquals(1, withPayment.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());

        AtomicReference<ManagerDashboard> pharmacyRoute = new AtomicReference<>();
        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                withPayment.getSession().getId(),
                withPayment.getSession().getCurrentStepCode(),
                callback(pharmacyRoute));
        assertNotNull(pharmacyRoute.get());
        AtomicReference<ManagerDashboard> prescriptionStep = new AtomicReference<>();
        repository.advanceCurrentStep(
                ManagerGuidePreviewRepository.MANAGER_ID,
                pharmacyRoute.get().getSession().getId(),
                pharmacyRoute.get().getSession().getCurrentStepCode(),
                callback(prescriptionStep));
        assertNotNull(prescriptionStep.get());
        assertEquals("PRESCRIPTION_DOCUMENTS",
                prescriptionStep.get().getSession().getCurrentStepCode());

        ManagerDashboard withThreePrescriptions = replaceArtifacts(
                repository,
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE,
                3);
        assertEquals(3, withThreePrescriptions.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE).size());
        assertEquals(1, withThreePrescriptions.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());

        ManagerDashboard withOnePrescription = replaceArtifacts(
                repository,
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE,
                1);
        assertEquals(1, withOnePrescription.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE).size());
        assertEquals(1, withOnePrescription.getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());

        AtomicReference<ManagerDashboard> cleared = new AtomicReference<>();
        repository.clearSessionArtifacts(
                ManagerGuidePreviewRepository.MANAGER_ID,
                withOnePrescription.getSession().getId(),
                withOnePrescription.getSession().getCurrentStepCode(),
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE,
                callback(cleared));

        assertNotNull(cleared.get());
        assertTrue(cleared.get().getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE).isEmpty());
        assertEquals(1, cleared.get().getSession().getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());
    }

    @Test
    public void journalSubmission_returnsLocalReportWithoutActiveSessionReload() {
        ManagerGuidePreviewRepository repository =
                new ManagerGuidePreviewRepository("MANAGER_JOURNAL");
        AtomicReference<SessionReport> submitted = new AtomicReference<>();

        repository.submitSessionReport(
                ManagerGuidePreviewRepository.MANAGER_ID,
                "동행 요약",
                "진료 내용",
                "복약 메모",
                "테스트 약",
                "변경 없음",
                "조식 후 복용",
                null,
                "",
                "2026-10-01",
                new RepositoryCallback<SessionReport>() {
                    @Override
                    public void onSuccess(SessionReport result) {
                        submitted.set(result);
                    }

                    @Override
                    public void onError(String message) {
                        throw new AssertionError(message);
                    }
                });

        assertNotNull(submitted.get());
        assertEquals("debug-preview-report", submitted.get().getId());
        assertEquals("동행 요약", submitted.get().getSummary());
    }

    private ManagerDashboard dashboard(ManagerGuidePreviewRepository repository) {
        AtomicReference<ManagerDashboard> result = new AtomicReference<>();
        repository.getManagerDashboard(
                ManagerGuidePreviewRepository.MANAGER_ID,
                callback(result));
        assertNotNull(result.get());
        return result.get();
    }

    private ManagerDashboard replaceArtifacts(
            ManagerGuidePreviewRepository repository,
            String purpose,
            int count
    ) {
        List<Uri> uris = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            uris.add(null);
        }
        AtomicReference<ManagerDashboard> result = new AtomicReference<>();
        ManagerDashboard current = dashboard(repository);
        repository.replaceSessionArtifacts(
                ManagerGuidePreviewRepository.MANAGER_ID,
                current.getSession().getId(),
                current.getSession().getCurrentStepCode(),
                purpose,
                "debug-request-" + purpose + "-" + count,
                uris,
                callback(result));
        assertNotNull(result.get());
        return result.get();
    }

    private RepositoryCallback<ManagerDashboard> callback(
            AtomicReference<ManagerDashboard> result
    ) {
        return new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard dashboard) {
                result.set(dashboard);
            }

            @Override
            public void onError(String message) {
                throw new AssertionError(message);
            }
        };
    }
}
