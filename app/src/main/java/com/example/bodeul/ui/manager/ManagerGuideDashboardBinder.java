package com.example.bodeul.ui.manager;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.ui.booking.BookingLocationMapView;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * 동행 가이드 화면 모델을 실제 뷰에 렌더링한다.
 */
public final class ManagerGuideDashboardBinder {
    private final LayoutInflater inflater;
    private final ManagerGuideStageItemBinder stageItemBinder;
    private final TextView textGuideMode;
    private final TextView textGuideTitle;
    private final TextView textGuideSubtitle;
    private final TextView textGuideHeroBadge;
    private final TextView textGuideHeroTitle;
    private final TextView textGuideHeroBody;
    private final TextView textGuideHeroNote;
    private final BookingLocationMapView viewGuideHospitalMap;
    private final LinearLayout guideMapActionContainer;
    private final ManagerGuideMapActionBinder mapActionBinder;
    private final ManagerGuideMeetingOverviewBinder meetingOverviewBinder;
    private final LinearLayout guideStageRailContainer;
    private final TextView textGuideFocusBadge;
    private final TextView textGuideFocusTitle;
    private final TextView textGuideFocusBody;
    private final TextView textGuideFocusPreviewLabel;
    private final TextView textGuideFocusPreviewBody;
    private final View viewGuideFocusPreview;
    private final ManagerGuideVideoGuidanceBinder videoGuidanceBinder;
    private final ManagerGuideStepSectionsBinder stepSectionsBinder;
    private final ManagerGuideMemoSummaryBinder memoSummaryBinder;
    private final TextView textGuideLiveLocationStatus;
    private final TextView textGuideLiveLocationHistory;
    private final TextInputEditText inputGuideLocationSummary;
    private final TextInputEditText inputGuardianUpdate;
    private final TextInputEditText inputGuidePhotoNote;
    private final MaterialCheckBox checkGuidePreConsultationConfirmed;
    private final TextInputEditText inputMedicationNote;
    private final TextInputEditText inputPharmacySummary;
    private final TextView textGuidePharmacyProgressSummary;
    private final TextInputEditText inputReportSummary;
    private final TextInputEditText inputReportTreatment;
    private final TextInputEditText inputReportMedicationName;
    private final TextInputEditText inputReportMedicationChangeSummary;
    private final TextInputEditText inputReportMedicationScheduleNote;
    private final RadioGroup groupReportMedicationComparisonDecision;
    private final MaterialRadioButton radioMedicationComparisonMatched;
    private final MaterialRadioButton radioMedicationComparisonChanged;
    private final MaterialRadioButton radioMedicationComparisonRecheck;
    private final TextInputEditText inputReportMedicationComparisonNote;
    private final TextInputEditText inputNextVisit;
    private final MaterialButton buttonAdvanceGuide;
    private final MaterialButton buttonSaveLocationSummary;
    private final MaterialButton buttonShareCurrentLocation;
    private final MaterialButton buttonStartLiveLocationSharing;
    private final MaterialButton buttonStopLiveLocationSharing;
    private final MaterialButton buttonSaveGuardianUpdate;
    private final MaterialButton buttonSaveGuidePhotoNote;
    private final MaterialButton buttonSaveMedicationNote;
    private final MaterialButton buttonSavePharmacySummary;
    private final MaterialButton buttonTogglePrescriptionCollected;
    private final MaterialButton buttonTogglePharmacyCompleted;
    private final MaterialButton buttonToggleMedicationGuidanceCompleted;
    private final MaterialButton buttonSubmitReport;

