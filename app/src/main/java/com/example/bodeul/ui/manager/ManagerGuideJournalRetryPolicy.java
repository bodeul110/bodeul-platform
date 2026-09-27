package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.CompanionSession;

/** 서버의 기존 리포트 생성 상태를 Step 13 재제출 안내로 변환한다. */
final class ManagerGuideJournalRetryPolicy {
    private ManagerGuideJournalRetryPolicy() {
    }

    static boolean shouldShowRetry(@Nullable CompanionSession session) {
        if (session == null) {
            return false;
        }
        String status = normalized(session.getReportGenerationStatus());
        return "FAILED".equals(status)
                || "PENDING".equals(status)
                || !normalized(session.getReportGenerationLastError()).isEmpty();
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }
}
