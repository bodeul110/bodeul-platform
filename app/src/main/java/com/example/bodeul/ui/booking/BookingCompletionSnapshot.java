package com.example.bodeul.ui.booking;

import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.bodeul.domain.model.AppointmentRequest;

/**
 * 예약 완료 화면에서 필요한 요청 스냅샷을 인텐트로 주고받는다.
 */
public final class BookingCompletionSnapshot {
    private static final String EXTRA_REQUEST_ID = "requestId";
    private static final String EXTRA_HOSPITAL_NAME = "hospitalName";
    private static final String EXTRA_HOSPITAL_LATITUDE = "hospitalLatitude";
    private static final String EXTRA_HOSPITAL_LONGITUDE = "hospitalLongitude";
    private static final String EXTRA_DEPARTMENT_NAME = "departmentName";
    private static final String EXTRA_APPOINTMENT_AT = "appointmentAt";
    private static final String EXTRA_MEETING_PLACE = "meetingPlace";
    private static final String EXTRA_SPECIAL_NOTES = "specialNotes";
    private static final String EXTRA_MOBILITY_SUPPORT_CODE = "mobilitySupportCode";
    private static final String EXTRA_TRIP_TYPE_CODE = "tripTypeCode";
    private static final String EXTRA_PAYMENT_METHOD_CODE = "paymentMethodCode";
    private static final String EXTRA_COUPON_CODE = "couponCode";
    private static final String EXTRA_FINAL_PRICE = "finalPrice";
    private static final String EXTRA_PAYMENT_STATUS_CODE = "paymentStatusCode";
    private static final String EXTRA_PAYMENT_APPROVAL_CODE = "paymentApprovalCode";
    private static final String EXTRA_PAYMENT_APPROVED_AT = "paymentApprovedAt";
    private static final String EXTRA_PAYMENT_PROVIDER_LABEL = "paymentProviderLabel";

    private final String requestId;
    private final String hospitalName;
    private final double hospitalLatitude;
    private final double hospitalLongitude;
    private final String departmentName;
    private final String appointmentAt;
    private final String meetingPlace;
    private final String specialNotes;
    private final String mobilitySupportCode;
    private final String tripTypeCode;
    private final String paymentMethodCode;
    private final String couponCode;
    private final int finalPrice;
    private final String paymentStatusCode;
    private final String paymentApprovalCode;
    private final String paymentApprovedAt;
    private final String paymentProviderLabel;

    public BookingCompletionSnapshot(
            String requestId,
            String hospitalName,
            double hospitalLatitude,
            double hospitalLongitude,
            String departmentName,
            String appointmentAt,
            String meetingPlace,
            String specialNotes,
            String mobilitySupportCode,
            String tripTypeCode,
            String paymentMethodCode,
            String couponCode,
            int finalPrice,
            String paymentStatusCode,
            String paymentApprovalCode,
            String paymentApprovedAt,
            String paymentProviderLabel
    ) {
        this.requestId = normalize(requestId);
        this.hospitalName = normalize(hospitalName);
        this.hospitalLatitude = hospitalLatitude;
        this.hospitalLongitude = hospitalLongitude;
        this.departmentName = normalize(departmentName);
        this.appointmentAt = normalize(appointmentAt);
        this.meetingPlace = normalize(meetingPlace);
        this.specialNotes = normalize(specialNotes);
        this.mobilitySupportCode = normalize(mobilitySupportCode);
        this.tripTypeCode = normalize(tripTypeCode);
        this.paymentMethodCode = normalize(paymentMethodCode);
        this.couponCode = normalize(couponCode);
        this.finalPrice = Math.max(finalPrice, 0);
        this.paymentStatusCode = normalize(paymentStatusCode);
        this.paymentApprovalCode = normalize(paymentApprovalCode);
        this.paymentApprovedAt = normalize(paymentApprovedAt);
        this.paymentProviderLabel = normalize(paymentProviderLabel);
    }

    public static BookingCompletionSnapshot fromRequest(AppointmentRequest request) {
        return new BookingCompletionSnapshot(
                request.getId(),
                request.getHospitalName(),
                request.getHospitalLatitude(),
                request.getHospitalLongitude(),
                request.getDepartmentName(),
                request.getAppointmentAt(),
                request.getMeetingPlace(),
                request.getSpecialNotes(),
                request.getMobilitySupportCode(),
                request.getTripTypeCode(),
                request.getPaymentMethodCode(),
                request.getCouponCode(),
                request.getFinalPrice(),
                request.getPaymentStatusCode(),
                request.getPaymentApprovalCode(),
                request.getPaymentApprovedAt(),
                request.getPaymentProviderLabel()
        );
    }