    public ManagerGuideDashboardBinder(
            LayoutInflater inflater,
            ManagerGuideStageItemBinder stageItemBinder,
            TextView textGuideMode,
            TextView textGuideTitle,
            TextView textGuideSubtitle,
            TextView textGuideHeroBadge,
            TextView textGuideHeroTitle,
            TextView textGuideHeroBody,
            TextView textGuideHeroNote,
            BookingLocationMapView viewGuideHospitalMap,
            LinearLayout guideMapActionContainer,
            ManagerGuideMapActionBinder mapActionBinder,
            ManagerGuideMeetingOverviewBinder meetingOverviewBinder,
            LinearLayout guideStageRailContainer,
            TextView textGuideFocusBadge,
            TextView textGuideFocusTitle,
            TextView textGuideFocusBody,
            TextView textGuideFocusPreviewLabel,
            TextView textGuideFocusPreviewBody,
            View viewGuideFocusPreview,
            ManagerGuideVideoGuidanceBinder videoGuidanceBinder,
            ManagerGuideStepSectionsBinder stepSectionsBinder,
            ManagerGuideMemoSummaryBinder memoSummaryBinder,
            TextView textGuideLiveLocationStatus,
            TextView textGuideLiveLocationHistory,
            TextInputEditText inputGuideLocationSummary,
            TextInputEditText inputGuardianUpdate,
            TextInputEditText inputGuidePhotoNote,
            MaterialCheckBox checkGuidePreConsultationConfirmed,
            TextInputEditText inputMedicationNote,
            TextInputEditText inputPharmacySummary,
            TextView textGuidePharmacyProgressSummary,
            TextInputEditText inputReportSummary,
            TextInputEditText inputReportTreatment,
            TextInputEditText inputReportMedicationName,
            TextInputEditText inputReportMedicationChangeSummary,
            TextInputEditText inputReportMedicationScheduleNote,
            RadioGroup groupReportMedicationComparisonDecision,
            MaterialRadioButton radioMedicationComparisonMatched,
            MaterialRadioButton radioMedicationComparisonChanged,
            MaterialRadioButton radioMedicationComparisonRecheck,
            TextInputEditText inputReportMedicationComparisonNote,
            TextInputEditText inputNextVisit,
            MaterialButton buttonAdvanceGuide,
            MaterialButton buttonSaveLocationSummary,
            MaterialButton buttonShareCurrentLocation,
            MaterialButton buttonStartLiveLocationSharing,
            MaterialButton buttonStopLiveLocationSharing,
            MaterialButton buttonSaveGuardianUpdate,
            MaterialButton buttonSaveGuidePhotoNote,
            MaterialButton buttonSaveMedicationNote,
            MaterialButton buttonSavePharmacySummary,
            MaterialButton buttonTogglePrescriptionCollected,
            MaterialButton buttonTogglePharmacyCompleted,
            MaterialButton buttonToggleMedicationGuidanceCompleted,
            MaterialButton buttonSubmitReport
    ) {
        this.inflater = inflater;
        this.stageItemBinder = stageItemBinder;
        this.textGuideMode = textGuideMode;
        this.textGuideTitle = textGuideTitle;
        this.textGuideSubtitle = textGuideSubtitle;
        this.textGuideHeroBadge = textGuideHeroBadge;
        this.textGuideHeroTitle = textGuideHeroTitle;
        this.textGuideHeroBody = textGuideHeroBody;
        this.textGuideHeroNote = textGuideHeroNote;
        this.viewGuideHospitalMap = viewGuideHospitalMap;
        this.guideMapActionContainer = guideMapActionContainer;
        this.mapActionBinder = mapActionBinder;
        this.meetingOverviewBinder = meetingOverviewBinder;
        this.guideStageRailContainer = guideStageRailContainer;
        this.textGuideFocusBadge = textGuideFocusBadge;
        this.textGuideFocusTitle = textGuideFocusTitle;
        this.textGuideFocusBody = textGuideFocusBody;
        this.textGuideFocusPreviewLabel = textGuideFocusPreviewLabel;
        this.textGuideFocusPreviewBody = textGuideFocusPreviewBody;
        this.viewGuideFocusPreview = viewGuideFocusPreview;
        this.videoGuidanceBinder = videoGuidanceBinder;
        this.stepSectionsBinder = stepSectionsBinder;
        this.memoSummaryBinder = memoSummaryBinder;
        this.textGuideLiveLocationStatus = textGuideLiveLocationStatus;
        this.textGuideLiveLocationHistory = textGuideLiveLocationHistory;
        this.inputGuideLocationSummary = inputGuideLocationSummary;
        this.inputGuardianUpdate = inputGuardianUpdate;
        this.inputGuidePhotoNote = inputGuidePhotoNote;
        this.checkGuidePreConsultationConfirmed = checkGuidePreConsultationConfirmed;
        this.inputMedicationNote = inputMedicationNote;
        this.inputPharmacySummary = inputPharmacySummary;
        this.textGuidePharmacyProgressSummary = textGuidePharmacyProgressSummary;
        this.inputReportSummary = inputReportSummary;
        this.inputReportTreatment = inputReportTreatment;
        this.inputReportMedicationName = inputReportMedicationName;
        this.inputReportMedicationChangeSummary = inputReportMedicationChangeSummary;
        this.inputReportMedicationScheduleNote = inputReportMedicationScheduleNote;
        this.groupReportMedicationComparisonDecision = groupReportMedicationComparisonDecision;
        this.radioMedicationComparisonMatched = radioMedicationComparisonMatched;
        this.radioMedicationComparisonChanged = radioMedicationComparisonChanged;
        this.radioMedicationComparisonRecheck = radioMedicationComparisonRecheck;
        this.inputReportMedicationComparisonNote = inputReportMedicationComparisonNote;
        this.inputNextVisit = inputNextVisit;
        this.buttonAdvanceGuide = buttonAdvanceGuide;
        this.buttonSaveLocationSummary = buttonSaveLocationSummary;
        this.buttonShareCurrentLocation = buttonShareCurrentLocation;
        this.buttonStartLiveLocationSharing = buttonStartLiveLocationSharing;
        this.buttonStopLiveLocationSharing = buttonStopLiveLocationSharing;
        this.buttonSaveGuardianUpdate = buttonSaveGuardianUpdate;
        this.buttonSaveGuidePhotoNote = buttonSaveGuidePhotoNote;
        this.buttonSaveMedicationNote = buttonSaveMedicationNote;
        this.buttonSavePharmacySummary = buttonSavePharmacySummary;
        this.buttonTogglePrescriptionCollected = buttonTogglePrescriptionCollected;
        this.buttonTogglePharmacyCompleted = buttonTogglePharmacyCompleted;
        this.buttonToggleMedicationGuidanceCompleted = buttonToggleMedicationGuidanceCompleted;
        this.buttonSubmitReport = buttonSubmitReport;
    }

