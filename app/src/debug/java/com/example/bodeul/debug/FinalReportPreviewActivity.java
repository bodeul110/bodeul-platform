package com.example.bodeul.debug;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.FinalReportRepository;
import com.example.bodeul.ui.report.FinalReportActivity;

/** 테스트가 주입한 메모리 저장소만 사용하며 실제 인증·서버에는 연결하지 않는다. */
public final class FinalReportPreviewActivity extends FinalReportActivity {
    static AuthRepository testAuth;
    static FinalReportRepository testReports;

    @Override protected AuthRepository provideAuthRepository() {
        if (testAuth == null) throw new IllegalStateException("테스트 인증 저장소가 필요합니다.");
        return testAuth;
    }

    @Override protected FinalReportRepository provideFinalReportRepository() {
        if (testReports == null) throw new IllegalStateException("테스트 리포트 저장소가 필요합니다.");
        return testReports;
    }
}
