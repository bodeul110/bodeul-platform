package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.google.android.material.button.MaterialButton;

/**
 * Figma 예약 메인 화면에서 선택 결과를 짧은 카드 요약으로 표현한다.
 */
final class BookingMainScreenBinder {
    private final Context context;
    private final MaterialButton buttonHospitalSearch;
    private final TextView textVisitDate;
    private final TextView textVisitTime;

    BookingMainScreenBinder(
            Context context,
            MaterialButton buttonHospitalSearch,
            TextView textVisitDate,
            TextView textVisitTime
    ) {
        this.context = context;
        this.buttonHospitalSearch = buttonHospitalSearch;
        this.textVisitDate = textVisitDate;
        this.textVisitTime = textVisitTime;
        bindHospitalSelection(new BookingHospitalSelection("", "", 0.0, 0.0));
        bindAppointment("");
    }

    void bindHospitalSelection(@NonNull BookingHospitalSelection selection) {
        if (!selection.isComplete()) {
            buttonHospitalSearch.setText(R.string.booking_main_hospital_search);
            bindHospitalSearchColor(R.color.figma_mvp_text_hint);
            return;
        }
        buttonHospitalSearch.setText(context.getString(
                R.string.booking_main_hospital_selected_format,
                selection.getHospitalName(),
                selection.getDepartmentName()
        ));
        bindHospitalSearchColor(R.color.figma_mvp_primary);
    }

    void bindAppointment(String appointmentAt) {
        BookingMainScreenFormatter.DisplayValue displayValue =
                BookingMainScreenFormatter.formatAppointment(appointmentAt);
        textVisitDate.setText(displayValue.isEmpty()
                ? context.getString(R.string.booking_main_visit_date_empty)
                : displayValue.getDateText());
        textVisitTime.setText(displayValue.isEmpty()
                ? context.getString(R.string.booking_main_visit_time_empty)
                : displayValue.getTimeText());
        textVisitDate.setSelected(!displayValue.isEmpty());
        textVisitTime.setSelected(!displayValue.isEmpty());
        int textColor = ContextCompat.getColor(
                context,
                displayValue.isEmpty()
                        ? R.color.figma_mvp_text_tertiary
                        : R.color.figma_mvp_text_primary
        );
        textVisitDate.setTextColor(textColor);
        textVisitTime.setTextColor(textColor);
    }

    private void bindHospitalSearchColor(int colorResId) {
        int color = ContextCompat.getColor(context, colorResId);
        buttonHospitalSearch.setTextColor(color);
        buttonHospitalSearch.setIconTint(ColorStateList.valueOf(color));
    }
}
