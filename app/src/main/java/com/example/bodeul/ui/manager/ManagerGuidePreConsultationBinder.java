package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

/** 문진 준비 단계의 화면·로컬 체크리스트를 서버의 단일 확인 상태와 연결한다. */
final class ManagerGuidePreConsultationBinder {
    private final View content;
    private final View toolbar;
    private final View defaultToolbar;
    private final View bottomNav;
    private final View legacySummary;
    private final View legacyFocus;
    private final View actionsTitle;
    private final View actionsHelper;
    private final View fieldNoteCard;
    private final View legacyConfirmation;
    private final LinearLayout scrollContent;
    private final TextView mode;
    private final TextView subtitle;
    private final TextView guardian;
    private final TextView patient;
    private final TextView request;
    private final TextView medication;
    private final TextView requestHint;
    private final TextView footerHint;
    private final MaterialCheckBox checkMedication;
    private final MaterialCheckBox checkRequest;
    private final MaterialCheckBox checkDocuments;
    private final AppCompatButton footerButton;
    private final MaterialButton legacyAdvanceButton;
    private final MaterialButton toggleFieldNote;
    private String boundSessionId = "";
    private boolean wasConfirmed;
    private boolean confirmed;
    private boolean inputsEnabled;
    private boolean mutationInFlight;
    private boolean fieldNoteExpanded;

