package com.example.bodeul.ui.booking;

/**
 * 예약 이력 화면에서 요청 카드 한 건을 읽기 전용으로 표현한다.
 */
public final class ClientBookingHistoryEntryModel {
    private final String requestId;
    private final String statusLabel;
    private final int statusBackgroundColorResId;
    private final int statusTextColorResId;
    private final int strokeColorResId;
    private final String title;
    private final String detail;
    private final String place;
    private final String linked;
    private final String option;
    private final String profile;
    private final String price;
    private final String note;
    private final boolean completed;
    private final boolean reviewAllowed;

    public ClientBookingHistoryEntryModel(
            String requestId,
            String statusLabel,
            int statusBackgroundColorResId,
            int statusTextColorResId,
            int strokeColorResId,
            String title,
            String detail,
            String place,
            String linked,
            String option,
            String profile,
            String price,
            String note,
            boolean completed,
            boolean reviewAllowed
    ) {
        this.requestId = requestId;
        this.statusLabel = statusLabel;
        this.statusBackgroundColorResId = statusBackgroundColorResId;
        this.statusTextColorResId = statusTextColorResId;
        this.strokeColorResId = strokeColorResId;
        this.title = title;
        this.detail = detail;
        this.place = place;
        this.linked = linked;
        this.option = option;
        this.profile = profile;
        this.price = price;
        this.note = note;
        this.completed = completed;
        this.reviewAllowed = reviewAllowed;
    }

    public String getRequestId() { return requestId; }
    public String getStatusLabel() { return statusLabel; }
    public int getStatusBackgroundColorResId() { return statusBackgroundColorResId; }
    public int getStatusTextColorResId() { return statusTextColorResId; }
    public int getStrokeColorResId() { return strokeColorResId; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getPlace() { return place; }
    public String getLinked() { return linked; }
    public String getOption() { return option; }
    public String getProfile() { return profile; }
    public String getPrice() { return price; }
    public String getNote() { return note; }
    public boolean isCompleted() { return completed; }
    public boolean isReviewAllowed() { return reviewAllowed; }
}
