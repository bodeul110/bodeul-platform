package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;

/**
 * 서버의 원본 단계 코드는 유지하면서, 같은 의미의 최신 전용 화면을 선택한다.
 */
final class ManagerGuidePresentationStepResolver {
    private ManagerGuidePresentationStepResolver() {
    }

    static String resolve(@Nullable String rawStepCode) {
        String code = rawStepCode == null ? "" : rawStepCode.trim();
        switch (code) {
            case "LEGACY_CORE_PATIENT_CONTACT":
                return "MEETING_CONFIRMATION";
            case "LEGACY_CORE_RECEPTION_PREPARATION":
                return "HOSPITAL_ROUTE";
            case "LEGACY_CORE_RECEPTION":
                return "RECEPTION_QUEUE";
            case "LEGACY_CORE_CONSULTATION":
                return "CONSULTATION_SUPPORT";
            case "LEGACY_CORE_PAYMENT":
                return "PAYMENT_EVIDENCE";
            case "LEGACY_CORE_PHARMACY":
                return "MEDICATION_CONFIRMATION";
            case "LEGACY_CORE_RETURN_AND_CLOSE":
                return "MANAGER_JOURNAL";
            default:
                return code;
        }
    }

    static boolean matches(@Nullable String rawStepCode, String presentationStepCode) {
        return presentationStepCode != null
                && presentationStepCode.equals(resolve(rawStepCode));
    }

    @Nullable
    static GuideStep toPresentationStep(@Nullable GuideStep step) {
        if (step == null) {
            return null;
        }
        return new GuideStep(
                resolve(step.getCode()),
                step.getOrder(),
                step.getTitle(),
                step.getDescription(),
                step.getVideoAssetId(),
                step.getVideoAssetVersion(),
                step.getVideoFallbackText());
    }
}
