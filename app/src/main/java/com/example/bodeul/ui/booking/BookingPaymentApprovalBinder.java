package com.example.bodeul.ui.booking;

import android.widget.CheckBox;
import android.widget.Button;
import android.widget.TextView;

/**
 * 결제 승인 화면의 정적 요약 정보를 바인딩한다.
 */
public final class BookingPaymentApprovalBinder {
    private final TextView textBadge;
    private final TextView textTitle;
    private final TextView textBody;
    private final TextView textPaymentMethod;
    private final TextView textCoupon;
    private final TextView textAmount;
    private final TextView textSchedule;
    private final TextView textHospital;
    private final TextView textMeetingPlace;
    private final CheckBox checkConsent;
    private final Button buttonApprove;

    public BookingPaymentApprovalBinder(
            TextView textBadge,
            TextView textTitle,
            TextView textBody,
            TextView textPaymentMethod,
            TextView textCoupon,
            TextView textAmount,
            TextView textSchedule,
            TextView textHospital,
            TextView textMeetingPlace,
            CheckBox checkConsent,
            Button buttonApprove
    ) {
        this.textBadge = textBadge;
        this.textTitle = textTitle;
        this.textBody = textBody;
        this.textPaymentMethod = textPaymentMethod;
        this.textCoupon = textCoupon;
        this.textAmount = textAmount;
        this.textSchedule = textSchedule;
        this.textHospital = textHospital;
        this.textMeetingPlace = textMeetingPlace;
        this.checkConsent = checkConsent;
        this.buttonApprove = buttonApprove;
    }

    public void bind(BookingPaymentApprovalScreenModel screenModel) {
        textBadge.setText(screenModel.getBadge());
        textTitle.setText(screenModel.getTitle());
        textBody.setText(screenModel.getBody());
        textPaymentMethod.setText(screenModel.getPaymentMethod());
        textCoupon.setText(screenModel.getCoupon());
        textAmount.setText(screenModel.getAmount());
        textSchedule.setText(screenModel.getSchedule());
        textHospital.setText(screenModel.getHospital());
        textMeetingPlace.setText(screenModel.getMeetingPlace());
        checkConsent.setText(screenModel.getConsentText());
        buttonApprove.setText(screenModel.getApprovalButtonText());
        checkConsent.setChecked(false);
        checkConsent.setEnabled(screenModel.isConfirmable());
        // 상태 복원으로 동의 값이 돌아오는 경우에도 버튼과 동일하게 반영한다.
        checkConsent.setOnCheckedChangeListener((button, checked) ->
                buttonApprove.setEnabled(screenModel.isConfirmable() && checked));
        buttonApprove.setEnabled(false);
    }
}
