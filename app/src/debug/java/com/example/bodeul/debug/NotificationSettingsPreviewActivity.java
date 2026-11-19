package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.NotificationSettingsRepository;
import com.example.bodeul.ui.profile.NotificationSettingsActivity;

public final class NotificationSettingsPreviewActivity extends NotificationSettingsActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override protected NotificationSettingsRepository provideNotificationSettings() {
        if (ProfilePreviewDependencies.notifications == null) throw new IllegalStateException("테스트 알림 저장소가 필요합니다.");
        return ProfilePreviewDependencies.notifications;
    }
    @Override public void startActivity(Intent intent) {
        if (ProfilePreviewDependencies.rejectNotificationSettings) throw new android.content.ActivityNotFoundException();
        ProfilePreviewDependencies.lastNavigation = intent;
    }
}