    public static BookingCompletionSnapshot fromIntent(@NonNull Intent intent) {
        return new BookingCompletionSnapshot(
                intent.getStringExtra(EXTRA_REQUEST_ID),
                intent.getStringExtra(EXTRA_HOSPITAL_NAME),
                intent.getDoubleExtra(EXTRA_HOSPITAL_LATITUDE, 0),
                intent.getDoubleExtra(EXTRA_HOSPITAL_LONGITUDE, 0),
                intent.getStringExtra(EXTRA_DEPARTMENT_NAME),
                intent.getStringExtra(EXTRA_APPOINTMENT_AT),
                intent.getStringExtra(EXTRA_MEETING_PLACE),
                intent.getStringExtra(EXTRA_SPECIAL_NOTES),
                intent.getStringExtra(EXTRA_MOBILITY_SUPPORT_CODE),
                intent.getStringExtra(EXTRA_TRIP_TYPE_CODE),
                intent.getStringExtra(EXTRA_PAYMENT_METHOD_CODE),
                intent.getStringExtra(EXTRA_COUPON_CODE),
                intent.getIntExtra(EXTRA_FINAL_PRICE, 0),
                intent.getStringExtra(EXTRA_PAYMENT_STATUS_CODE),
                intent.getStringExtra(EXTRA_PAYMENT_APPROVAL_CODE),
                intent.getStringExtra(EXTRA_PAYMENT_APPROVED_AT),
                intent.getStringExtra(EXTRA_PAYMENT_PROVIDER_LABEL)
        );
    }

    public void writeToIntent(Intent intent) {
        intent.putExtra(EXTRA_REQUEST_ID, requestId);
        intent.putExtra(EXTRA_HOSPITAL_NAME, hospitalName);
        intent.putExtra(EXTRA_HOSPITAL_LATITUDE, hospitalLatitude);
        intent.putExtra(EXTRA_HOSPITAL_LONGITUDE, hospitalLongitude);
        intent.putExtra(EXTRA_DEPARTMENT_NAME, departmentName);
        intent.putExtra(EXTRA_APPOINTMENT_AT, appointmentAt);
        intent.putExtra(EXTRA_MEETING_PLACE, meetingPlace);
        intent.putExtra(EXTRA_SPECIAL_NOTES, specialNotes);
        intent.putExtra(EXTRA_MOBILITY_SUPPORT_CODE, mobilitySupportCode);
        intent.putExtra(EXTRA_TRIP_TYPE_CODE, tripTypeCode);
        intent.putExtra(EXTRA_PAYMENT_METHOD_CODE, paymentMethodCode);
        intent.putExtra(EXTRA_COUPON_CODE, couponCode);
        intent.putExtra(EXTRA_FINAL_PRICE, finalPrice);
        intent.putExtra(EXTRA_PAYMENT_STATUS_CODE, paymentStatusCode);
        intent.putExtra(EXTRA_PAYMENT_APPROVAL_CODE, paymentApprovalCode);
        intent.putExtra(EXTRA_PAYMENT_APPROVED_AT, paymentApprovedAt);
        intent.putExtra(EXTRA_PAYMENT_PROVIDER_LABEL, paymentProviderLabel);
    }

    public String getRequestId() {
        return requestId;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public String getHospitalMapQuery() {
        if (Double.isFinite(hospitalLatitude) && Double.isFinite(hospitalLongitude)
                && Math.abs(hospitalLatitude) <= 90 && Math.abs(hospitalLongitude) <= 180
                && hospitalLatitude != 0 && hospitalLongitude != 0) {
            return hospitalLatitude + "," + hospitalLongitude + "(" + hospitalName + ")";
        }
        return hospitalName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public String getAppointmentAt() {
        return appointmentAt;
    }

    public String getMeetingPlace() {
        return meetingPlace;
    }

    public String getSpecialNotes() {
        return specialNotes;
    }

    public String getMobilitySupportCode() {
        return mobilitySupportCode;
    }

    public String getTripTypeCode() {
        return tripTypeCode;
    }

    public String getPaymentMethodCode() {
        return paymentMethodCode;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public int getFinalPrice() {
        return finalPrice;
    }

    public String getPaymentStatusCode() {
        return paymentStatusCode;
    }

    public String getPaymentApprovalCode() {
        return paymentApprovalCode;
    }

    public String getPaymentApprovedAt() {
        return paymentApprovedAt;
    }

    public String getPaymentProviderLabel() {
        return paymentProviderLabel;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
