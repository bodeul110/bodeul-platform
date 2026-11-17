package com.example.bodeul.ui.manager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 서류 등록 화면 전체를 그리기 위한 표시 전용 모델이다.
 */
public final class ManagerDocumentRegistrationScreenModel {
    private final String modeText;
    private final String statusBadgeText;
    private final String statusTitleText;
    private final String statusBodyText;
    private final List<ManagerDocumentRegistrationItemModel> documentItems;
    private final boolean reviewCardVisible;
    private final String reviewTitleText;
    private final String reviewBodyText;
    private final String requestButtonText;
    private final boolean requestButtonEnabled;
    private final ManagerDocumentDisplayState displayState;

    public ManagerDocumentRegistrationScreenModel(
            String modeText,
            String statusBadgeText,
            String statusTitleText,
            String statusBodyText,
            List<ManagerDocumentRegistrationItemModel> documentItems,
            boolean reviewCardVisible,
            String reviewTitleText,
            String reviewBodyText,
            String requestButtonText,
            boolean requestButtonEnabled,
            ManagerDocumentDisplayState displayState
    ) {
        this.modeText = modeText;
        this.statusBadgeText = statusBadgeText;
        this.statusTitleText = statusTitleText;
        this.statusBodyText = statusBodyText;
        this.documentItems = documentItems == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(documentItems));
        this.reviewCardVisible = reviewCardVisible;
        this.reviewTitleText = reviewTitleText;
        this.reviewBodyText = reviewBodyText;
        this.requestButtonText = requestButtonText;
        this.requestButtonEnabled = requestButtonEnabled;
        this.displayState = displayState;
    }

    public String getModeText() {
        return modeText;
    }

    public ManagerDocumentDisplayState getDisplayState() { return displayState; }

    public String getStatusBadgeText() {
        return statusBadgeText;
    }

    public String getStatusTitleText() {
        return statusTitleText;
    }

    public String getStatusBodyText() {
        return statusBodyText;
    }

    public List<ManagerDocumentRegistrationItemModel> getDocumentItems() {
        return documentItems;
    }

    public boolean isReviewCardVisible() {
        return reviewCardVisible;
    }

    public String getReviewTitleText() {
        return reviewTitleText;
    }

    public String getReviewBodyText() {
        return reviewBodyText;
    }

    public String getRequestButtonText() {
        return requestButtonText;
    }

    public boolean isRequestButtonEnabled() {
        return requestButtonEnabled;
    }
}
