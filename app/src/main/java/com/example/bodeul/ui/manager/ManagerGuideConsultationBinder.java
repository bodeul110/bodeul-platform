package com.example.bodeul.ui.manager;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Chronometer;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageButton;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** 진료 보조 전용 화면과 실제 저장 기능에서 분리된 debug 녹음 상태 미리보기를 묶는다. */
final class ManagerGuideConsultationBinder {
    interface DraftListener {
        void onChanged(String sessionId, ManagerGuideConsultationDraft draft);
    }

    private static final String STATE_RECORDING =
            "managerGuide.consultationPreview.recording";
    private static final String STATE_RECORDING_ELAPSED =
            "managerGuide.consultationPreview.elapsed";
    private static final String STATE_RECORDING_STOPPED =
            "managerGuide.consultationPreview.stopped";

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
    private final View legacyLocationCard;
    private final View legacyNotesCard;
    private final TextView step;
    private final ProgressBar progress;
    private final View recordingPreview;
    private final AppCompatImageButton recordPreviewButton;
    private final Chronometer chronometer;
    private final TextView recordingStatus;
    private final TextInputEditText guardianUpdate;
    private final TextInputEditText fieldNote;
    private final MaterialButton saveGuardian;
    private final MaterialButton saveFieldNote;
    private final MaterialButton advance;
    private final DraftListener draftListener;
    private final boolean previewMode;

    private String boundSessionId = "";
    private String savedGuardianUpdate = "";
    private String savedFieldNote = "";
    private boolean bindingDraft;
    private boolean recording;
    private boolean recordingStopped;
    private long recordingElapsedMillis;
    private long recordingStartedAtMillis;

