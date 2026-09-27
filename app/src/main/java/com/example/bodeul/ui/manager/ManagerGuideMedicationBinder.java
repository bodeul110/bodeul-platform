package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Figma Step 11 위계를 기존 복약 메모·약국 진행 상태 계약에 연결한다. */
final class ManagerGuideMedicationBinder {
    private final View content;
    private final View toolbar;
    private final View defaultToolbar;
    private final LinearLayout scrollContent;
    private final View mode;
    private final View subtitle;
    private final View meetingOverview;
    private final View legacySummary;
    private final View legacyFocus;
    private final View actionsTitle;
    private final View actionsHelper;
    private final View legacyNotesCard;
    private final TextView existingMedication;
    private final TextView prescriptionStatus;
    private final TextView prescriptionState;
    private final TextView pharmacyState;
    private final TextView guidanceState;
    private final TextView progressText;
    private final ProgressBar progress;
    private final TextInputEditText pharmacyNote;
    private final TextInputEditText guidanceNote;
    private final MaterialButton savePharmacyNote;
    private final MaterialButton saveGuidanceNote;
    private final MaterialButton togglePrescription;
    private final MaterialButton togglePharmacy;
    private final MaterialButton toggleGuidance;
    private final MaterialButton advance;
    private final View footerHint;

    private String boundSessionId = "";
    private String boundPharmacyNote = "";
    private String boundGuidanceNote = "";

