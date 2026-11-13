package com.example.bodeul.ui.booking;

/**
 * 예약 완료 화면에 표시할 Figma 기준 정보 묶음이다.
 */
public final class BookingCompletionScreenModel {
    private final String screenTitle;
    private final String title;
    private final String body;
    private final String hospital;
    private final String department;
    private final String date;
    private final String time;
    private final String note;
    private final String meetingPlace;
    private final String payment;
    private final String amount;

    public BookingCompletionScreenModel(
            String screenTitle,
            String title,
            String body,
            String hospital,
            String department,
            String date,
            String time,
            String note,
            String meetingPlace,
            String payment,
            String amount
    ) {
        this.screenTitle = screenTitle;
        this.title = title;
        this.body = body;
        this.hospital = hospital;
        this.department = department;
        this.date = date;
        this.time = time;
        this.note = note;
        this.meetingPlace = meetingPlace;
        this.payment = payment;
        this.amount = amount;
    }

    public String getScreenTitle() {
        return screenTitle;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getHospital() {
        return hospital;
    }

    public String getDepartment() {
        return department;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getNote() {
        return note;
    }

    public String getMeetingPlace() { return meetingPlace; }
    public String getPayment() { return payment; }
    public String getAmount() { return amount; }
}
