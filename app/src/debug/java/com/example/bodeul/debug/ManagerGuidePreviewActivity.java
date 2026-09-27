package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.ui.manager.ManagerGuideActivity;

/** 운영 서버 저장소와 realtime을 사용하지 않는 debug 가이드 미리보기다. */
public final class ManagerGuidePreviewActivity extends ManagerGuideActivity {
    static final String EXTRA_STEP_CODE =
            "com.example.bodeul.debug.extra.MANAGER_GUIDE_STEP_CODE";

    private static final CompanionRealtimeSubscriber NO_OP_REALTIME =
            new CompanionRealtimeSubscriber() {
                @Override
                public void subscribe(String companionSessionId, Runnable changedCallback) {
                    // 미리보기는 외부 realtime 채널을 열지 않는다.
                }

                @Override
                public void stop() {
                    // 열린 채널이 없다.
                }
            };

    @Nullable
    private ManagerGuidePreviewDependencies dependencies;

    static Intent createIntent(Context context, String stepCode) {
        return new Intent(context, ManagerGuidePreviewActivity.class)
                .putExtra(EXTRA_STEP_CODE, stepCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreviewBanner();
        disableServerBackedDestinations();
    }

    @Override
    protected AuthRepository provideAuthRepository() {
        return dependencies().authRepository;
    }

    @Override
    protected ManagerRepository provideManagerRepository() {
        return dependencies().managerRepository;
    }

    @Override
    protected CompanionRealtimeSubscriber provideRealtimeSubscriber() {
        return NO_OP_REALTIME;
    }

    @Override
    protected boolean isLegacyManagerLocationEnabled() {
        return false;
    }

    @Override
    protected boolean isPlaceSearchEnabled() {
        return false;
    }

    @Override
    protected boolean isGuidePreviewMode() {
        return true;
    }

    @Override
    protected void openManagerHome() {
        finish();
    }

    @Override
    protected void openCompanionChat() {
        showLocalOnlyMessage();
    }

    private ManagerGuidePreviewDependencies dependencies() {
        if (dependencies == null) {
            dependencies = ManagerGuidePreviewDependencies.create(
                    this,
                    selectedStep().getCode());
        }
        return dependencies;
    }

    private GuideStep selectedStep() {
        return ManagerGuidePreviewCatalog.resolve(
                getIntent().getStringExtra(EXTRA_STEP_CODE));
    }

    private void addPreviewBanner() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content.getChildCount() == 0 || !(content.getChildAt(0) instanceof LinearLayout)) {
            return;
        }
        TextView banner = new TextView(this);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(12), dp(8), dp(12), dp(8));
        banner.setText(R.string.debug_manager_guide_preview_banner);
        banner.setTextColor(ContextCompat.getColor(this, R.color.white));
        banner.setTextSize(12f);
        banner.setBackgroundColor(ContextCompat.getColor(this, R.color.bodeul_primary_dark));
        LinearLayout root = (LinearLayout) content.getChildAt(0);
        root.addView(
                banner,
                0,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void disableServerBackedDestinations() {
        findViewById(R.id.navGuideHistory).setOnClickListener(view -> showLocalOnlyMessage());
        findViewById(R.id.navGuideProfile).setOnClickListener(view -> showLocalOnlyMessage());
        findViewById(R.id.buttonGuideOpenChat).setOnClickListener(view -> showLocalOnlyMessage());
        findViewById(R.id.buttonGuideMeetingOpenChat).setOnClickListener(
                view -> showLocalOnlyMessage());
    }

    private void showLocalOnlyMessage() {
        Toast.makeText(
                this,
                R.string.debug_manager_guide_preview_local_only,
                Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
