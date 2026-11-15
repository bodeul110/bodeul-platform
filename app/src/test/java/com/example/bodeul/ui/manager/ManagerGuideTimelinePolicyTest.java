package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertEquals;

import com.example.bodeul.domain.model.GuideStep;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

public class ManagerGuideTimelinePolicyTest {
    private static final String[] CODES = {"MEETING_CONFIRMATION", "HOSPITAL_ROUTE",
            "RECEPTION_QUEUE", "VITALS_CHECK", "PRE_CONSULTATION", "CONSULTATION_SUPPORT",
            "CONSULTATION_SUMMARY", "PAYMENT_EVIDENCE", "PHARMACY_ROUTE",
            "PRESCRIPTION_DOCUMENTS", "MEDICATION_CONFIRMATION", "CARE_COMPLETION", "MANAGER_JOURNAL"};

    @Test public void thirteenServerSteps_areNineDisplayGroups_withoutJournal() {
        List<ManagerGuideTimelinePolicy.Item> items = build(CODES, 6);
        assertEquals(9, items.size());
        for (int i = 0; i < items.size(); i++) assertEquals(i + 1, items.get(i).group);
        assertEquals(2, items.get(5).descriptions.size());
        assertEquals(3, items.get(7).descriptions.size());
    }

    @Test public void summaryRemainsActive_afterConsultationIsDone() {
        List<ManagerGuideTimelinePolicy.Item> items = build(CODES, 7);
        assertEquals(ManagerGuideStageState.ACTIVE, items.get(5).state());
        assertEquals(ManagerGuideStageState.UPCOMING, items.get(6).state());
    }

    @Test public void medicationGroup_isNotDoneUntilAllThreeServerStepsAreDone() {
        for (int step = 9; step <= 11; step++) {
            assertEquals(ManagerGuideStageState.ACTIVE, build(CODES, step).get(7).state());
        }
        assertEquals(ManagerGuideStageState.COMPLETED, build(CODES, 12).get(7).state());
    }

    @Test public void noActiveStepInsidePartiallyCompletedGroup_isStillNotComplete() {
        List<GuideStep> steps = steps(new String[]{"CONSULTATION_SUPPORT", "CONSULTATION_SUMMARY"});
        List<ManagerGuideStageModel> states = List.of(stage(1, ManagerGuideStageState.COMPLETED));
        assertEquals(ManagerGuideStageState.UPCOMING,
                ManagerGuideTimelinePolicy.build(steps, states).get(0).state());
    }

    @Test public void legacyGuide_doesNotInventMissingVitalsOrPreparationSteps() {
        String[] codes = {"LEGACY_CORE_PATIENT_CONTACT", "LEGACY_CORE_RECEPTION_PREPARATION",
                "LEGACY_CORE_RECEPTION", "LEGACY_CORE_CONSULTATION", "LEGACY_CORE_PAYMENT",
                "LEGACY_CORE_PHARMACY", "LEGACY_CORE_RETURN_AND_CLOSE"};
        List<ManagerGuideTimelinePolicy.Item> items = build(codes, 7);
        assertEquals(List.of(1, 2, 3, 6, 7, 8, 9),
                items.stream().map(item -> item.group).toList());
        assertEquals(ManagerGuideStageState.ACTIVE, items.get(6).state());
    }

    @Test public void unknownStepsRemainSeparate_andKeepActualState() {
        List<ManagerGuideTimelinePolicy.Item> items = build(new String[]{"EXTENSION_A", "EXTENSION_B"}, 2);
        assertEquals(2, items.size());
        assertEquals(0, items.get(0).group);
        assertEquals(ManagerGuideStageState.COMPLETED, items.get(0).state());
        assertEquals(ManagerGuideStageState.ACTIVE, items.get(1).state());
        assertEquals(0, ManagerGuideTimelinePolicy.groupOf(null));
    }

    @Test public void stateMatchesStepOrder_notListPosition() {
        List<GuideStep> steps = List.of(new GuideStep("HOSPITAL_ROUTE", 2, "병원", "안내"));
        assertEquals(ManagerGuideStageState.ACTIVE,
                ManagerGuideTimelinePolicy.build(steps, List.of(stage(2, ManagerGuideStageState.ACTIVE))).get(0).state());
    }

    private List<ManagerGuideTimelinePolicy.Item> build(String[] codes, int current) {
        List<ManagerGuideStageModel> stages = new ArrayList<>();
        for (int i = 1; i <= codes.length; i++) stages.add(stage(i, i < current
                ? ManagerGuideStageState.COMPLETED : i == current ? ManagerGuideStageState.ACTIVE
                : ManagerGuideStageState.UPCOMING));
        return ManagerGuideTimelinePolicy.build(steps(codes), stages);
    }

    private List<GuideStep> steps(String[] codes) {
        List<GuideStep> steps = new ArrayList<>();
        for (int i = 0; i < codes.length; i++) steps.add(new GuideStep(codes[i], i + 1, "단계", "안내"));
        return steps;
    }

    private ManagerGuideStageModel stage(int order, ManagerGuideStageState state) {
        return new ManagerGuideStageModel(order, "단계", "안내", "상태", state);
    }
}
