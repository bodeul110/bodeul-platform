package com.example.bodeul.ui.report;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentStatus;

import java.util.List;

/**
 * 보호자 요청별 진행 카드에 필요한 전체 정보를 묶는다.
 */
public final class GuardianReportEntryCardModel {
    @Nullable
    private final String requestId;
    private final AppointmentStatus status;
    private final boolean finalReportReady;
    private final String titleText;
    private final String heroBodyText;
    private final String appointmentDateText;
    private final String hospitalNameText;
    private final String departmentNameText;
    @Nullable
    private final String patientConditionText;
    @Nullable
    private final String managerNameText;
    @Nullable
    private final String managerMessageLabelText;
    @Nullable
    private final String managerMessageText;
    private final String liveSectionTitleText;
    private final List<GuardianReportLineItem> liveLines;
    private final String historySectionTitleText;
    private final List<GuardianReportLineItem> historyLines;
    private final String memoSectionTitleText;
    private final List<GuardianReportLineItem> memoLines;
    private final String reportSectionTitleText;
    private final List<GuardianReportSectionModel> reportSections;
    @Nullable
    private final String pendingReportText;
    private final String actionLabelText;

    public GuardianReportEntryCardModel(
            @Nullable String requestId,
            AppointmentStatus status,
            boolean finalReportReady,
            String titleText,
            String heroBodyText,
            String appointmentDateText,
            String hospitalNameText,
            String departmentNameText,
            @Nullable String patientConditionText,
            @Nullable String managerNameText,
            @Nullable String managerMessageLabelText,
            @Nullable String managerMessageText,
            String liveSectionTitleText,
            List<GuardianReportLineItem> liveLines,
            String historySectionTitleText,
            List<GuardianReportLineItem> historyLines,
            String memoSectionTitleText,
            List<GuardianReportLineItem> memoLines,
            String reportSectionTitleText,
            List<GuardianReportSectionModel> reportSections,
            @Nullable String pendingReportText,
            String actionLabelText
    ) {
        this.requestId = requestId;
        this.status = status;
        this.finalReportReady = finalReportReady;
        this.titleText = titleText;
        this.heroBodyText = heroBodyText;
        this.appointmentDateText = appointmentDateText;
        this.hospitalNameText = hospitalNameText;
        this.departmentNameText = departmentNameText;
        this.patientConditionText = patientConditionText;
        this.managerNameText = managerNameText;
        this.managerMessageLabelText = managerMessageLabelText;
        this.managerMessageText = managerMessageText;
        this.liveSectionTitleText = liveSectionTitleText;
        this.liveLines = liveLines;
        this.historySectionTitleText = historySectionTitleText;
        this.historyLines = historyLines;
        this.memoSectionTitleText = memoSectionTitleText;
        this.memoLines = memoLines;
        this.reportSectionTitleText = reportSectionTitleText;
        this.reportSections = reportSections;
        this.pendingReportText = pendingReportText;
        this.actionLabelText = actionLabelText;
    }

    @Nullable
    public String getRequestId() {
        return requestId;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public boolean isFinalReportReady() {
        return finalReportReady;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getAppointmentDateText() {
        return appointmentDateText;
    }

    public String getHospitalNameText() {
        return hospitalNameText;
    }

    public String getDepartmentNameText() {
        return departmentNameText;
    }

    @Nullable
    public String getPatientConditionText() {
        return patientConditionText;
    }

    @Nullable
    public String getManagerNameText() {
        return managerNameText;
    }

    @Nullable
    public String getManagerMessageLabelText() {
        return managerMessageLabelText;
    }

    @Nullable
    public String getManagerMessageText() {
        return managerMessageText;
    }

    public String getLiveSectionTitleText() {
        return liveSectionTitleText;
    }

    public List<GuardianReportLineItem> getLiveLines() {
        return liveLines;
    }

    public String getHistorySectionTitleText() {
        return historySectionTitleText;
    }

    public List<GuardianReportLineItem> getHistoryLines() {
        return historyLines;
    }

    public String getMemoSectionTitleText() {
        return memoSectionTitleText;
    }

    public List<GuardianReportLineItem> getMemoLines() {
        return memoLines;
    }

    public String getReportSectionTitleText() {
        return reportSectionTitleText;
    }

    public List<GuardianReportSectionModel> getReportSections() {
        return reportSections;
    }

    @Nullable
    public String getPendingReportText() {
        return pendingReportText;
    }

    public String getActionLabelText() {
        return actionLabelText;
    }
}
