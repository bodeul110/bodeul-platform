package com.example.bodeul.ui.manager;

import androidx.lifecycle.SavedStateHandle;

import com.example.bodeul.domain.model.MedicationComparisonDecision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ManagerGuideViewModelSavedStateTest {

    @Test
    public void reportDraftSurvivesViewModelRecreationState() {
        SavedStateHandle state = new SavedStateHandle();
        ManagerGuideViewModel.ReportDraft draft = new ManagerGuideViewModel.ReportDraft(
                "session-1",
                "요약",
                "진료",
                "복약",
                "약 이름",
                "변경",
                "일정",
                MedicationComparisonDecision.RECHECK_REQUIRED,
                "재확인",
                "다음 방문");

        ManagerGuideViewModel.saveReportDraft(state, draft);
        ManagerGuideViewModel.ReportDraft restored =
                ManagerGuideViewModel.restoreReportDraft(state);

        assertNotNull(restored);
        assertEquals("요약", restored.summary);
        assertEquals("진료", restored.treatment);
        assertEquals("복약", restored.medication);
        assertEquals(MedicationComparisonDecision.RECHECK_REQUIRED,
                restored.medicationComparisonDecision);
        assertEquals("다음 방문", restored.nextVisit);
    }

    @Test
    public void artifactRequestIdIsReusedOnlyForTheSameLogicalSelection() {
        SavedStateHandle state = new SavedStateHandle();

        String first = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/1");
        String retry = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/1");
        String replacement = ManagerGuideViewModel.artifactRequestId(
                state,
                "PAYMENT_EVIDENCE",
                "PAYMENT_EVIDENCE\ncontent://receipt/2");

        assertEquals(first, retry);
        assertNotEquals(first, replacement);
    }

    @Test
    public void vitalsDraftSurvivesReloadAndRecreationOnlyForItsSession() {
        SavedStateHandle state = new SavedStateHandle();
        ManagerGuideViewModel.saveVitalsDraft(state, "session-a",
                ManagerGuideVitalsDraft.fromInputs("12", "", "7", "68."));

        ManagerGuideVitalsDraft restored =
                ManagerGuideViewModel.restoreVitalsDraft(state, "session-a");
        assertNotNull(restored);
        assertEquals("12", restored.systolic);
        assertEquals("", restored.diastolic);
        assertEquals("7", restored.heartRate);
        assertEquals("68.", restored.weight);
        assertNull(ManagerGuideViewModel.restoreVitalsDraft(state, "session-b"));

        ManagerGuideViewModel.clearVitalsDraft(state, "session-a");
        assertNull(ManagerGuideViewModel.restoreVitalsDraft(state, "session-a"));
    }

    @Test
    public void vitalsDraftIsNotClearedByAnotherSessionSave() {
        SavedStateHandle state = new SavedStateHandle();
        ManagerGuideViewModel.saveVitalsDraft(state, "session-a",
                ManagerGuideVitalsDraft.fromInputs("120", "80", "", ""));

        ManagerGuideViewModel.clearVitalsDraft(state, "session-b");

        assertNotNull(ManagerGuideViewModel.restoreVitalsDraft(state, "session-a"));
    }

    @Test
    public void consultationDraftTracksDirtyFieldsAgainstServerBaseline() {
        ManagerGuideConsultationDraft draft = ManagerGuideConsultationDraft.fromInputs(
                "로컬 보호자 공유",
                "서버 현장 메모",
                "서버 보호자 공유",
                "서버 현장 메모");

        assertTrue(draft.isGuardianDirty());
        assertFalse(draft.isFieldNoteDirty());
        assertTrue(draft.hasUnsavedChanges());
    }

    @Test
    public void consultationDraftReconcileUpdatesCleanFieldAndKeepsDirtyField() {
        ManagerGuideConsultationDraft draft = ManagerGuideConsultationDraft.fromInputs(
                "로컬 보호자 공유",
                "이전 현장 메모",
                "이전 보호자 공유",
                "이전 현장 메모");

        ManagerGuideConsultationDraft reconciled = draft.reconcileServer(
                "원격 보호자 공유",
                "원격 현장 메모");

        assertEquals("로컬 보호자 공유", reconciled.guardianUpdate);
        assertEquals("원격 보호자 공유", reconciled.guardianBaseline);
        assertTrue(reconciled.isGuardianDirty());
        assertEquals("원격 현장 메모", reconciled.fieldNote);
        assertEquals("원격 현장 메모", reconciled.fieldNoteBaseline);
        assertFalse(reconciled.isFieldNoteDirty());
    }

    @Test
    public void consultationDraftBecomesCleanWhenServerMatchesLocalValues() {
        ManagerGuideConsultationDraft draft = ManagerGuideConsultationDraft.fromInputs(
                "새 보호자 공유",
                "새 현장 메모",
                "이전 보호자 공유",
                "이전 현장 메모");

        ManagerGuideConsultationDraft reconciled = draft.reconcileServer(
                "새 보호자 공유",
                "새 현장 메모");

        assertEquals("새 보호자 공유", reconciled.guardianUpdate);
        assertEquals("새 현장 메모", reconciled.fieldNote);
        assertFalse(reconciled.isGuardianDirty());
        assertFalse(reconciled.isFieldNoteDirty());
        assertFalse(reconciled.hasUnsavedChanges());
    }
}
