package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.profile.AccountSettingsActivity;

public final class AccountSettingsPreviewActivity extends AccountSettingsActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override public void startActivity(Intent intent) { ProfilePreviewDependencies.lastNavigation = intent; }
}