    ManagerGuideMedicationBinder(View root) {
        content = root.findViewById(R.id.managerGuideMedicationContent);
        toolbar = root.findViewById(R.id.guideMedicationToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        legacyNotesCard = root.findViewById(R.id.cardGuideNotesActions);
        existingMedication = root.findViewById(R.id.textGuideMedicationExistingSummary);
        prescriptionStatus = root.findViewById(R.id.textGuideMedicationPrescriptionStatus);
        prescriptionState = root.findViewById(R.id.textGuideMedicationPrescriptionState);
        pharmacyState = root.findViewById(R.id.textGuideMedicationPharmacyState);
        guidanceState = root.findViewById(R.id.textGuideMedicationGuidanceState);
        progressText = root.findViewById(R.id.textGuideMedicationProgress);
        progress = root.findViewById(R.id.progressGuideMedication);
        pharmacyNote = root.findViewById(R.id.inputGuideMedicationPharmacyNote);
        guidanceNote = root.findViewById(R.id.inputGuideMedicationGuidanceNote);
        savePharmacyNote = root.findViewById(R.id.buttonGuideMedicationSavePharmacyNote);
        saveGuidanceNote = root.findViewById(R.id.buttonGuideMedicationSaveGuidanceNote);
        togglePrescription = root.findViewById(
                R.id.buttonGuideMedicationTogglePrescription);
        togglePharmacy = root.findViewById(R.id.buttonGuideMedicationTogglePharmacy);
        toggleGuidance = root.findViewById(R.id.buttonGuideMedicationToggleGuidance);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
        footerHint = root.findViewById(R.id.textGuideMedicationFooterHint);
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight
    ) {
        boolean medicationStep = "MEDICATION_CONFIRMATION".equals(
                model.getCurrentStepCode());
        content.setVisibility(medicationStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(medicationStep ? View.VISIBLE : View.GONE);
        footerHint.setVisibility(medicationStep ? View.VISIBLE : View.GONE);
        if (!medicationStep) {
            boundSessionId = "";
            boundPharmacyNote = "";
            boundGuidanceNote = "";
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        meetingOverview.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        legacyNotesCard.setVisibility(View.GONE);

        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        String sessionId = session == null ? "" : normalized(session.getId());
        boolean newSession = !TextUtils.equals(boundSessionId, sessionId);
        bindMedicationComparison(dashboard, session);
        bindProgress(session);
        reconcileInput(pharmacyNote, model.getPharmacySummary(), boundPharmacyNote, newSession);
        reconcileInput(guidanceNote, model.getMedicationNote(), boundGuidanceNote, newSession);
        boundSessionId = sessionId;
        boundPharmacyNote = normalized(model.getPharmacySummary());
        boundGuidanceNote = normalized(model.getMedicationNote());

        setInputsEnabled(model.isInputsEnabled() && !mutationInFlight);
        advance.setIconResource(R.drawable.ic_figma_medication_check);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        footerHint.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
        restoreAdvanceIcon();
    }

    void setInputsEnabled(boolean enabled) {
        pharmacyNote.setEnabled(enabled);
        guidanceNote.setEnabled(enabled);
        savePharmacyNote.setEnabled(enabled);
        saveGuidanceNote.setEnabled(enabled);
        togglePrescription.setEnabled(enabled);
        togglePharmacy.setEnabled(enabled);
        toggleGuidance.setEnabled(enabled);
    }

    String pharmacyNote() {
        return valueOf(pharmacyNote);
    }

    String guidanceNote() {
        return valueOf(guidanceNote);
    }

    boolean hasUnsavedInput() {
        return !boundSessionId.isEmpty()
                && (!TextUtils.equals(valueOf(pharmacyNote), boundPharmacyNote)
                || !TextUtils.equals(valueOf(guidanceNote), boundGuidanceNote));
    }

    void discardUnsavedInput() {
        pharmacyNote.setText(boundPharmacyNote);
        guidanceNote.setText(boundGuidanceNote);
        pharmacyNote.clearFocus();
        guidanceNote.clearFocus();
    }

    private void bindMedicationComparison(
            ManagerDashboard dashboard,
            CompanionSession session
    ) {
        AppointmentRequest request = dashboard == null ? null : dashboard.getAppointmentRequest();
        String registeredMedication = request == null
                ? ""
                : normalized(request.getMedicationSummary());
        existingMedication.setText(registeredMedication.isEmpty()
                ? existingMedication.getContext().getString(
                        R.string.guide_medication_existing_empty)
                : registeredMedication);

        int prescriptionImageCount = session == null
                ? 0
                : session.getArtifacts(
                        CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE).size();
        prescriptionStatus.setText(prescriptionImageCount == 0
                ? prescriptionStatus.getContext().getString(
                        R.string.guide_medication_prescription_empty)
                : prescriptionStatus.getContext().getString(
                        R.string.guide_medication_prescription_count,
                        prescriptionImageCount));
    }

    private void bindProgress(CompanionSession session) {
        boolean prescriptionDone = session != null && session.isPrescriptionCollected();
        boolean pharmacyDone = session != null && session.isPharmacyCompleted();
        boolean guidanceDone = session != null && session.isMedicationGuidanceCompleted();
        bindState(prescriptionState, prescriptionDone);
        bindState(pharmacyState, pharmacyDone);
        bindState(guidanceState, guidanceDone);

        int completed = (prescriptionDone ? 1 : 0)
                + (pharmacyDone ? 1 : 0)
                + (guidanceDone ? 1 : 0);
        progress.setProgress(completed);
        progressText.setText(progressText.getContext().getString(
                R.string.guide_medication_progress_format, completed));
        bindProgressAction(
                togglePrescription,
                R.string.guide_medication_prescription_row,
                prescriptionDone);
        bindProgressAction(
                togglePharmacy,
                R.string.guide_medication_pharmacy_row,
                pharmacyDone);
        bindProgressAction(
                toggleGuidance,
                R.string.guide_medication_guidance_row,
                guidanceDone);
    }

    private void bindProgressAction(
            MaterialButton button,
            int rowLabelResource,
            boolean completed
    ) {
        int actionResource = completed
                ? R.string.guide_medication_action_undo
                : R.string.guide_medication_action_complete;
        String action = button.getContext().getString(actionResource);
        button.setText(action);
        button.setContentDescription(button.getContext().getString(
                R.string.guide_medication_action_accessibility,
                button.getContext().getString(rowLabelResource),
                action));
    }

    private void bindState(TextView view, boolean completed) {
        view.setText(completed
                ? R.string.guide_medication_status_done
                : R.string.guide_medication_status_pending);
        view.setTextColor(ContextCompat.getColor(
                view.getContext(),
                completed ? R.color.figma_mvp_primary : R.color.figma_mvp_text_secondary));
    }

    private void reconcileInput(
            TextInputEditText input,
            String serverValue,
            String previouslyBoundValue,
            boolean newSession
    ) {
        String normalizedServerValue = normalized(serverValue);
        String currentValue = input.getText() == null ? "" : input.getText().toString();
        if (newSession || TextUtils.equals(currentValue, previouslyBoundValue)) {
            if (!TextUtils.equals(currentValue, normalizedServerValue)) {
                input.setText(normalizedServerValue);
            }
        }
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void restoreAdvanceIcon() {
        advance.setIconResource(R.drawable.ic_figma_guide_arrow_vector);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
