package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.GuideStep;

/**
 * 서버 단계 코드를 현재 공통 가이드 화면의 표시 유형으로 연결한다.
 */
final class ManagerGuideStepRegistry {
    enum PresentationType {
        MEETING,
        DOCUMENT,
        TREATMENT,
        MEDICATION,
        FINISH,
        GENERAL
    }

    private ManagerGuideStepRegistry() {
    }

    static boolean isPharmacyRoute(@Nullable String rawCode) {
        return rawCode != null && "PHARMACY_ROUTE".equals(rawCode.trim());
    }

    static boolean isHospitalRoute(@Nullable String rawCode) {
        if (rawCode == null) {
            return false;
        }
        String code = rawCode.trim();
        return "HOSPITAL_ROUTE".equals(code)
                || "LEGACY_CORE_RECEPTION_PREPARATION".equals(code);
    }

    static PresentationType resolve(@Nullable String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim();
        switch (code) {
            case "MEETING_CONFIRMATION":
            case "HOSPITAL_ROUTE":
            case "LEGACY_CORE_PATIENT_CONTACT":
                return PresentationType.MEETING;
            case "RECEPTION_QUEUE":
            case "VITALS_CHECK":
            case "PAYMENT_EVIDENCE":
            case "LEGACY_CORE_RECEPTION_PREPARATION":
            case "LEGACY_CORE_RECEPTION":
            case "LEGACY_CORE_PAYMENT":
                return PresentationType.DOCUMENT;
            case "PRE_CONSULTATION":
            case "CONSULTATION_SUPPORT":
            case "CONSULTATION_SUMMARY":
            case "LEGACY_CORE_CONSULTATION":
                return PresentationType.TREATMENT;
            case "PHARMACY_ROUTE":
            case "PRESCRIPTION_DOCUMENTS":
            case "MEDICATION_CONFIRMATION":
            case "LEGACY_CORE_PHARMACY":
                return PresentationType.MEDICATION;
            case "CARE_COMPLETION":
            case "MANAGER_JOURNAL":
            case "LEGACY_CORE_RETURN_AND_CLOSE":
                return PresentationType.FINISH;
            default:
                return PresentationType.GENERAL;
        }
    }

    static PresentationType resolve(@Nullable GuideStep step) {
        if (step == null) {
            return PresentationType.GENERAL;
        }
        if (step.getCode() != null && !step.getCode().trim().isEmpty()) {
            return resolve(step.getCode());
        }
        switch (step.getOrder()) {
            case 1:
                return PresentationType.MEETING;
            case 2:
            case 3:
                return PresentationType.DOCUMENT;
            case 4:
            case 5:
                return PresentationType.TREATMENT;
            case 6:
                return PresentationType.MEDICATION;
            case 7:
                return PresentationType.FINISH;
            default:
                return PresentationType.GENERAL;
        }
    }
}
