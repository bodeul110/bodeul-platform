package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.profile.ProfileSettingsActivity;

public final class ProfileSettingsPreviewActivity extends ProfileSettingsActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override public void startActivity(Intent intent) {
        ProfilePreviewDependencies.lastNavigation = intent;
        if (!getIntent().getBooleanExtra("openDetails", false) || intent.getComponent() == null) return;
        String target = intent.getComponent().getClassName();
        if (target.equals(com.example.bodeul.ui.profile.AccountSettingsActivity.class.getName())) {
            super.startActivity(new Intent(this, AccountSettingsPreviewActivity.class));
        } else if (target.equals(com.example.bodeul.ui.profile.NotificationSettingsActivity.class.getName())) {
            super.startActivity(new Intent(this, NotificationSettingsPreviewActivity.class));
        }
    }
}
