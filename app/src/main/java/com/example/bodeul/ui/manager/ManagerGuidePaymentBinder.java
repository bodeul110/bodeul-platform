package com.example.bodeul.ui.manager;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;

/** Figma 수납 화면을 실제 병원 정보, 공유 메모, 선택 결제 증빙 계약과 연결한다. */
final class ManagerGuidePaymentBinder {
    interface DraftListener {
        void onChanged(String sessionId, ManagerGuidePaymentDraft draft);
    }

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
    private final View legacyArtifactGroup;
    private final TextView step;
    private final TextView hospital;
    private final View select;
    private final TextView selectLabel;
    private final TextView status;
    private final TextView file;
    private final MaterialButton clear;
    private final TextInputEditText note;
    private final MaterialButton saveNote;
    private final DraftListener draftListener;

    private String boundSessionId = "";
    private String boundNote = "";
    private boolean actionsEnabled;
    private boolean hasArtifact;
    private boolean bindingDraft;

    ManagerGuidePaymentBinder(View root, DraftListener draftListener) {
        this.draftListener = draftListener;
        content = root.findViewById(R.id.managerGuidePaymentContent);
        toolbar = root.findViewById(R.id.guidePaymentToolbar);
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
        legacyArtifactGroup = root.findViewById(R.id.groupGuideSessionArtifact);
        step = root.findViewById(R.id.textGuidePaymentStep);
        hospital = root.findViewById(R.id.textGuidePaymentHospital);
        select = root.findViewById(R.id.buttonGuidePaymentSelect);
        selectLabel = root.findViewById(R.id.textGuidePaymentSelectLabel);
        status = root.findViewById(R.id.textGuidePaymentStatus);
        file = root.findViewById(R.id.textGuidePaymentFile);
        clear = root.findViewById(R.id.buttonGuidePaymentClear);
        note = root.findViewById(R.id.inputGuidePaymentNote);
        saveNote = root.findViewById(R.id.buttonGuidePaymentSaveNote);
        ViewCompat.setAccessibilityDelegate(select, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                    View host,
                    AccessibilityNodeInfoCompat info
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
        note.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(
                    CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!bindingDraft && !boundSessionId.isEmpty()) {
                    draftListener.onChanged(boundSessionId, currentDraft());
                }
            }
        });
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight,
            ManagerGuidePaymentDraft savedDraft
    ) {
        boolean paymentStep = "PAYMENT_EVIDENCE".equals(model.getCurrentStepCode());
        content.setVisibility(paymentStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(paymentStep ? View.VISIBLE : View.GONE);
        if (!paymentStep) {
            resetBoundState();
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
        legacyArtifactGroup.setVisibility(View.GONE);

        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        String sessionId = session == null ? "" : normalized(session.getId());
        boolean newSession = !TextUtils.equals(boundSessionId, sessionId);
        int currentOrder = session == null ? 0 : session.getCurrentStepOrder();
        step.setText(currentOrder > 0
                ? step.getContext().getString(R.string.guide_payment_step_format, currentOrder)
                : step.getContext().getString(R.string.guide_payment_step_unknown));
        bindHospital(dashboard);
        bindArtifacts(session == null
                ? Collections.emptyList()
                : session.getArtifacts(CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE));
        String serverNote = session == null ? "" : normalized(session.getFieldPhotoNote());
        ManagerGuidePaymentDraft reconciledDraft;
        if (newSession) {
            reconciledDraft = savedDraft == null
                    ? ManagerGuidePaymentDraft.fromServer(serverNote)
                    : savedDraft.reconcileServer(serverNote);
        } else {
            reconciledDraft = currentDraft().reconcileServer(serverNote);
        }
        bindDraft(reconciledDraft);
        boundSessionId = sessionId;
        boundNote = serverNote;
        if (!sessionId.isEmpty()) {
            draftListener.onChanged(sessionId, currentDraft());
        }

        actionsEnabled = model.isInputsEnabled() && !mutationInFlight;
        bindActionEnabledState();
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
    }

    void setInputsEnabled(boolean enabled) {
        actionsEnabled = enabled;
        bindActionEnabledState();
    }

    String note() {
        return valueOf(note);
    }

    boolean hasUnsavedInput() {
        return !boundSessionId.isEmpty() && currentDraft().hasUnsavedChanges();
    }

    void discardUnsavedInput() {
        note.setText(boundNote);
        note.clearFocus();
    }

    private void bindHospital(ManagerDashboard dashboard) {
        AppointmentRequest request = dashboard == null ? null : dashboard.getAppointmentRequest();
        String hospitalName = request == null ? "" : normalized(request.getHospitalName());
        String departmentName = request == null ? "" : normalized(request.getDepartmentName());
        if (hospitalName.isEmpty() && departmentName.isEmpty()) {
            hospital.setText(R.string.guide_payment_location_empty);
        } else if (hospitalName.isEmpty()) {
            hospital.setText(departmentName);
        } else if (departmentName.isEmpty()) {
            hospital.setText(hospitalName);
        } else {
            hospital.setText(hospital.getContext().getString(
                    R.string.guide_payment_location_format, hospitalName, departmentName));
        }
    }

    private void bindArtifacts(List<CompanionSessionArtifact> artifacts) {
        hasArtifact = artifacts != null && !artifacts.isEmpty();
        selectLabel.setText(hasArtifact
                ? R.string.guide_payment_select_replace
                : R.string.guide_payment_select_empty);
        select.setContentDescription(select.getContext().getString(hasArtifact
                ? R.string.guide_payment_replace_description
                : R.string.guide_payment_upload_description));
        status.setText(hasArtifact
                ? R.string.guide_payment_status_registered
                : R.string.guide_payment_status_empty);
        clear.setVisibility(hasArtifact ? View.VISIBLE : View.GONE);
        file.setVisibility(hasArtifact ? View.VISIBLE : View.GONE);
        if (hasArtifact) {
            CompanionSessionArtifact artifact = artifacts.get(0);
            file.setText(file.getContext().getString(
                    R.string.guide_payment_file_format,
                    normalized(artifact.getFileName()),
                    formatSize(artifact.getSizeBytes())));
        }
    }

    private String formatSize(long sizeBytes) {
        if (sizeBytes <= 0L) {
            return file.getContext().getString(R.string.guide_payment_file_size_unknown);
        }
        if (sizeBytes >= 1024L * 1024L) {
            return file.getContext().getString(
                    R.string.guide_payment_file_size_mib,
                    sizeBytes / (1024d * 1024d));
        }
        return file.getContext().getString(
                R.string.guide_payment_file_size_kib,
                sizeBytes / 1024d);
    }

    private ManagerGuidePaymentDraft currentDraft() {
        return ManagerGuidePaymentDraft.fromInput(valueOf(note), boundNote);
    }

    private void bindDraft(ManagerGuidePaymentDraft draft) {
        bindingDraft = true;
        try {
            if (!TextUtils.equals(valueOf(note), draft.note)) {
                note.setText(draft.note);
            }
            boundNote = draft.baseline;
        } finally {
            bindingDraft = false;
        }
    }

    private void bindActionEnabledState() {
        select.setEnabled(actionsEnabled);
        select.setClickable(actionsEnabled);
        select.setAlpha(actionsEnabled ? 1f : 0.55f);
        clear.setEnabled(actionsEnabled && hasArtifact);
        note.setEnabled(actionsEnabled);
        saveNote.setEnabled(actionsEnabled);
    }

    private void resetBoundState() {
        boundSessionId = "";
        boundNote = "";
        note.setText("");
    }

    private String valueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
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
