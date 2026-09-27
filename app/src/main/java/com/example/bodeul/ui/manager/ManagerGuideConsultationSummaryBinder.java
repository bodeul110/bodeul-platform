package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Figma Step 7의 리포트 위계를 기존 진료 메모 저장 계약에 연결한다. */
final class ManagerGuideConsultationSummaryBinder {
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
    private final View legacyReportCard;
    private final TextView patient;
    private final TextView hospital;
    private final TextView department;
    private final TextInputEditText note;
    private final MaterialButton saveNote;
    private final MaterialButton advance;

    private String boundSessionId = "";
    private String boundNote = "";

    ManagerGuideConsultationSummaryBinder(View root) {
        content = root.findViewById(R.id.managerGuideConsultationSummaryContent);
        toolbar = root.findViewById(R.id.guideConsultationSummaryToolbar);
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
        legacyReportCard = root.findViewById(R.id.cardGuideReportActions);
        patient = root.findViewById(R.id.textGuideSummaryPatient);
        hospital = root.findViewById(R.id.textGuideSummaryHospital);
        department = root.findViewById(R.id.textGuideSummaryDepartment);
        note = root.findViewById(R.id.inputGuideSummaryNote);
        saveNote = root.findViewById(R.id.buttonGuideSummarySaveNote);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight
    ) {
        boolean summaryStep = "CONSULTATION_SUMMARY".equals(model.getCurrentStepCode());
        content.setVisibility(summaryStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(summaryStep ? View.VISIBLE : View.GONE);
        if (!summaryStep) {
            boundSessionId = "";
            boundNote = "";
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        hideLegacySections();
        bindDashboard(dashboard);

        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        String sessionId = session == null ? "" : normalized(session.getId());
        String serverNote = normalized(model.getFieldPhotoNote());
        String currentNote = rawValueOf(note);
        boolean newSession = !TextUtils.equals(boundSessionId, sessionId);
        if (newSession || TextUtils.equals(currentNote, boundNote)) {
            setTextIfDifferent(note, serverNote);
        }
        boundSessionId = sessionId;
        boundNote = serverNote;
        setInputsEnabled(model.isInputsEnabled() && !mutationInFlight);
        advance.setIconResource(R.drawable.ic_figma_guide_arrow_vector);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
        boundSessionId = "";
        boundNote = "";
    }

    void setInputsEnabled(boolean enabled) {
        note.setEnabled(enabled);
        saveNote.setEnabled(enabled);
    }

    String note() {
        return rawValueOf(note).trim();
    }

    private void bindDashboard(ManagerDashboard dashboard) {
        User patientUser = dashboard == null ? null : dashboard.getPatient();
        AppointmentRequest request = dashboard == null
                ? null : dashboard.getAppointmentRequest();
        patient.setText(valueOrUnknown(patientUser == null ? "" : patientUser.getName()));
        hospital.setText(valueOrUnknown(request == null ? "" : request.getHospitalName()));
        department.setText(valueOrUnknown(request == null ? "" : request.getDepartmentName()));
    }

    private void hideLegacySections() {
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
        legacyReportCard.setVisibility(View.GONE);
    }

    private String valueOrUnknown(String value) {
        String normalized = normalized(value);
        return normalized.isEmpty()
                ? patient.getContext().getString(R.string.guide_remaining_value_unknown)
                : normalized;
    }

    private String rawValueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void setTextIfDifferent(TextInputEditText input, String value) {
        if (!TextUtils.equals(rawValueOf(input), value)) {
            input.setText(value);
        }
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
