package com.example.bodeul.ui.manager;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Figma Step 13의 완료 화면을 기존 300자 일지·리포트 제출 및 재시도 계약에 연결한다. */
final class ManagerGuideJournalBinder {
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
    private final View legacyMap;
    private final View legacyLocationCard;
    private final View legacyNotesCard;
    private final View reportCard;
    private final TextView patient;
    private final TextView statusTitle;
    private final TextView statusBody;
    private final TextInputEditText summary;
    private final TextInputEditText treatment;
    private final TextInputEditText medicationName;
    private final TextInputEditText medicationChange;
    private final TextInputEditText medicationSchedule;
    private final TextInputEditText medicationComparisonNote;
    private final TextInputEditText nextVisit;
    private final MaterialRadioButton comparisonMatched;
    private final MaterialRadioButton comparisonChanged;
    private final MaterialRadioButton comparisonRecheck;
    private final MaterialButton advance;

    ManagerGuideJournalBinder(View root) {
        content = root.findViewById(R.id.managerGuideJournalContent);
        toolbar = root.findViewById(R.id.guideJournalToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        legacyMap = root.findViewById(R.id.cardGuideMap);
        legacyLocationCard = root.findViewById(R.id.cardGuideLocationActions);
        legacyNotesCard = root.findViewById(R.id.cardGuideNotesActions);
        reportCard = root.findViewById(R.id.cardGuideReportActions);
        patient = root.findViewById(R.id.textGuideJournalPatient);
        statusTitle = root.findViewById(R.id.textGuideJournalStatusTitle);
        statusBody = root.findViewById(R.id.textGuideJournalStatusBody);
        summary = root.findViewById(R.id.inputReportSummary);
        treatment = root.findViewById(R.id.inputReportTreatment);
        medicationName = root.findViewById(R.id.inputReportMedicationName);
        medicationChange = root.findViewById(R.id.inputReportMedicationChangeSummary);
        medicationSchedule = root.findViewById(R.id.inputReportMedicationScheduleNote);
        medicationComparisonNote = root.findViewById(
                R.id.inputReportMedicationComparisonNote);
        nextVisit = root.findViewById(R.id.inputNextVisit);
        comparisonMatched = root.findViewById(R.id.radioMedicationComparisonMatched);
        comparisonChanged = root.findViewById(R.id.radioMedicationComparisonChanged);
        comparisonRecheck = root.findViewById(R.id.radioMedicationComparisonRecheck);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
        TextInputLayout summaryLayout = root.findViewById(R.id.layoutReportSummary);
        summaryLayout.setCounterEnabled(true);
        summaryLayout.setCounterMaxLength(300);
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight
    ) {
        boolean journalStep = "MANAGER_JOURNAL".equals(model.getCurrentStepCode());
        content.setVisibility(journalStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(journalStep ? View.VISIBLE : View.GONE);
        if (!journalStep) {
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        hideLegacySectionsExceptReport();
        bindStatus(dashboard);
        setInputsEnabled(model.isInputsEnabled() && !mutationInFlight);
        advance.setIconResource(R.drawable.ic_figma_medication_check);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
    }

    void setInputsEnabled(boolean enabled) {
        summary.setEnabled(enabled);
        treatment.setEnabled(enabled);
        medicationName.setEnabled(enabled);
        medicationChange.setEnabled(enabled);
        medicationSchedule.setEnabled(enabled);
        medicationComparisonNote.setEnabled(enabled);
        nextVisit.setEnabled(enabled);
        comparisonMatched.setEnabled(enabled);
        comparisonChanged.setEnabled(enabled);
        comparisonRecheck.setEnabled(enabled);
    }

    private void bindStatus(ManagerDashboard dashboard) {
        User patientUser = dashboard == null ? null : dashboard.getPatient();
        String patientName = patientUser == null ? "" : normalized(patientUser.getName());
        patient.setText(patientName.isEmpty()
                ? patient.getContext().getString(R.string.guide_remaining_value_unknown)
                : patient.getContext().getString(
                        R.string.guide_journal_patient_label) + " · " + patientName);

        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        boolean retry = ManagerGuideJournalRetryPolicy.shouldShowRetry(session);
        statusTitle.setText(retry
                ? R.string.guide_journal_retry_title
                : R.string.guide_journal_ready_title);
        statusBody.setText(retry
                ? R.string.guide_journal_retry_body
                : R.string.guide_journal_ready_body);
    }

    private void hideLegacySectionsExceptReport() {
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        meetingOverview.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        legacyMap.setVisibility(View.GONE);
        legacyLocationCard.setVisibility(View.GONE);
        legacyNotesCard.setVisibility(View.GONE);
        reportCard.setVisibility(View.VISIBLE);
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
