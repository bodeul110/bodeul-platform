package com.example.bodeul.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/**
 * 로그인 계정 정보를 내 정보 화면용 문구로 변환한다.
 */
public final class ClientProfileCoordinator {
    private final Context context;

    public ClientProfileCoordinator(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public ClientProfileScreenModel createScreenModel(@NonNull User user) {
        String name = displayValue(user.getName());
        boolean complete = hasText(user.getName()) && hasText(user.getEmail()) && hasText(user.getPhone());
        return new ClientProfileScreenModel(
                context.getString(R.string.client_profile_title),
                context.getString(R.string.client_profile_subtitle),
                context.getString(R.string.client_profile_hero_title, name),
                context.getString(complete
                        ? R.string.client_profile_hero_body_complete
                        : R.string.client_profile_hero_body_incomplete),
                context.getString(user.getRole() == UserRole.MANAGER ? R.string.login_role_manager
                        : user.getRole() == UserRole.GUARDIAN ? R.string.login_role_guardian : R.string.login_role_patient),
                name,
                displayValue(user.getEmail()),
                displayValue(user.getPhone())
        );
    }

    private String displayValue(String value) {
        return hasText(value) ? value.trim() : context.getString(R.string.client_profile_value_missing);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
