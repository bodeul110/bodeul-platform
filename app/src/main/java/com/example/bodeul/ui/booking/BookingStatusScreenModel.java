package com.example.bodeul.ui.booking;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;

import java.util.Collections;
import java.util.List;

/**
 * 예약 상세 화면에 필요한 표시 데이터를 한 번에 전달한다.
 */
public final class BookingStatusScreenModel {
    private final AppointmentStatus status;
    private final String modeText;
    private final String heroBadgeText;
    private final String heroTitleText;
    private final String heroBodyText;
    private final String progressTitleText;
    private final String progressBodyText;
    private final AppointmentProgressOverviewModel progressOverview;
    private final List<BookingStatusLineItem> participantLines;
    private final List<BookingStatusLineItem> summaryLines;
    private final List<BookingStatusLineItem> paymentLines;
    private final String referenceText;
    private final String managerName;
    private final String managerContact;
    private final String managerNote;
    private final boolean awaitingDeposit;
    private final List<BookingStatusLineItem> liveLines;
    private final List<BookingStatusSectionModel> reportSections;
    @Nullable
    private final BookingStatusActionModel primaryAction;
    @Nullable
    private final BookingStatusActionModel secondaryAction;

    public BookingStatusScreenModel(
            AppointmentStatus status,
            String modeText,
            String heroBadgeText,
            String heroTitleText,
            String heroBodyText,
            String progressTitleText,
            String progressBodyText,
            AppointmentProgressOverviewModel progressOverview,
            List<BookingStatusLineItem> participantLines,
            List<BookingStatusLineItem> summaryLines,
            List<BookingStatusLineItem> paymentLines,
            String referenceText,
            String managerName,
            String managerContact,
            String managerNote,
            boolean awaitingDeposit,
            List<BookingStatusLineItem> liveLines,
            List<BookingStatusSectionModel> reportSections,
            @Nullable BookingStatusActionModel primaryAction,
            @Nullable BookingStatusActionModel secondaryAction
    ) {
        this.status = status;
        this.modeText = modeText;
        this.heroBadgeText = heroBadgeText;
        this.heroTitleText = heroTitleText == null ? "" : heroTitleText;
        this.heroBodyText = heroBodyText == null ? "" : heroBodyText;
        this.progressTitleText = progressTitleText;
        this.progressBodyText = progressBodyText;
        this.progressOverview = progressOverview;
        this.participantLines = Collections.unmodifiableList(participantLines);
        this.summaryLines = Collections.unmodifiableList(summaryLines);
        this.paymentLines = Collections.unmodifiableList(paymentLines);
        this.referenceText = referenceText;
        this.managerName = managerName;
        this.managerContact = managerContact;
        this.managerNote = managerNote;
        this.awaitingDeposit = awaitingDeposit;
        this.liveLines = Collections.unmodifiableList(liveLines);
        this.reportSections = Collections.unmodifiableList(reportSections);
        this.primaryAction = primaryAction;
        this.secondaryAction = secondaryAction;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getModeText() {
        return modeText;
    }

    public String getHeroBadgeText() {
        return heroBadgeText;
    }

    public String getHeroTitleText() {
        return heroTitleText;
    }

    public String getHeroBodyText() {
        return heroBodyText;
    }

    public String getProgressTitleText() {
        return progressTitleText;
    }

    public String getProgressBodyText() {
        return progressBodyText;
    }

    public AppointmentProgressOverviewModel getProgressOverview() {
        return progressOverview;
    }

    public List<BookingStatusLineItem> getParticipantLines() {
        return participantLines;
    }

    public List<BookingStatusLineItem> getSummaryLines() {
        return summaryLines;
    }

    public List<BookingStatusLineItem> getPaymentLines() { return paymentLines; }
    public String getReferenceText() { return referenceText; }
    public String getManagerName() { return managerName; }
    public String getManagerContact() { return managerContact; }
    public String getManagerNote() { return managerNote; }
    public boolean isAwaitingDeposit() { return awaitingDeposit; }

    public List<BookingStatusLineItem> getLiveLines() {
        return liveLines;
    }

    public List<BookingStatusSectionModel> getReportSections() {
        return reportSections;
    }

    public boolean hasLiveLines() {
        return !liveLines.isEmpty();
    }

    @Nullable
    public BookingStatusActionModel getPrimaryAction() {
        return primaryAction;
    }

    @Nullable
    public BookingStatusActionModel getSecondaryAction() {
        return secondaryAction;
    }

    public boolean hasReportLines() {
        return !reportSections.isEmpty();
    }
}
