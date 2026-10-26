package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.ui.report.GuardianReportActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 완료된 로컬 동행 데이터로 보호자 최종 리포트를 보여주는 debug 미리보기다. */
public final class GuardianReportFigmaPreviewActivity extends GuardianReportActivity {
    @Nullable
    private FigmaPreviewDependencies dependencies;

    static Intent createIntent(Context context) {
        return new Intent(context, GuardianReportFigmaPreviewActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreviewBanner();
        BottomNavigationView bottomNavigation = findViewById(R.id.clientBottomNavigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            showLocalOnlyMessage();
            return false;
        });
    }

    @Override
    protected AuthRepository provideAuthRepository() {
        return dependencies().authRepository;
    }

    @Override
    protected GuardianReportRepository provideGuardianReportRepository() {
        return dependencies().guardianReportRepository;
    }

    @Override
    public void onOpenRequestDetail(String requestId) {
        showLocalOnlyMessage();
    }

    private FigmaPreviewDependencies dependencies() {
        if (dependencies == null) {
            dependencies = FigmaPreviewDependencies.forGuardianReport(this);
        }
        return dependencies;
    }

    private void addPreviewBanner() {
        LinearLayout content = findViewById(R.id.guardianReportContentContainer);
        TextView banner = new TextView(this);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(12), dp(8), dp(12), dp(8));
        banner.setText(R.string.debug_manager_guide_preview_banner);
        banner.setTextColor(ContextCompat.getColor(this, R.color.white));
        banner.setTextSize(12f);
        banner.setBackgroundColor(ContextCompat.getColor(this, R.color.bodeul_primary_dark));
        content.addView(
                banner,
                0,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
        );
    }

    private void showLocalOnlyMessage() {
        Toast.makeText(
                this,
                R.string.debug_figma_preview_local_only,
                Toast.LENGTH_SHORT
        ).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