    public void bindScreen(ManagerGuideScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textGuideMode, screenModel.getModeLabel());
        textGuideTitle.setText(screenModel.getTitle());
        textGuideSubtitle.setText(screenModel.getSubtitle());
        textGuideHeroBadge.setText(screenModel.getHeroBadge());
        textGuideHeroTitle.setText(screenModel.getHeroTitle());
        textGuideHeroBody.setText(screenModel.getHeroBody());
        textGuideHeroNote.setText(screenModel.getHeroNote());
        meetingOverviewBinder.bind(screenModel);

        bindHospitalMap(screenModel.getHospitalMapPreviewModel());
        bindMapActions(screenModel.getMapActions(), screenModel.isMeetingStep());
        bindStages(screenModel.getStages());
        bindFocus(screenModel.getFocusModel());
        boolean showMemoSummary = ManagerGuideMemoSummaryDisplayPolicy.shouldShow(
                screenModel.getPrimaryAction());
        stepSectionsBinder.bind(
                screenModel.getSectionVisibility(),
                screenModel.getPresentationStepCode(),
                showMemoSummary
        );
        memoSummaryBinder.bind(
                screenModel.getMemoSummaryItems(),
                showMemoSummary);
        textGuideLiveLocationStatus.setText(screenModel.getLiveLocationStatus());
        textGuideLiveLocationHistory.setText(screenModel.getLiveLocationHistory());

