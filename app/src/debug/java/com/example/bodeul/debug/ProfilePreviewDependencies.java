package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;

/** 계측 테스트에서만 공급하며 실제 계정이나 서버에 연결하지 않는다. */
final class ProfilePreviewDependencies {
    static AuthRepository auth;
    static Intent lastNavigation;
    static AuthRepository requireAuth() {
        if (auth == null) throw new IllegalStateException("테스트 인증 저장소가 필요합니다.");
        return auth;
    }
    private ProfilePreviewDependencies() { }
}
