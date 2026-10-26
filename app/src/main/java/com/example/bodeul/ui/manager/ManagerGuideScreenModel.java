package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.ui.common.HospitalMapPreviewModel;

import java.util.List;

/**
 * 매니저 동행 가이드 화면 전체를 한 번에 렌더링하기 위한 모델이다.
 */
public final class ManagerGuideScreenModel {
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String heroNote;
    private final String meetingPatient;
    private final String meetingPlace;
    private final String meetingDepartment;
    private final List<ManagerGuideMapActionModel> mapActions;
    private final HospitalMapPreviewModel hospitalMapPreviewModel;
    private final List<ManagerGuideStageModel> stages;
    private final ManagerGuideFocusModel focusModel;
    private final ManagerGuideSectionVisibility sectionVisibility;
    private final List<ManagerGuideMemoItem> memoSummaryItems;
    private final String currentStepCode;
    private final String presentationStepCode;
    private final String liveLocationStatus;
    private final String liveLocationHistory;
    private final String locationSummary;
    private final String guardianUpdate;
    private final String fieldPhotoNote;
    private final boolean preConsultationConfirmed;
    private final String medicationNote;
    private final String pharmacySummary;
    private final String pharmacyProgressSummary;
    private final String prescriptionActionLabel;
    private final String pharmacyActionLabel;
    private final String medicationGuidanceActionLabel;
    private final String reportSummary;
    private final String reportTreatment;
    private final String reportMedicationName;
    private final String reportMedicationChangeSummary;
    private final String reportMedicationScheduleNote;
    @Nullable
    private final MedicationComparisonDecision reportMedicationComparisonDecision;
    private final String reportMedicationComparisonNote;
    private final String nextVisitAt;
    private final String advanceButtonLabel;
    private final ManagerGuidePrimaryAction primaryAction;
    private final boolean advanceEnabled;
    private final String reportButtonLabel;
    private final boolean liveLocationSharingActive;
    private final boolean inputsEnabled;

    public ManagerGuideScreenModel(
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String heroNote,
            String meetingPatient,
            String meetingPlace,
            String meetingDepartment,
            List<ManagerGuideMapActionModel> mapActions,
            HospitalMapPreviewModel hospitalMapPreviewModel,
            List<ManagerGuideStageModel> stages,
            ManagerGuideFocusModel focusModel,
            ManagerGuideSectionVisibility sectionVisibility,
            List<ManagerGuideMemoItem> memoSummaryItems,
            String currentStepCode,
            String presentationStepCode,
            String liveLocationStatus,
            String liveLocationHistory,
            String locationSummary,
            String guardianUpdate,
            String fieldPhotoNote,
            boolean preConsultationConfirmed,
            String medicationNote,
            String pharmacySummary,
            String pharmacyProgressSummary,
            String prescriptionActionLabel,
            String pharmacyActionLabel,
            String medicationGuidanceActionLabel,
            String reportSummary,
            String reportTreatment,
            String reportMedicationName,
            String reportMedicationChangeSummary,
            String reportMedicationScheduleNote,
            @Nullable MedicationComparisonDecision reportMedicationComparisonDecision,
            String reportMedicationComparisonNote,
            String nextVisitAt,
            String advanceButtonLabel,
            ManagerGuidePrimaryAction primaryAction,
            boolean advanceEnabled,
            String reportButtonLabel,
            boolean liveLocationSharingActive,
            boolean inputsEnabled
    ) {
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.heroNote = heroNote;
        this.meetingPatient = meetingPatient;
        this.meetingPlace = meetingPlace;
        this.meetingDepartment = meetingDepartment;
        this.mapActions = mapActions;
        this.hospitalMapPreviewModel = hospitalMapPreviewModel;
        this.stages = stages;
        this.focusModel = focusModel;
        this.sectionVisibility = sectionVisibility;
        this.memoSummaryItems = memoSummaryItems;
        this.currentStepCode = currentStepCode;
        this.presentationStepCode = presentationStepCode;
        this.liveLocationStatus = liveLocationStatus;
        this.liveLocationHistory = liveLocationHistory;
        this.locationSummary = locationSummary;
        this.guardianUpdate = guardianUpdate;
        this.fieldPhotoNote = fieldPhotoNote;
        this.preConsultationConfirmed = preConsultationConfirmed;
        this.medicationNote = medicationNote;
        this.pharmacySummary = pharmacySummary;
        this.pharmacyProgressSummary = pharmacyProgressSummary;
        this.prescriptionActionLabel = prescriptionActionLabel;
        this.pharmacyActionLabel = pharmacyActionLabel;
        this.medicationGuidanceActionLabel = medicationGuidanceActionLabel;
        this.reportSummary = reportSummary;
        this.reportTreatment = reportTreatment;
        this.reportMedicationName = reportMedicationName;
        this.reportMedicationChangeSummary = reportMedicationChangeSummary;
        this.reportMedicationScheduleNote = reportMedicationScheduleNote;
        this.reportMedicationComparisonDecision = reportMedicationComparisonDecision;
        this.reportMedicationComparisonNote = reportMedicationComparisonNote;
        this.nextVisitAt = nextVisitAt;
        this.advanceButtonLabel = advanceButtonLabel;
        this.primaryAction = primaryAction;
        this.advanceEnabled = advanceEnabled;
        this.reportButtonLabel = reportButtonLabel;
        this.liveLocationSharingActive = liveLocationSharingActive;
        this.inputsEnabled = inputsEnabled;
    }