    ManagerGuideConsultationBinder(
            View root,
            boolean previewMode,
            Bundle savedInstanceState,
            DraftListener draftListener
    ) {
        this.previewMode = previewMode;
        this.draftListener = draftListener;
        content = root.findViewById(R.id.managerGuideConsultationContent);
        toolbar = root.findViewById(R.id.guideConsultationToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        legacyLocationCard = root.findViewById(R.id.cardGuideLocationActions);
        legacyNotesCard = root.findViewById(R.id.cardGuideNotesActions);
        step = root.findViewById(R.id.textGuideConsultationStep);
        progress = root.findViewById(R.id.progressGuideConsultation);
        recordingPreview = root.findViewById(R.id.layoutGuideConsultationRecordingPreview);
        recordPreviewButton = root.findViewById(R.id.buttonGuideConsultationRecordPreview);
        chronometer = root.findViewById(R.id.chronometerGuideConsultation);
        recordingStatus = root.findViewById(R.id.textGuideConsultationRecordStatus);
        guardianUpdate = root.findViewById(R.id.inputGuideConsultationGuardian);
        fieldNote = root.findViewById(R.id.inputGuideConsultationFieldNote);
        saveGuardian = root.findViewById(R.id.buttonGuideConsultationSaveGuardian);
        saveFieldNote = root.findViewById(R.id.buttonGuideConsultationSaveFieldNote);
        advance = root.findViewById(R.id.buttonAdvanceGuide);

        restoreRecordingPreview(savedInstanceState);
        recordPreviewButton.setOnClickListener(view -> toggleRecordingPreview());
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(
                    CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!bindingDraft && !boundSessionId.isEmpty()) {
                    draftListener.onChanged(boundSessionId, currentDraft());
                }
            }
        };
        guardianUpdate.addTextChangedListener(watcher);
        fieldNote.addTextChangedListener(watcher);
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight,
            ManagerGuideConsultationDraft savedDraft
    ) {
        boolean consultationStep = "CONSULTATION_SUPPORT".equals(
                model.getCurrentStepCode());
        content.setVisibility(consultationStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(consultationStep ? View.VISIBLE : View.GONE);
        if (!consultationStep) {
            boundSessionId = "";
            chronometer.stop();
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
        legacyLocationCard.setVisibility(View.GONE);
        legacyNotesCard.setVisibility(View.GONE);

        int currentOrder = dashboard == null || dashboard.getSession() == null
                ? 0 : dashboard.getSession().getCurrentStepOrder();
        int totalSteps = model.getStages() == null ? 0 : model.getStages().size();
        step.setText(currentOrder > 0
                ? step.getContext().getString(
                        R.string.guide_consultation_step_format, currentOrder)
                : step.getContext().getString(R.string.guide_consultation_step_unknown));
        progress.setMax(Math.max(Math.max(totalSteps, currentOrder), 1));
        progress.setProgress(Math.max(currentOrder, 0));

        String sessionId = dashboard == null || dashboard.getSession() == null
                ? "" : dashboard.getSession().getId();
        String modelGuardian = normalized(model.getGuardianUpdate());
        String modelFieldNote = normalized(model.getFieldPhotoNote());
        boolean newSession = !TextUtils.equals(boundSessionId, sessionId);
        ManagerGuideConsultationDraft reconciledDraft;
        if (newSession) {
            reconciledDraft = savedDraft == null
                    ? ManagerGuideConsultationDraft.fromServer(modelGuardian, modelFieldNote)
                    : savedDraft.reconcileServer(modelGuardian, modelFieldNote);
        } else {
            reconciledDraft = currentDraft().reconcileServer(modelGuardian, modelFieldNote);
        }
        bindDraft(reconciledDraft);
        boundSessionId = sessionId;
        savedGuardianUpdate = modelGuardian;
        savedFieldNote = modelFieldNote;
        if (!reconciledDraft.isGuardianDirty()) {
            guardianUpdate.setError(null);
        }
        if (!reconciledDraft.isFieldNoteDirty()) {
            fieldNote.setError(null);
        }
        if (!sessionId.isEmpty()) {
            draftListener.onChanged(sessionId, currentDraft());
        }

        setInputsEnabled(model.isInputsEnabled() && !mutationInFlight);
        recordingPreview.setVisibility(previewMode ? View.VISIBLE : View.GONE);
        if (previewMode) {
            bindRecordingPreview();
        }
        advance.setIcon(null);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
        boundSessionId = "";
        chronometer.stop();
        restoreAdvanceIcon();
    }

    void setInputsEnabled(boolean enabled) {
        guardianUpdate.setEnabled(enabled);
        fieldNote.setEnabled(enabled);
        saveGuardian.setEnabled(enabled);
        saveFieldNote.setEnabled(enabled);
    }

    String guardianUpdate() {
        return valueOf(guardianUpdate);
    }

    String fieldNote() {
        return valueOf(fieldNote);
    }

    boolean showUnsavedInputError() {
        guardianUpdate.setError(null);
        fieldNote.setError(null);
        ManagerGuideConsultationDraft draft = currentDraft();
        if (draft.isGuardianDirty()) {
            showError(guardianUpdate);
            return true;
        }
        if (draft.isFieldNoteDirty()) {
            showError(fieldNote);
            return true;
        }
        return false;
    }

    boolean hasUnsavedInput() {
        return currentDraft().hasUnsavedChanges();
    }

    void discardUnsavedInput() {
        ManagerGuideConsultationDraft cleanDraft =
                ManagerGuideConsultationDraft.fromServer(
                        savedGuardianUpdate, savedFieldNote);
        bindDraft(cleanDraft);
        guardianUpdate.setError(null);
        fieldNote.setError(null);
        if (!boundSessionId.isEmpty()) {
            draftListener.onChanged(boundSessionId, cleanDraft);
        }
    }

    void saveInstanceState(Bundle outState) {
        if (!previewMode) {
            return;
        }
        outState.putBoolean(STATE_RECORDING, recording);
        outState.putLong(STATE_RECORDING_ELAPSED, currentRecordingElapsedMillis());
        outState.putBoolean(STATE_RECORDING_STOPPED, recordingStopped);
    }

    private void bindDraft(ManagerGuideConsultationDraft draft) {
        bindingDraft = true;
        try {
            setTextIfDifferent(guardianUpdate, draft.guardianUpdate);
            setTextIfDifferent(fieldNote, draft.fieldNote);
        } finally {
            bindingDraft = false;
        }
    }

    private ManagerGuideConsultationDraft currentDraft() {
        return ManagerGuideConsultationDraft.fromInputs(
                rawValueOf(guardianUpdate),
                rawValueOf(fieldNote),
                savedGuardianUpdate,
                savedFieldNote);
    }

    private void toggleRecordingPreview() {
        if (!previewMode) {
            return;
        }
        if (recording) {
            recordingElapsedMillis = currentRecordingElapsedMillis();
            recordingStartedAtMillis = 0L;
            recording = false;
            recordingStopped = true;
        } else {
            recordingElapsedMillis = 0L;
            recordingStartedAtMillis = SystemClock.elapsedRealtime();
            recording = true;
            recordingStopped = false;
        }
        bindRecordingPreview();
    }

    private void bindRecordingPreview() {
        long now = SystemClock.elapsedRealtime();
        long elapsed = currentRecordingElapsedMillis();
        chronometer.setBase(now - elapsed);
        if (recording) {
            chronometer.start();
            recordingStatus.setText(R.string.guide_consultation_record_active);
            recordPreviewButton.setContentDescription(
                    recordPreviewButton.getContext().getString(
                            R.string.guide_consultation_record_stop));
        } else {
            chronometer.stop();
            recordingStatus.setText(recordingStopped
                    ? R.string.guide_consultation_record_stopped
                    : R.string.guide_consultation_record_ready);
            recordPreviewButton.setContentDescription(
                    recordPreviewButton.getContext().getString(
                            R.string.guide_consultation_record_start));
        }
    }

    private void restoreRecordingPreview(Bundle savedInstanceState) {
        if (!previewMode || savedInstanceState == null) {
            return;
        }
        recording = savedInstanceState.getBoolean(STATE_RECORDING, false);
        recordingElapsedMillis = Math.max(
                0L, savedInstanceState.getLong(STATE_RECORDING_ELAPSED, 0L));
        recordingStopped = savedInstanceState.getBoolean(
                STATE_RECORDING_STOPPED, false);
        if (recording) {
            recordingStartedAtMillis = SystemClock.elapsedRealtime();
        }
    }

    private long currentRecordingElapsedMillis() {
        if (!recording || recordingStartedAtMillis == 0L) {
            return recordingElapsedMillis;
        }
        return recordingElapsedMillis
                + Math.max(0L, SystemClock.elapsedRealtime() - recordingStartedAtMillis);
    }

    private void showError(TextInputEditText input) {
        input.setError(input.getContext().getString(
                R.string.guide_consultation_save_before_complete));
        input.requestFocus();
    }

    private String valueOf(TextInputEditText input) {
        return rawValueOf(input).trim();
    }

    private String rawValueOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private void setTextIfDifferent(TextInputEditText input, String value) {
        String current = rawValueOf(input);
        if (!TextUtils.equals(current, value)) {
            input.setText(value);
        }
    }

    private void restoreAdvanceIcon() {
        advance.setIconResource(R.drawable.ic_figma_guide_arrow_vector);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources()
                .getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
