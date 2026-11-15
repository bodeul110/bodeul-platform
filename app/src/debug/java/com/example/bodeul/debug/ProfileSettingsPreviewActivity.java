package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.profile.ProfileSettingsActivity;

public final class ProfileSettingsPreviewActivity extends ProfileSettingsActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override public void startActivity(Intent intent) { ProfilePreviewDependencies.lastNavigation = intent; }
}
