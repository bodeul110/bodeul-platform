package com.example.bodeul.data;

import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.User;

/** 최종 리포트는 인증된 사용자의 기존 조회 경로 안에서만 가져온다. */
public interface FinalReportRepository {
    void getReport(User user, String requestId, RepositoryCallback<GuardianReportEntry> callback);
}
