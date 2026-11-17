package com.example.bodeul.debug;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.support.SupportScreenActivity;
import com.example.bodeul.ui.support.SupportSource;

/** 계측에서 주입한 저장소만 사용하며 실제 서버에는 연결하지 않는다. */
public final class SupportFigmaPreviewActivity extends SupportScreenActivity {
    static AuthRepository auth;
    static SupportSource source;
    @Override protected AuthRepository provideAuthRepository() {
        if (auth == null) throw new IllegalStateException("테스트 인증 저장소가 필요합니다.");
        return auth;
    }
    @Override protected SupportSource provideSupportSource() {
        if (source == null) throw new IllegalStateException("테스트 문의 저장소가 필요합니다.");
        return source;
    }
}
