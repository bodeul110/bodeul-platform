package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ManagerGuideCoordinatorPolicyTest {
    private static final List<String> PRODUCT_GUIDE_STEP_CODES = Arrays.asList(
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
    private static final List<String> LEGACY_GUIDE_STEP_CODES = Arrays.asList(
            "LEGACY_CORE_PATIENT_CONTACT",
            "LEGACY_CORE_RECEPTION_PREPARATION",
            "LEGACY_CORE_RECEPTION",
            "LEGACY_CORE_CONSULTATION",
            "LEGACY_CORE_PAYMENT",
            "LEGACY_CORE_PHARMACY",
            "LEGACY_CORE_RETURN_AND_CLOSE");

    @Test
    public void progressPolicy_usesServerAdvanceDecision() {
        CompanionSession session = createSession(2);
        session.applyServerGuideProgress(
                "RECEPTION_QUEUE",
                true,
                false,
                "STEP_CONTRACT_MISMATCH");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 13);

        assertFalse(decision.isAdvanceEnabled());
        assertEquals(
                ManagerGuideProgressPolicy.State.CONTRACT_MISMATCH,
                decision.getState());
    }

    @Test
    public void progressPolicy_allowsEntryBeforeFirstStepWhenServerAllowsIt() {
        CompanionSession session = createSession(0);
        session.applyServerGuideProgress("", true, true, "");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 1);

        assertTrue(decision.isAdvanceEnabled());
        assertEquals(ManagerGuideProgressPolicy.State.ADVANCE, decision.getState());
    }

    @Test
    public void progressPolicy_marksEmptyGuideAsPreparing() {
        CompanionSession session = createSession(0);
        session.applyServerGuideProgress("", true, false, "GUIDE_NOT_READY");

        ManagerGuideProgressPolicy.Decision decision =
                ManagerGuideProgressPolicy.resolve(session, 0);

        assertFalse(decision.isAdvanceEnabled());
        assertEquals(
                ManagerGuideProgressPolicy.State.GUIDE_NOT_READY,
                decision.getState());
    }

    @Test
    public void progressPolicy_requiresPreConsultationConfirmationBeforeAdvance() {
        CompanionSession session = createSession(5);
        session.applyServerGuideProgress("PRE_CONSULTATION", true, true, "");

        ManagerGuideProgressPolicy.Decision blocked =
                ManagerGuideProgressPolicy.resolve(session, 13, "PRE_CONSULTATION");

        assertFalse(blocked.isAdvanceEnabled());
        assertEquals(ManagerGuideProgressPolicy.State.INPUT_REQUIRED, blocked.getState());
        assertEquals(
                ManagerGuidePrimaryAction.NONE,
                ManagerGuideCoordinator.resolvePrimaryAction(blocked));
        assertTrue(ManagerGuideCoordinator.isStepInputEnabled(blocked));

        session.setPreConsultationConfirmed(true);
        ManagerGuideProgressPolicy.Decision confirmed =
                ManagerGuideProgressPolicy.resolve(session, 13, "PRE_CONSULTATION");

        assertTrue(confirmed.isAdvanceEnabled());
        assertEquals(ManagerGuideProgressPolicy.State.ADVANCE, confirmed.getState());
    }

    @Test
    public void focusResolver_restoresUnknownCurrentCodeAfterReload() {
        GuideStep first = new GuideStep("MEETING_CONFIRMATION", 1, "상봉 확인", "설명 1");
        GuideStep extension = new GuideStep(
                "UNLISTED_EXTENSION",
                2,
                "병원별 추가 단계",
                "추가 안내");
        CompanionSession restored = createSession(1);
        restored.applyServerGuideProgress("UNLISTED_EXTENSION", true, true, "");

        GuideStep result = ManagerGuideFocusResolver.resolve(
                Arrays.asList(first, extension),
                restored);

        assertSame(extension, result);
    }

    @Test
    public void focusResolver_returnsNullForEmptySnapshot() {
        assertEquals(
                null,
                ManagerGuideFocusResolver.resolve(
                        Collections.emptyList(),
                        createSession(0)));
    }

    @Test
    public void pharmacyRouteAction_usesCurrentStepCodeInsteadOfOrderFallback() {
        CompanionSession pharmacyRoute = createSession(9);
        pharmacyRoute.applyServerGuideProgress("PHARMACY_ROUTE", true, true, "");
        assertTrue(ManagerGuideCoordinator.shouldShowPharmacyRouteAction(pharmacyRoute));

        CompanionSession unknownAtSameOrder = createSession(9);
        unknownAtSameOrder.applyServerGuideProgress("UNLISTED_EXTENSION", true, true, "");
        assertFalse(ManagerGuideCoordinator.shouldShowPharmacyRouteAction(unknownAtSameOrder));
    }

    @Test
    public void externalMapFallbackQuery_usesOnlyTrimmedHospitalName() {
        assertEquals(
                "서울대학교병원",
                ManagerGuideCoordinator.buildExternalMapFallbackQuery("  서울대학교병원  "));
        assertEquals("", ManagerGuideCoordinator.buildExternalMapFallbackQuery(null));
    }

    @Test
    public void reportCompletionAction_requiresValidatedLastStepDecision() {
        CompanionSession lastStep = createSession(13);
        lastStep.applyServerGuideProgress(
                "MANAGER_JOURNAL",
                true,
                false,
                "LAST_STEP_REACHED");
        ManagerGuideProgressPolicy.Decision lastStepDecision =
                ManagerGuideProgressPolicy.resolve(lastStep, 13);

        CompanionSession contractMismatch = createSession(13);
        contractMismatch.applyServerGuideProgress(
                "",
                true,
                false,
                "STEP_CONTRACT_MISMATCH");
        ManagerGuideProgressPolicy.Decision mismatchDecision =
                ManagerGuideProgressPolicy.resolve(contractMismatch, 13);

        assertEquals(
                ManagerGuidePrimaryAction.SUBMIT_REPORT,
                ManagerGuideCoordinator.resolvePrimaryAction(lastStepDecision));
        assertTrue(ManagerGuideCoordinator.isPrimaryActionEnabled(lastStepDecision));
        assertTrue(ManagerGuideCoordinator.isStepInputEnabled(lastStepDecision));
        assertEquals(
                ManagerGuidePrimaryAction.NONE,
                ManagerGuideCoordinator.resolvePrimaryAction(mismatchDecision));
        assertFalse(ManagerGuideCoordinator.isPrimaryActionEnabled(mismatchDecision));
        assertFalse(ManagerGuideCoordinator.isStepInputEnabled(mismatchDecision));
    }

    @Test
    public void careCompletionAndReportRetry_useDifferentPrimaryActions() {
        CompanionSession careCompletion = createSession(12);
        careCompletion.applyServerGuideProgress(
                "CARE_COMPLETION",
                true,
                true,
                "");
        ManagerGuideProgressPolicy.Decision careDecision =
                ManagerGuideProgressPolicy.resolve(careCompletion, 13, "CARE_COMPLETION");

        CompanionSession careEnded = createSession(13, SessionStatus.CARE_ENDED);
        careEnded.applyServerGuideProgress(
                "MANAGER_JOURNAL",
                true,
                false,
                "CARE_ENDED_PENDING_COMPLETION");
        ManagerGuideProgressPolicy.Decision journalDecision =
                ManagerGuideProgressPolicy.resolve(careEnded, 13, "MANAGER_JOURNAL");

        CompanionSession reportFailed = createSession(13, SessionStatus.COMPLETED);
        reportFailed.applyCompletionState(
                1L,
                "",
                "FAILED",
                1,
                "REPORT_WRITE_FAILED",
                2L,
                List.of());
        reportFailed.applyServerGuideProgress(
                "MANAGER_JOURNAL",
                true,
                false,
                "REPORT_RETRY_REQUIRED");
        ManagerGuideProgressPolicy.Decision retryDecision =
                ManagerGuideProgressPolicy.resolve(reportFailed, 13, "MANAGER_JOURNAL");

        assertEquals(
                ManagerGuidePrimaryAction.END_CARE,
                ManagerGuideCoordinator.resolvePrimaryAction(careDecision, "CARE_COMPLETION"));
        assertEquals(
                ManagerGuidePrimaryAction.SUBMIT_REPORT,
                ManagerGuideCoordinator.resolvePrimaryAction(journalDecision, "MANAGER_JOURNAL"));
        assertEquals(ManagerGuideProgressPolicy.State.REPORT_RETRY, retryDecision.getState());
        assertEquals(
                ManagerGuidePrimaryAction.SUBMIT_REPORT,
                ManagerGuideCoordinator.resolvePrimaryAction(retryDecision, "MANAGER_JOURNAL"));
        assertTrue(ManagerGuideCoordinator.isStepInputEnabled(retryDecision));
    }

    @Test
    public void additiveSnapshot_screenModelHidesJournalReportUntilActualLastStep() {
        GuideStep journal = new GuideStep(
                "MANAGER_JOURNAL",
                13,
                "매니저 일지",
                "동행 기록을 정리합니다.");
        GuideStep extension = new GuideStep(
                "HOSPITAL_EXTENSION",
                14,
                "병원별 추가 단계",
                "병원별 안내를 확인합니다.");
        List<GuideStep> steps = new ArrayList<>();
        for (int order = 1; order <= 12; order++) {
            steps.add(new GuideStep(
                    "STEP_" + order,
                    order,
                    "단계 " + order,
                    "설명 " + order));
        }
        steps.add(journal);
        steps.add(extension);

        CompanionSession journalSession = createSession(13);
        journalSession.applyServerGuideProgress("MANAGER_JOURNAL", true, true, "");
        GuideStep journalFocus = ManagerGuideFocusResolver.resolve(steps, journalSession);
        ManagerGuideProgressPolicy.Decision journalDecision =
                ManagerGuideProgressPolicy.resolve(journalSession, 14);

        assertSame(journal, journalFocus);
        assertEquals(
                ManagerGuidePrimaryAction.ADVANCE,
                ManagerGuideCoordinator.resolvePrimaryAction(journalDecision));
        assertTrue(ManagerGuideCoordinator.isPrimaryActionEnabled(journalDecision));
        assertFalse(ManagerGuideCoordinator.resolveSectionVisibility(
                journalFocus,
                ManagerGuideCoordinator.resolvePrimaryAction(journalDecision))
                .hasReportSection());

        CompanionSession extensionSession = createSession(14);
        extensionSession.applyServerGuideProgress(
                "HOSPITAL_EXTENSION",
                true,
                false,
                "LAST_STEP_REACHED");
        GuideStep extensionFocus = ManagerGuideFocusResolver.resolve(steps, extensionSession);
        ManagerGuideProgressPolicy.Decision extensionDecision =
                ManagerGuideProgressPolicy.resolve(extensionSession, 14);

        assertSame(extension, extensionFocus);
        assertEquals(
                ManagerGuidePrimaryAction.SUBMIT_REPORT,
                ManagerGuideCoordinator.resolvePrimaryAction(extensionDecision));
        assertTrue(ManagerGuideCoordinator.isPrimaryActionEnabled(extensionDecision));
        assertTrue(ManagerGuideCoordinator.resolveSectionVisibility(
                extensionFocus,
                ManagerGuideCoordinator.resolvePrimaryAction(extensionDecision))
                .hasReportSection());
    }

    @Test
    public void fullSnapshots_restoreEveryStepAndUseActualLastStepForReport() {
        for (int stepCount : Arrays.asList(7, 13, 14)) {
            List<GuideStep> steps = createTransitionSteps(stepCount);
            for (int currentOrder = 0; currentOrder <= stepCount; currentOrder++) {
                String currentCode = currentOrder == 0
                        ? ""
                        : steps.get(currentOrder - 1).getCode();
                boolean canAdvance = currentOrder < stepCount;
                String blockedReason = canAdvance ? "" : "LAST_STEP_REACHED";
                CompanionSession restored = createSession(currentOrder);
                if ("PRE_CONSULTATION".equals(currentCode)) {
                    restored.setPreConsultationConfirmed(true);
                }
                restored.applyServerGuideProgress(
                        currentCode,
                        true,
                        canAdvance,
                        blockedReason);

                GuideStep focus = ManagerGuideFocusResolver.resolve(steps, restored);
                ManagerGuideProgressPolicy.Decision decision =
                        ManagerGuideProgressPolicy.resolve(restored, stepCount);
                ManagerGuidePrimaryAction action =
                        ManagerGuideCoordinator.resolvePrimaryAction(decision);

                assertSame(
                        steps.get(Math.max(1, currentOrder) - 1),
                        focus);
                assertEquals(
                        canAdvance
                                ? ManagerGuidePrimaryAction.ADVANCE
                                : ManagerGuidePrimaryAction.SUBMIT_REPORT,
                        action);
                assertTrue(ManagerGuideCoordinator.isPrimaryActionEnabled(decision));
                assertEquals(
                        !canAdvance,
                        ManagerGuideCoordinator.resolveSectionVisibility(focus, action)
                                .hasReportSection());
            }

            GuideStep lastStep = steps.get(stepCount - 1);
            CompanionSession completed = createSession(stepCount, SessionStatus.COMPLETED);
            completed.applyServerGuideProgress(
                    lastStep.getCode(),
                    true,
                    false,
                    "SESSION_TERMINAL");
            ManagerGuideProgressPolicy.Decision completedDecision =
                    ManagerGuideProgressPolicy.resolve(completed, stepCount);

            assertSame(lastStep, ManagerGuideFocusResolver.resolve(steps, completed));
            assertEquals(
                    ManagerGuideProgressPolicy.State.COMPLETED,
                    completedDecision.getState());
            assertEquals(
                    ManagerGuidePrimaryAction.NONE,
                    ManagerGuideCoordinator.resolvePrimaryAction(completedDecision));
            assertFalse(ManagerGuideCoordinator.isPrimaryActionEnabled(completedDecision));
            assertFalse(ManagerGuideCoordinator.resolveSectionVisibility(
                    lastStep,
                    ManagerGuidePrimaryAction.NONE).hasReportSection());
        }
    }

    private List<GuideStep> createTransitionSteps(int stepCount) {
        List<String> codes = stepCount == 7
                ? LEGACY_GUIDE_STEP_CODES
                : new ArrayList<>(PRODUCT_GUIDE_STEP_CODES);
        if (stepCount == 14) {
            codes.add("HOSPITAL_EXTENSION");
        }
        List<GuideStep> steps = new ArrayList<>();
        for (int index = 0; index < codes.size(); index++) {
            int order = index + 1;
            steps.add(new GuideStep(
                    codes.get(index),
                    order,
                    "단계 " + order,
                    "설명 " + order));
        }
        return steps;
    }

    private CompanionSession createSession(int currentStepOrder) {
        return createSession(currentStepOrder, SessionStatus.MEETING);
    }

    private CompanionSession createSession(int currentStepOrder, SessionStatus status) {
        return new CompanionSession(
                "session-id",
                "appointment-id",
                "manager-id",
                currentStepOrder,
                status,
                "",
                "",
                "",
                "",
                "",
                false);
    }
}
