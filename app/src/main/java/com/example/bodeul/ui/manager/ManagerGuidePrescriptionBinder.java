package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.List;

/** Figma 처방 자료 화면을 기존 선택 첨부 계약과 연결한다. */
final class ManagerGuidePrescriptionBinder {
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
    private final View fieldNoteCard;
    private final View legacyArtifactGroup;
    private final TextView step;
    private final View select;
    private final TextView selectLabel;
    private final TextView status;
    private final MaterialButton clear;
    private final MaterialButton toggleFieldNote;
    private final MaterialButton advance;

    private String boundSessionId = "";
    private boolean fieldNoteExpanded;
    private boolean actionsEnabled;
    private boolean hasArtifacts;

    ManagerGuidePrescriptionBinder(View root) {
        content = root.findViewById(R.id.managerGuidePrescriptionContent);
        toolbar = root.findViewById(R.id.guidePrescriptionToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        fieldNoteCard = root.findViewById(R.id.cardGuideNotesActions);
        legacyArtifactGroup = root.findViewById(R.id.groupGuideSessionArtifact);
        step = root.findViewById(R.id.textGuidePrescriptionStep);
        select = root.findViewById(R.id.buttonGuidePrescriptionSelect);
        selectLabel = root.findViewById(R.id.textGuidePrescriptionSelectLabel);
        status = root.findViewById(R.id.textGuidePrescriptionStatus);
        clear = root.findViewById(R.id.buttonGuidePrescriptionClear);
        toggleFieldNote = root.findViewById(R.id.buttonGuidePrescriptionFieldNote);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
        toggleFieldNote.setOnClickListener(view -> {
            fieldNoteExpanded = !fieldNoteExpanded;
            bindFieldNote();
        });
    }

    void bind(ManagerGuideScreenModel model, ManagerDashboard dashboard, boolean mutationInFlight) {
        boolean prescriptionStep = "PRESCRIPTION_DOCUMENTS".equals(model.getCurrentStepCode());
        content.setVisibility(prescriptionStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(prescriptionStep ? View.VISIBLE : View.GONE);
        if (!prescriptionStep) {
            boundSessionId = "";
            fieldNoteExpanded = false;
            restoreAdvanceIcon();
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
        legacyArtifactGroup.setVisibility(View.GONE);

        String sessionId = dashboard == null || dashboard.getSession() == null
                ? "" : dashboard.getSession().getId();
        if (!TextUtils.equals(boundSessionId, sessionId)) {
            fieldNoteExpanded = false;
        }
        boundSessionId = sessionId;
        int currentOrder = dashboard == null || dashboard.getSession() == null
                ? 0 : dashboard.getSession().getCurrentStepOrder();
        step.setText(currentOrder > 0
                ? step.getContext().getString(R.string.guide_prescription_step_format, currentOrder)
                : step.getContext().getString(R.string.guide_prescription_step_unknown));

        List<CompanionSessionArtifact> artifacts = dashboard == null || dashboard.getSession() == null
                ? Collections.emptyList()
                : dashboard.getSession().getArtifacts(
                        CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE);
        bindArtifacts(artifacts);
        actionsEnabled = model.isInputsEnabled() && !mutationInFlight;
        bindActionEnabledState();
        bindFieldNote();

        advance.setIconResource(R.drawable.ic_figma_prescription_walking);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
        boundSessionId = "";
        fieldNoteExpanded = false;
        restoreAdvanceIcon();
    }

    void setActionsEnabled(boolean enabled) {
        actionsEnabled = enabled;
        bindActionEnabledState();
    }

    private void bindArtifacts(List<CompanionSessionArtifact> artifacts) {
        int count = artifacts == null ? 0 : artifacts.size();
        hasArtifacts = count > 0;
        selectLabel.setText(hasArtifacts
                ? R.string.guide_prescription_select_replace
                : R.string.guide_prescription_select_empty);
        status.setText(hasArtifacts
                ? status.getContext().getString(R.string.guide_prescription_status_count, count)
                : status.getContext().getString(R.string.guide_prescription_status_empty));
        clear.setVisibility(hasArtifacts ? View.VISIBLE : View.GONE);
    }

    private void bindActionEnabledState() {
        select.setEnabled(actionsEnabled);
        select.setClickable(actionsEnabled);
        select.setAlpha(actionsEnabled ? 1f : 0.55f);
        clear.setEnabled(actionsEnabled && hasArtifacts);
        toggleFieldNote.setEnabled(actionsEnabled);
    }

    private void bindFieldNote() {
        fieldNoteCard.setVisibility(fieldNoteExpanded ? View.VISIBLE : View.GONE);
        legacyArtifactGroup.setVisibility(View.GONE);
        toggleFieldNote.setText(fieldNoteExpanded
                ? R.string.guide_prescription_field_note_close
                : R.string.guide_prescription_field_note_open);
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
