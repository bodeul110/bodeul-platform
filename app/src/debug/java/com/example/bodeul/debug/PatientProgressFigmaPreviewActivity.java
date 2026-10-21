package com.example.bodeul.debug;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.ui.booking.BookingStatusActivity;
import com.google.android.material.button.MaterialButton;

/** 실제 환자 진행현황 화면을 로컬 1~7단계와 완료 상태로 여는 debug 미리보기다. */
public final class PatientProgressFigmaPreviewActivity extends BookingStatusActivity {
    static final String EXTRA_PREVIEW_STATE = "patientProgressPreviewState";
    static final int FIRST_STATE = 1;
    static final int COMPLETED_STATE = 8;

    @Nullable
    private FigmaPreviewDependencies dependencies;

    static Intent createIntent(Context context) {
        return createIntent(context, FIRST_STATE);
    }

    static Intent createIntent(Context context, int previewState) {
        Intent intent = BookingStatusActivity.createIntent(context, "request-1");
        intent.setClass(context, PatientProgressFigmaPreviewActivity.class);
        intent.putExtra(EXTRA_PREVIEW_STATE, clampState(previewState));
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreviewControls();
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

    private FigmaPreviewDependencies dependencies() {
        if (dependencies == null) {
            int state = previewState();
            dependencies = FigmaPreviewDependencies.forPatientProgress(
                    this,
                    Math.min(state, 7),
                    state == COMPLETED_STATE
            );
        }
        return dependencies;
    }

    private int previewState() {
        return clampState(getIntent().getIntExtra(EXTRA_PREVIEW_STATE, FIRST_STATE));
    }

    private void addPreviewControls() {
        ViewGroup screenColumn = findViewById(R.id.bookingStatusContentContainer);
        if (screenColumn == null) {
            return;
        }
        View controls = getLayoutInflater().inflate(
                R.layout.include_patient_progress_preview_controls,
                screenColumn,
                false
        );
        screenColumn.addView(controls, 0);

        int state = previewState();
        String[] stateLabels = getResources().getStringArray(
                R.array.debug_patient_progress_preview_states);
        TextView stateView = controls.findViewById(R.id.textPatientProgressPreviewState);
        stateView.setText(stateLabels[state - 1]);

        MaterialButton previousButton = controls.findViewById(
                R.id.buttonPatientProgressPreviewPrevious);
        MaterialButton nextButton = controls.findViewById(
                R.id.buttonPatientProgressPreviewNext);
        previousButton.setEnabled(state > FIRST_STATE);
        nextButton.setEnabled(state < COMPLETED_STATE);
        previousButton.setOnClickListener(view -> showState(state - 1));
        nextButton.setOnClickListener(view -> showState(state + 1));
    }

    private void showState(int state) {
        getIntent().putExtra(EXTRA_PREVIEW_STATE, clampState(state));
        dependencies = null;
        recreate();
    }

    private void disableExternalDestinations() {
        int[] actionIds = {
                R.id.buttonBookingStatusPrimary,
                R.id.buttonBookingStatusSecondary,
                R.id.buttonBookingStatusBankTransferPayment,
                R.id.buttonBookingStatusGuardianSharingConsent
        };
        for (int actionId : actionIds) {
            View action = findViewById(actionId);
            if (action == null) {
                continue;
            }
            action.setOnClickListener(view -> Toast.makeText(
                    this,
                    R.string.debug_patient_progress_preview_local_only,
                    Toast.LENGTH_SHORT
            ).show());
        }
    }

    private static int clampState(int state) {
        return Math.max(FIRST_STATE, Math.min(state, COMPLETED_STATE));
    }
}