        setTextIfDifferent(inputGuideLocationSummary, screenModel.getLocationSummary());
        setTextIfDifferent(inputGuardianUpdate, screenModel.getGuardianUpdate());
        setTextIfDifferent(inputGuidePhotoNote, screenModel.getFieldPhotoNote());
        if (checkGuidePreConsultationConfirmed.isChecked()
                != screenModel.isPreConsultationConfirmed()) {
            checkGuidePreConsultationConfirmed.setChecked(
                    screenModel.isPreConsultationConfirmed());
        }
        setTextIfDifferent(inputMedicationNote, screenModel.getMedicationNote());
        setTextIfDifferent(inputPharmacySummary, screenModel.getPharmacySummary());
        textGuidePharmacyProgressSummary.setText(screenModel.getPharmacyProgressSummary());
        setTextIfDifferent(inputReportSummary, screenModel.getReportSummary());
        setTextIfDifferent(inputReportTreatment, screenModel.getReportTreatment());
        setTextIfDifferent(inputReportMedicationName, screenModel.getReportMedicationName());
        setTextIfDifferent(
                inputReportMedicationChangeSummary,
                screenModel.getReportMedicationChangeSummary()
        );
        setTextIfDifferent(
                inputReportMedicationScheduleNote,
                screenModel.getReportMedicationScheduleNote()
        );
        bindMedicationComparisonDecision(screenModel.getReportMedicationComparisonDecision());
        setTextIfDifferent(
                inputReportMedicationComparisonNote,
                screenModel.getReportMedicationComparisonNote()
        );
        setTextIfDifferent(inputNextVisit, screenModel.getNextVisitAt());

        boolean inputsEnabled = screenModel.isInputsEnabled();
        inputGuideLocationSummary.setEnabled(inputsEnabled);
        inputGuardianUpdate.setEnabled(inputsEnabled);
        inputGuidePhotoNote.setEnabled(inputsEnabled);
        checkGuidePreConsultationConfirmed.setEnabled(inputsEnabled);
        inputMedicationNote.setEnabled(inputsEnabled);
        inputPharmacySummary.setEnabled(inputsEnabled);
        inputReportSummary.setEnabled(inputsEnabled);
        inputReportTreatment.setEnabled(inputsEnabled);
        inputReportMedicationName.setEnabled(inputsEnabled);
        inputReportMedicationChangeSummary.setEnabled(inputsEnabled);
        inputReportMedicationScheduleNote.setEnabled(inputsEnabled);
        radioMedicationComparisonMatched.setEnabled(inputsEnabled);
        radioMedicationComparisonChanged.setEnabled(inputsEnabled);
        radioMedicationComparisonRecheck.setEnabled(inputsEnabled);
        inputReportMedicationComparisonNote.setEnabled(inputsEnabled);
        inputNextVisit.setEnabled(inputsEnabled);
        buttonSaveLocationSummary.setEnabled(inputsEnabled);
        buttonShareCurrentLocation.setEnabled(inputsEnabled);
        buttonStartLiveLocationSharing.setEnabled(
                inputsEnabled && !screenModel.isLiveLocationSharingActive()
        );
        buttonStopLiveLocationSharing.setEnabled(
                inputsEnabled && screenModel.isLiveLocationSharingActive()
        );
        buttonSaveGuardianUpdate.setEnabled(inputsEnabled);
        buttonSaveGuidePhotoNote.setEnabled(inputsEnabled);
        buttonSaveMedicationNote.setEnabled(inputsEnabled);
        buttonSavePharmacySummary.setEnabled(inputsEnabled);
        buttonTogglePrescriptionCollected.setEnabled(inputsEnabled);
        buttonTogglePharmacyCompleted.setEnabled(inputsEnabled);
        buttonToggleMedicationGuidanceCompleted.setEnabled(inputsEnabled);
        buttonSubmitReport.setEnabled(
                inputsEnabled
                        && screenModel.getPrimaryAction()
                        == ManagerGuidePrimaryAction.SUBMIT_REPORT
        );