    ManagerGuidePreConsultationBinder(View root) {
        content = root.findViewById(R.id.managerGuidePreConsultationContent);
        toolbar = root.findViewById(R.id.guidePreConsultationToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        bottomNav = root.findViewById(R.id.managerGuideBottomNav);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        fieldNoteCard = root.findViewById(R.id.cardGuideNotesActions);
        legacyConfirmation = root.findViewById(R.id.groupGuidePreConsultationConfirmation);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        guardian = root.findViewById(R.id.textGuidePreConsultationGuardian);
        patient = root.findViewById(R.id.textGuidePreConsultationPatient);
        request = root.findViewById(R.id.textGuidePreConsultationRequest);
        medication = root.findViewById(R.id.textGuidePreConsultationMedication);
        requestHint = root.findViewById(R.id.textGuidePreConsultationRequestHint);
        footerHint = root.findViewById(R.id.textGuidePreConsultationFooterHint);
        checkMedication = root.findViewById(R.id.checkGuidePreConsultationMedication);
        checkRequest = root.findViewById(R.id.checkGuidePreConsultationRequest);
        checkDocuments = root.findViewById(R.id.checkGuidePreConsultationDocuments);
        footerButton = root.findViewById(R.id.buttonGuidePreConsultationComplete);
        legacyAdvanceButton = root.findViewById(R.id.buttonAdvanceGuide);
        toggleFieldNote = root.findViewById(R.id.buttonGuidePreConsultationFieldNote);
        checkMedication.setOnCheckedChangeListener((button, checked) -> updateFooterEnabled());
        checkRequest.setOnCheckedChangeListener((button, checked) -> updateFooterEnabled());
        checkDocuments.setOnCheckedChangeListener((button, checked) -> updateFooterEnabled());
        toggleFieldNote.setOnClickListener(view -> {
            fieldNoteExpanded = !fieldNoteExpanded;
            bindFieldNote();
        });
    }

    void bind(ManagerGuideScreenModel model, ManagerDashboard dashboard, boolean mutationInFlight) {
        boolean preConsultationStep = "PRE_CONSULTATION".equals(
                model.getPresentationStepCode());
        content.setVisibility(preConsultationStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(preConsultationStep ? View.VISIBLE : View.GONE);
        bottomNav.setVisibility(preConsultationStep ? View.GONE : View.VISIBLE);
        footerHint.setVisibility(preConsultationStep ? View.VISIBLE : View.GONE);
        footerButton.setVisibility(preConsultationStep ? View.VISIBLE : View.GONE);
        legacyAdvanceButton.setVisibility(preConsultationStep ? View.GONE : View.VISIBLE);
        if (!preConsultationStep) {
            boundSessionId = "";
            fieldNoteExpanded = false;
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        legacyConfirmation.setVisibility(View.GONE);

        AppointmentRequest appointment = dashboard == null ? null : dashboard.getAppointmentRequest();
        String guardianName = dashboard != null && dashboard.getGuardian() != null
                ? dashboard.getGuardian().getName() : "";
        if (TextUtils.isEmpty(guardianName) && appointment != null) {
            guardianName = appointment.getGuardianName();
        }
        guardian.setText(TextUtils.isEmpty(guardianName)
                ? guardian.getContext().getString(R.string.guide_pre_consultation_guardian_unknown)
                : guardian.getContext().getString(
                        R.string.guide_pre_consultation_guardian_format, guardianName));
        String patientName = dashboard != null && dashboard.getPatient() != null
                ? dashboard.getPatient().getName() : "";
        if (TextUtils.isEmpty(patientName) && appointment != null) {
            patientName = appointment.getPatientName();
        }
        patient.setText(TextUtils.isEmpty(patientName)
                ? patient.getContext().getString(R.string.guide_pre_consultation_patient_unknown)
                : patient.getContext().getString(
                        R.string.guide_pre_consultation_patient_format, patientName));
        String specialNotes = appointment == null ? "" : appointment.getSpecialNotes();
        request.setText(TextUtils.isEmpty(specialNotes)
                ? request.getContext().getString(R.string.guide_pre_consultation_request_empty)
                : request.getContext().getString(
                        R.string.guide_pre_consultation_request_quote, specialNotes));
        requestHint.setText(R.string.guide_pre_consultation_request_check_hint);
        String medicationSummary = appointment == null ? "" : appointment.getMedicationSummary();
        medication.setText(TextUtils.isEmpty(medicationSummary)
                ? medication.getContext().getString(R.string.guide_pre_consultation_medication_empty)
                : medicationSummary);

        String sessionId = dashboard == null || dashboard.getSession() == null
                ? "" : dashboard.getSession().getId();
        boolean serverConfirmed = model.isPreConsultationConfirmed();
        if (!TextUtils.equals(boundSessionId, sessionId) || (wasConfirmed && !serverConfirmed)) {
            setAllChecked(serverConfirmed);
            fieldNoteExpanded = false;
        } else if (serverConfirmed) {
            setAllChecked(true);
        }
        boundSessionId = sessionId;
        wasConfirmed = serverConfirmed;
        confirmed = serverConfirmed;
        inputsEnabled = model.isInputsEnabled();
        this.mutationInFlight = mutationInFlight;
        boolean checkboxesEnabled = inputsEnabled && !mutationInFlight && !confirmed;
        checkMedication.setEnabled(checkboxesEnabled);
        checkRequest.setEnabled(checkboxesEnabled);
        checkDocuments.setEnabled(checkboxesEnabled);
        toggleFieldNote.setEnabled(inputsEnabled && !mutationInFlight);
        bindFieldNote();

        footerButton.setText(confirmed
                ? model.getAdvanceButtonLabel()
                : footerButton.getContext().getString(R.string.guide_pre_consultation_review_complete));
        footerHint.setText(confirmed
                ? R.string.guide_pre_consultation_review_saved
                : R.string.guide_pre_consultation_review_hint);
        updateFooterEnabled(model.isAdvanceEnabled());
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        bottomNav.setVisibility(View.VISIBLE);
        footerHint.setVisibility(View.GONE);
        footerButton.setVisibility(View.GONE);
        legacyAdvanceButton.setVisibility(View.VISIBLE);
        boundSessionId = "";
        fieldNoteExpanded = false;
    }

    boolean canConfirm() {
        return ManagerGuidePreConsultationChecklistPolicy.canConfirm(
                confirmed,
                inputsEnabled,
                mutationInFlight,
                checkMedication.isChecked(),
                checkRequest.isChecked(),
                checkDocuments.isChecked());
    }

    boolean isConfirmed() {
        return confirmed;
    }

    private void bindFieldNote() {
        fieldNoteCard.setVisibility(fieldNoteExpanded ? View.VISIBLE : View.GONE);
        toggleFieldNote.setText(fieldNoteExpanded
                ? R.string.guide_pre_consultation_field_note_close
                : R.string.guide_pre_consultation_field_note_open);
    }

    private void setAllChecked(boolean checked) {
        checkMedication.setChecked(checked);
        checkRequest.setChecked(checked);
        checkDocuments.setChecked(checked);
    }

    private void updateFooterEnabled() {
        updateFooterEnabled(confirmed && footerButton.isEnabled());
    }

    private void updateFooterEnabled(boolean serverAdvanceEnabled) {
        footerButton.setEnabled(confirmed
                ? serverAdvanceEnabled && !mutationInFlight
                : canConfirm());
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
