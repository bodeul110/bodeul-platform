package com.example.bodeul.ui.profile;

import com.example.bodeul.domain.model.UserRole;

/** 진입 화면이나 버튼 선택이 아닌 조회한 계정의 역할로 표시 범위를 결정한다. */
public final class ProfileAccessPolicy {
    private ProfileAccessPolicy() { }
    public static boolean canOpenHome(UserRole role, boolean managerPage) {
        return managerPage ? role == UserRole.MANAGER : role == UserRole.PATIENT || role == UserRole.GUARDIAN;
    }
    public static boolean canOpenSettings(UserRole role) {
        return canOpenHome(role, false) || canOpenHome(role, true);
    }
}
