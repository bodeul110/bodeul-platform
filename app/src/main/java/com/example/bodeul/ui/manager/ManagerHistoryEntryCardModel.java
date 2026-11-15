package com.example.bodeul.ui.manager;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 과거 동행 이력 카드 한 장을 표현하는 모델이다.
 */
public final class ManagerHistoryEntryCardModel {
    @Nullable
    private final String reportRequestId;
    private final ManagerHistoryBadgeModel statusBadge;
    @Nullable
    private final ManagerHistoryBadgeModel followUpBadge;
    private final String titleText;
    private final String subtitleText;
    private final String summaryText;
    private final String activityText;
    private final List<ManagerInfoLineItem> detailLines;

    public ManagerHistoryEntryCardModel(
            @Nullable String reportRequestId,
            ManagerHistoryBadgeModel statusBadge,
            @Nullable ManagerHistoryBadgeModel followUpBadge,
            String titleText,
            String subtitleText,
            String summaryText,
            String activityText,
            List<ManagerInfoLineItem> detailLines
    ) {
        this.reportRequestId = reportRequestId;
        this.statusBadge = statusBadge;
        this.followUpBadge = followUpBadge;
        this.titleText = titleText;
        this.subtitleText = subtitleText;
        this.summaryText = summaryText;
        this.activityText = activityText;
        this.detailLines = detailLines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(detailLines));
    }

    public ManagerHistoryBadgeModel getStatusBadge() {
        return statusBadge;
    }

    @Nullable
    public String getReportRequestId() { return reportRequestId; }

    @Nullable
    public ManagerHistoryBadgeModel getFollowUpBadge() {
        return followUpBadge;
    }

    public String getTitleText() {
        return titleText;
    }

    public String getSubtitleText() {
        return subtitleText;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public String getActivityText() {
        return activityText;
    }

    public List<ManagerInfoLineItem> getDetailLines() {
        return detailLines;
    }
}
