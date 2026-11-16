package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.profile.ClientProfileActivity;

public final class ProfileFigmaPreviewActivity extends ClientProfileActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override protected boolean isManagerPage() { return getIntent().getBooleanExtra("manager", false); }
    @Override public void startActivity(Intent intent) { ProfilePreviewDependencies.lastNavigation = intent; }
}