        buttonAdvanceGuide.setText(screenModel.getAdvanceButtonLabel());
        buttonAdvanceGuide.setEnabled(screenModel.isAdvanceEnabled());
        buttonTogglePrescriptionCollected.setText(screenModel.getPrescriptionActionLabel());
        buttonTogglePharmacyCompleted.setText(screenModel.getPharmacyActionLabel());
        buttonToggleMedicationGuidanceCompleted.setText(screenModel.getMedicationGuidanceActionLabel());
        buttonSubmitReport.setText(screenModel.getReportButtonLabel());
    }

    private void bindMedicationComparisonDecision(@Nullable MedicationComparisonDecision decision) {
        if (decision == null) {
            if (groupReportMedicationComparisonDecision.getCheckedRadioButtonId() != -1) {
                groupReportMedicationComparisonDecision.clearCheck();
            }
            return;
        }
        int targetId;
        switch (decision) {
            case MATCHED:
                targetId = radioMedicationComparisonMatched.getId();
                break;
            case CHANGED:
                targetId = radioMedicationComparisonChanged.getId();
                break;
            case RECHECK_REQUIRED:
                targetId = radioMedicationComparisonRecheck.getId();
                break;
            default:
                targetId = -1;
                break;
        }
        if (targetId == -1) {
            groupReportMedicationComparisonDecision.clearCheck();
            return;
        }
        if (groupReportMedicationComparisonDecision.getCheckedRadioButtonId() != targetId) {
            groupReportMedicationComparisonDecision.check(targetId);
        }
    }

    private void bindHospitalMap(com.example.bodeul.ui.common.HospitalMapPreviewModel mapPreviewModel) {
        if (mapPreviewModel == null || mapPreviewModel.getPointOptions().isEmpty()) {
            viewGuideHospitalMap.setVisibility(View.GONE);
            return;
        }
        viewGuideHospitalMap.setVisibility(View.VISIBLE);
        viewGuideHospitalMap.setPointOptions(mapPreviewModel.getPointOptions());
        viewGuideHospitalMap.setSelectedPointId(mapPreviewModel.getSelectedPointId());
        viewGuideHospitalMap.setHighlightedPointId(mapPreviewModel.getHighlightedPointId());
        viewGuideHospitalMap.setOnPointSelectedListener(null);
    }

    private void bindMapActions(List<ManagerGuideMapActionModel> actions, boolean meetingStep) {
        guideMapActionContainer.removeAllViews();
        guideMapActionContainer.setVisibility(actions.isEmpty() ? View.GONE : View.VISIBLE);
        for (ManagerGuideMapActionModel action : actions) {
            View actionView = inflater.inflate(
                    meetingStep
                            ? R.layout.item_manager_guide_meeting_map_action
                            : R.layout.item_manager_guide_map_action,
                    guideMapActionContainer,
                    false
            );
            if (meetingStep) {
                mapActionBinder.bindMeeting(actionView, action);
            } else {
                mapActionBinder.bind(actionView, action);
            }
            guideMapActionContainer.addView(actionView);
        }
    }

    private void bindStages(List<ManagerGuideStageModel> stages) {
        guideStageRailContainer.removeAllViews();
        if (guideStageRailContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        for (ManagerGuideStageModel stage : stages) {
            View stageView = inflater.inflate(R.layout.item_manager_guide_stage, guideStageRailContainer, false);
            stageItemBinder.bind(stageView, stage);
            guideStageRailContainer.addView(stageView);
        }
    }

    private void bindFocus(ManagerGuideFocusModel focusModel) {
        textGuideFocusBadge.setText(focusModel.getBadge());
        textGuideFocusTitle.setText(focusModel.getTitle());
        textGuideFocusBody.setText(focusModel.getBody());
        textGuideFocusPreviewLabel.setText(focusModel.getPreviewLabel());
        textGuideFocusPreviewBody.setText(focusModel.getPreviewBody());
        viewGuideFocusPreview.setBackgroundResource(focusModel.getPreviewBackgroundResId());
        videoGuidanceBinder.bind(focusModel);
    }

    private void setTextIfDifferent(TextInputEditText editText, String value) {
        String safeValue = value == null ? "" : value;
        String currentValue = editText.getText() == null ? "" : editText.getText().toString();
        if (!TextUtils.equals(currentValue, safeValue)) {
            editText.setText(safeValue);
        }
    }
}
