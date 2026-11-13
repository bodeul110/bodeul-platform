package com.example.bodeul.ui.booking;

import android.view.View;
import android.widget.TextView;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;

/** 접수된 예약의 값만 완료 시트에 표시한다. */
public final class BookingCompletionBinder {
    private final View root;

    public BookingCompletionBinder(View root) {
        this.root = root;
    }

    public void bind(BookingCompletionScreenModel model) {
        ViewCompat.setAccessibilityHeading(root.findViewById(R.id.textBookingCompletionTitle), true);
        ViewCompat.setAccessibilityHeading(root.findViewById(R.id.textBookingCompletionSummaryTitle), true);
        ViewCompat.setAccessibilityHeading(root.findViewById(R.id.textBookingCompletionPaymentTitle), true);
        text(R.id.textBookingCompletionTitle, model.getTitle());
        text(R.id.textBookingCompletionBody, model.getBody());
        text(R.id.textBookingCompletionHospital, model.getHospital());
        text(R.id.textBookingCompletionDepartment, model.getDepartment());
        text(R.id.textBookingCompletionDate, model.getDate());
        text(R.id.textBookingCompletionTime, model.getTime());
        root.findViewById(R.id.textBookingCompletionTime).setVisibility(
                model.getTime().isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.textBookingCompletionMeetingPlace, model.getMeetingPlace());
        text(R.id.textBookingCompletionNote, model.getNote());
        text(R.id.textBookingCompletionPayment, model.getPayment());
        text(R.id.textBookingCompletionAmount, model.getAmount());
    }

    private void text(int id, String value) {
        ((TextView) root.findViewById(id)).setText(value);
    }
}
