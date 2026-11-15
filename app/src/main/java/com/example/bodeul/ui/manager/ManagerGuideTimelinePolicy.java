package com.example.bodeul.ui.manager;

import com.example.bodeul.domain.model.GuideStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 저장 단계는 그대로 두고 완성본의 표시 단계만 묶는다. */
final class ManagerGuideTimelinePolicy {
    static int groupOf(String rawCode) {
        if ("LEGACY_CORE_RETURN_AND_CLOSE".equals(rawCode)) return 9;
        return switch (ManagerGuidePresentationStepResolver.resolve(rawCode)) {
            case "MEETING_CONFIRMATION" -> 1;
            case "HOSPITAL_ROUTE" -> 2;
            case "RECEPTION_QUEUE" -> 3;
            case "VITALS_CHECK" -> 4;
            case "PRE_CONSULTATION" -> 5;
            case "CONSULTATION_SUPPORT", "CONSULTATION_SUMMARY" -> 6;
            case "PAYMENT_EVIDENCE" -> 7;
            case "PHARMACY_ROUTE", "PRESCRIPTION_DOCUMENTS", "MEDICATION_CONFIRMATION" -> 8;
            case "CARE_COMPLETION" -> 9;
            case "MANAGER_JOURNAL" -> 10;
            default -> 0;
        };
    }

    static List<Item> build(List<GuideStep> steps, List<ManagerGuideStageModel> stages) {
        Map<Integer, Item> groups = new LinkedHashMap<>();
        for (GuideStep step : steps) {
            int group = groupOf(step.getCode());
            if (group == 10) continue;
            // 모르는 단계와 구형 가이드의 없는 단계를 완료로 추정하지 않는다.
            int key = group == 0 ? -step.getOrder() : group;
            Item item = groups.computeIfAbsent(key, ignored -> new Item(group, step.getTitle()));
            ManagerGuideStageState state = ManagerGuideStageState.UPCOMING;
            for (ManagerGuideStageModel stage : stages) {
                if (stage.getOrder() == step.getOrder()) {
                    state = stage.getState();
                    break;
                }
            }
            item.states.add(state);
            item.descriptions.add(step.getTitle() + "\n" + step.getDescription());
        }
        return new ArrayList<>(groups.values());
    }

    static final class Item {
        final int group;
        final String fallbackTitle;
        final List<String> descriptions = new ArrayList<>();
        private final List<ManagerGuideStageState> states = new ArrayList<>();

        Item(int group, String fallbackTitle) {
            this.group = group;
            this.fallbackTitle = fallbackTitle;
        }

        ManagerGuideStageState state() {
            if (states.contains(ManagerGuideStageState.ACTIVE)) return ManagerGuideStageState.ACTIVE;
            for (ManagerGuideStageState state : states) {
                if (state != ManagerGuideStageState.COMPLETED) return ManagerGuideStageState.UPCOMING;
            }
            return states.isEmpty() ? ManagerGuideStageState.UPCOMING : ManagerGuideStageState.COMPLETED;
        }
    }
}
