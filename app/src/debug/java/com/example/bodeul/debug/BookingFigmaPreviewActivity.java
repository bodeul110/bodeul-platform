package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.ui.booking.BookingActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 서버와 외부 화면을 열지 않는 환자 예약 메인 피그마 미리보기다. */
public final class BookingFigmaPreviewActivity extends BookingActivity {
    @Nullable
    private FigmaPreviewDependencies dependencies;

    static Intent createIntent(Context context) {
        return new Intent(context, BookingFigmaPreviewActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreviewBanner();
        disableExternalDestinations();
    }

    @Override
    protected AuthRepository provideAuthRepository() {
        return dependencies().authRepository;
    }

    @Override
    protected BookingRepository provideBookingRepository() {
        return dependencies().bookingRepository;
    }

    @Override
    protected int bookingTopBarExtraHeightPx() {
        return dp(36);
    }

    @Override
    protected boolean shouldStartHospitalMapPreview() {
        return false;
    }

    @Override
    protected void openRequestDetail(AppointmentRequest request) {
        showLocalOnlyMessage();
    }

    @Override
    protected void openHospitalSelector() {
        showLocalOnlyMessage();
    }

    @Override
    protected void openHealthProfile() {
        showLocalOnlyMessage();
    }

    @Override
    protected void openLocationSelector() {
        showLocalOnlyMessage();
    }

    private FigmaPreviewDependencies dependencies() {
        if (dependencies == null) {
            dependencies = FigmaPreviewDependencies.forBooking(this);
        }
        return dependencies;
    }

    private void addPreviewBanner() {
        LinearLayout topBar = findViewById(R.id.layoutBookingTopBar);
        TextView banner = createBanner();
        topBar.addView(
                banner,
                0,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
        );
    }

    private void disableExternalDestinations() {
        View appointment = findViewById(R.id.layoutBookingAppointmentAt);
        appointment.setOnClickListener(view -> showLocalOnlyMessage());
        View appointmentSummary = findViewById(R.id.buttonBookingAppointmentSummary);
        appointmentSummary.setOnClickListener(view -> showLocalOnlyMessage());
        View submit = findViewById(R.id.buttonSubmitBooking);
        submit.setOnClickListener(view -> showLocalOnlyMessage());
        BottomNavigationView bottomNavigation = findViewById(R.id.clientBottomNavigation);
        bottomNavigation.setOnItemSelectedListener(item -> {
            showLocalOnlyMessage();
            return false;
        });
    }

    private TextView createBanner() {
        TextView banner = new TextView(this);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(dp(12), dp(8), dp(12), dp(8));
        banner.setText(R.string.debug_manager_guide_preview_banner);
        banner.setTextColor(ContextCompat.getColor(this, R.color.white));
        banner.setTextSize(12f);
        banner.setBackgroundColor(ContextCompat.getColor(this, R.color.bodeul_primary_dark));
        return banner;
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