    public String getModeLabel() {
        return modeLabel;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroBadge() {
        return heroBadge;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public String getHeroBody() {
        return heroBody;
    }

    public String getHeroNote() {
        return heroNote;
    }

    public String getMeetingPatient() {
        return meetingPatient;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getMeetingDepartment() {
        return meetingDepartment;
    }

    public boolean isMeetingStep() {
        return ManagerGuideCurrentLocationPolicy.isAvailableFor(presentationStepCode);
    }

    public List<ManagerGuideMapActionModel> getMapActions() {
        return mapActions;
    }

    public HospitalMapPreviewModel getHospitalMapPreviewModel() {
        return hospitalMapPreviewModel;
    }

    public List<ManagerGuideStageModel> getStages() {
        return stages;
    }

    public ManagerGuideFocusModel getFocusModel() {
        return focusModel;
    }

    ManagerGuideSectionVisibility getSectionVisibility() {
        return sectionVisibility;
    }

    public List<ManagerGuideMemoItem> getMemoSummaryItems() {
        return memoSummaryItems;
    }

    String getCurrentStepCode() {
        return currentStepCode;
    }

    String getPresentationStepCode() {
        return presentationStepCode;
    }

    public String getLiveLocationStatus() {
        return liveLocationStatus;
    }

    public String getLiveLocationHistory() {
        return liveLocationHistory;
    }

    public String getLocationSummary() {
        return locationSummary;
    }

    public String getGuardianUpdate() {
        return guardianUpdate;
    }

    public String getFieldPhotoNote() {
        return fieldPhotoNote;
    }

    public boolean isPreConsultationConfirmed() {
        return preConsultationConfirmed;
    }

    public String getMedicationNote() {
        return medicationNote;
    }

    public String getPharmacySummary() {
        return pharmacySummary;
    }

    public String getPharmacyProgressSummary() {
        return pharmacyProgressSummary;
    }

    public String getPrescriptionActionLabel() {
        return prescriptionActionLabel;
    }

    public String getPharmacyActionLabel() {
        return pharmacyActionLabel;
    }

    public String getMedicationGuidanceActionLabel() {
        return medicationGuidanceActionLabel;
    }

    public String getReportSummary() {
        return reportSummary;
    }

    public String getReportTreatment() {
        return reportTreatment;
    }

    public String getReportMedicationName() {
        return reportMedicationName;
    }

    public String getReportMedicationChangeSummary() {
        return reportMedicationChangeSummary;
    }

    public String getReportMedicationScheduleNote() {
        return reportMedicationScheduleNote;
    }

    @Nullable
    public MedicationComparisonDecision getReportMedicationComparisonDecision() {
        return reportMedicationComparisonDecision;
    }

    public String getReportMedicationComparisonNote() {
        return reportMedicationComparisonNote;
    }

    public String getNextVisitAt() {
        return nextVisitAt;
    }

    public String getAdvanceButtonLabel() {
        return advanceButtonLabel;
    }

    ManagerGuidePrimaryAction getPrimaryAction() {
        return primaryAction;
    }

    public boolean isAdvanceEnabled() {
        return advanceEnabled;
    }

    public String getReportButtonLabel() {
        return reportButtonLabel;
    }

    public boolean isLiveLocationSharingActive() {
        return liveLocationSharingActive;
    }

    public boolean isInputsEnabled() {
        return inputsEnabled;
    }
}
