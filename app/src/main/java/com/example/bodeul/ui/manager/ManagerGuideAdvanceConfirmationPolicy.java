package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

/**
 * 단계 완료 전에 사용자의 명시적 확인이 필요한 업무 단계를 판별한다.
 */
final class ManagerGuideAdvanceConfirmationPolicy {
    private ManagerGuideAdvanceConfirmationPolicy() {
    }

    static boolean requiresConfirmation(
            ManagerGuidePrimaryAction primaryAction,
            @Nullable String stepCode
    ) {
        return primaryAction == ManagerGuidePrimaryAction.ADVANCE
                && (ManagerGuideStepRegistry.isHospitalRoute(stepCode)
                        || ManagerGuideStepRegistry.isPharmacyRoute(stepCode));
    }

    static boolean canApplyConfirmation(
            ManagerGuidePrimaryAction primaryAction,
            @Nullable String expectedSessionId,
            @Nullable String expectedStepCode,
            @Nullable String activeSessionId,
            @Nullable String activeStepCode
    ) {
        return normalized(expectedSessionId).equals(normalized(activeSessionId))
                && normalized(expectedStepCode).equals(normalized(activeStepCode))
                && requiresConfirmation(primaryAction, activeStepCode);
    }

    private static String normalized(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
