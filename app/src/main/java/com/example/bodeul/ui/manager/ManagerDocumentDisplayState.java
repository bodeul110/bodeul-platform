package com.example.bodeul.ui.manager;

import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;

/** 파일 초안과 검토 결과를 구분하며, 유효하지 않은 파일을 인증 완료로 표시하지 않는다. */
public enum ManagerDocumentDisplayState {
    MISSING, DRAFT, REPLACEMENT, PENDING, APPROVED, REJECTED;

    public static ManagerDocumentDisplayState from(ManagerHomeProfile profile) {
        if (!ManagerDocumentRegistrationCoordinator.hasRequiredFiles(profile)) {
            return ManagerDocumentRegistrationCoordinator.requiresQualificationReplacement(profile)
                    ? REPLACEMENT : MISSING;
        }
        ManagerDocumentStatus status = profile.getDocumentStatus();
        if (status == ManagerDocumentStatus.APPROVED) return APPROVED;
        if (status == ManagerDocumentStatus.PENDING_REVIEW) return PENDING;
        if (status == ManagerDocumentStatus.REJECTED) return REJECTED;
        return DRAFT;
    }
}
