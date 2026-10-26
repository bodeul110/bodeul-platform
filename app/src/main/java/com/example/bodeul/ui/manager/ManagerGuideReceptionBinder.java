package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

/** 접수 단계 전용 위계와 기존 가이드 위계의 전환을 담당한다. */
final class ManagerGuideReceptionBinder {
    private final View receptionContent;
    private final View receptionToolbar;
    private final View defaultToolbar;
    private final LinearLayout scrollContent;
    private final View legacySummary;
    private final View legacyFocus;
    private final View actionsTitle;
    private final View actionsHelper;
    private final View fieldNoteCard;
    private final TextView mode;
    private final TextView subtitle;
    private final TextView receptionToolbarTitle;
    private final TextView patient;
    private final TextView hospital;
    private final TextView lastShare;
    private final EditText queue;
    private final EditText waitMinutes;
    private final AppCompatButton share;
    private final MaterialButton toggleFieldNote;
    private boolean fieldNoteExpanded;
    private String boundSessionId = "";

    ManagerGuideReceptionBinder(View root) {
        receptionContent = root.findViewById(R.id.managerGuideReceptionContent);
        receptionToolbar = root.findViewById(R.id.guideReceptionToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        fieldNoteCard = root.findViewById(R.id.cardGuideNotesActions);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        receptionToolbarTitle = root.findViewById(R.id.textGuideReceptionToolbarTitle);
        patient = root.findViewById(R.id.textGuideReceptionPatient);
        hospital = root.findViewById(R.id.textGuideReceptionHospital);
        lastShare = root.findViewById(R.id.textGuideReceptionLastShare);
        queue = root.findViewById(R.id.inputGuideReceptionQueue);
        waitMinutes = root.findViewById(R.id.inputGuideReceptionWaitMinutes);
        share = root.findViewById(R.id.buttonGuideReceptionShare);
        toggleFieldNote = root.findViewById(R.id.buttonGuideReceptionFieldNote);
        toggleFieldNote.setOnClickListener(view -> {
            fieldNoteExpanded = !fieldNoteExpanded;
            bindFieldNoteToggle();
        });
    }

    void bind(ManagerGuideScreenModel model, ManagerDashboard dashboard, boolean mutationInFlight) {
        boolean receptionStep = "RECEPTION_QUEUE".equals(
                model.getPresentationStepCode());
        receptionContent.setVisibility(receptionStep ? View.VISIBLE : View.GONE);
        receptionToolbar.setVisibility(receptionStep ? View.VISIBLE : View.GONE);
        defaultToolbar.setVisibility(receptionStep ? View.GONE : View.VISIBLE);
        setScrollTopPadding(receptionStep ? 0 : 24);
        if (!receptionStep) {
            fieldNoteExpanded = false;
            EnvironmentModeBadgeHelper.bind(mode, model.getModeLabel());
            return;
        }

        String sessionId = dashboard == null || dashboard.getSession() == null
                ? "" : dashboard.getSession().getId();
        if (ManagerGuideReceptionDraftPolicy.shouldClear(boundSessionId, sessionId)) {
            queue.setText(null);
            waitMinutes.setText(null);
            fieldNoteExpanded = false;
        }
        boundSessionId = sessionId;

        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        receptionToolbarTitle.setText(model.getTitle());
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        bindFieldNoteToggle();

        String patientName = dashboard == null || dashboard.getPatient() == null
                ? "" : dashboard.getPatient().getName();
        if (TextUtils.isEmpty(patientName) && dashboard != null
                && dashboard.getAppointmentRequest() != null) {
            patientName = dashboard.getAppointmentRequest().getPatientName();
        }
        patient.setText(TextUtils.isEmpty(patientName)
                ? R.string.guide_reception_patient_unknown
                : R.string.guide_reception_patient_label);
        if (!TextUtils.isEmpty(patientName)) {
            patient.setText(patient.getContext().getString(
                    R.string.guide_reception_patient_name_format, patientName));
        }

        AppointmentRequest request = dashboard == null ? null : dashboard.getAppointmentRequest();
        String hospitalName = request == null ? "" : request.getHospitalName();
        String departmentName = request == null ? "" : request.getDepartmentName();
        if (TextUtils.isEmpty(hospitalName) && TextUtils.isEmpty(departmentName)) {
            hospital.setText(R.string.guide_reception_hospital_unknown);
        } else if (TextUtils.isEmpty(departmentName)) {
            hospital.setText(hospitalName);
        } else if (TextUtils.isEmpty(hospitalName)) {
            hospital.setText(departmentName);
        } else {
            hospital.setText(hospital.getContext().getString(
                    R.string.guide_reception_hospital_format, hospitalName, departmentName));
        }

        String guardianUpdate = model.getGuardianUpdate();
        lastShare.setVisibility(TextUtils.isEmpty(guardianUpdate) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(guardianUpdate)) {
            lastShare.setText(lastShare.getContext().getString(
                    R.string.guide_reception_last_share_format, guardianUpdate));
        }

        boolean inputsEnabled = model.isInputsEnabled() && !mutationInFlight;
        queue.setEnabled(inputsEnabled);
        waitMinutes.setEnabled(inputsEnabled);
        share.setEnabled(inputsEnabled);
    }

    void hideForState() {
        receptionContent.setVisibility(View.GONE);
        receptionToolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
    }

    void setShareEnabled(boolean enabled) {
        share.setEnabled(enabled);
    }

    private void bindFieldNoteToggle() {
        fieldNoteCard.setVisibility(fieldNoteExpanded ? View.VISIBLE : View.GONE);
        toggleFieldNote.setText(fieldNoteExpanded
                ? R.string.guide_reception_field_note_close
                : R.string.guide_reception_field_note_open);
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }

    /** 유효한 입력에 한해 기존 보호자 공유 필드에 저장할 문구를 만든다. */
    String buildGuardianUpdate() {
        String queueValue = queue.getText().toString().trim();
        if (!ManagerGuideReceptionInputPolicy.isValidQueue(queueValue)) {
            queue.setError(queue.getContext().getString(R.string.guide_reception_queue_required));
            queue.requestFocus();
            return null;
        }
        int minutes = ManagerGuideReceptionInputPolicy.parseWaitMinutes(
                waitMinutes.getText().toString());
        if (minutes == 0) {
            waitMinutes.setError(waitMinutes.getContext().getString(
                    R.string.guide_reception_wait_required));
            waitMinutes.requestFocus();
            return null;
        }
        return share.getContext().getString(
                R.string.guide_reception_share_format, queueValue, minutes);
    }
}
