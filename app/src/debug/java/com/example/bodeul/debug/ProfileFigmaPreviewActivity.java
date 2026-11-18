package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.ui.profile.ClientProfileActivity;
import com.example.bodeul.ui.support.FaqActivity;

public final class ProfileFigmaPreviewActivity extends ClientProfileActivity {
    @Override protected AuthRepository provideAuthRepository() { return ProfilePreviewDependencies.requireAuth(); }
    @Override protected boolean isManagerPage() { return getIntent().getBooleanExtra("manager", false); }
    @Override public void startActivity(Intent intent) {
        ProfilePreviewDependencies.lastNavigation = intent;
        if (getIntent().getBooleanExtra("openFaq", false) && intent.getComponent() != null
                && FaqActivity.class.getName().equals(intent.getComponent().getClassName())) {
            super.startActivity(intent);
        }
    }
}
