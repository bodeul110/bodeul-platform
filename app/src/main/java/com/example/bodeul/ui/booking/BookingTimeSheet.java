package com.example.bodeul.ui.booking;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.bodeul.R;
import java.util.Locale;

/** 네이티브 시간 휠의 미확정 값을 복원하며 확인할 때만 예약 선택기에 전달한다. */
public final class BookingTimeSheet extends BookingSelectorSheet {
    static final String TAG = "bookingTime";
    static final String RESULT = "bookingTimeSelection";
    private int hour;
    private int minute;

    static BookingTimeSheet create(int hour, int minute) {
        BookingTimeSheet sheet = new BookingTimeSheet();
        Bundle args = new Bundle();
        args.putInt("hour", hour);
        args.putInt("minute", minute);
        sheet.setArguments(args);
        return sheet;
    }

    static String formatTime(Context context, int hour, int minute) {
        return context.getString(R.string.final_schedule_time_value,
                context.getString(hour < 12 ? R.string.final_schedule_am : R.string.final_schedule_pm),
                hour % 12 == 0 ? 12 : hour % 12, minute);
    }

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        Bundle values = state == null ? requireArguments() : state;
        hour = values.getInt("hour");
        minute = values.getInt("minute");
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.sheet_booking_time, container, false);
    }

    @Override public void onViewCreated(@NonNull View root, @Nullable Bundle state) {
        NumberPicker periodPicker = root.findViewById(R.id.pickerBookingPeriod);
        NumberPicker hourPicker = root.findViewById(R.id.pickerBookingHour);
        NumberPicker minutePicker = root.findViewById(R.id.pickerBookingMinute);
        configure(periodPicker, 0, 1, hour / 12);
        periodPicker.setDisplayedValues(new String[]{getString(R.string.final_schedule_am), getString(R.string.final_schedule_pm)});
        configure(hourPicker, 1, 12, hour % 12 == 0 ? 12 : hour % 12);
        // 기존 10:17 같은 예약을 5분 단위로 반올림하지 않는다.
        configure(minutePicker, 0, 59, minute);
        hourPicker.setFormatter(value -> String.format(Locale.KOREA, "%02d", value));
        minutePicker.setFormatter(value -> String.format(Locale.KOREA, "%02d", value));
        NumberPicker.OnValueChangeListener change = (picker, oldValue, newValue) -> {
            hour = periodPicker.getValue() * 12 + hourPicker.getValue() % 12;
            minute = minutePicker.getValue();
            render(root);
        };
        periodPicker.setOnValueChangedListener(change);
        hourPicker.setOnValueChangedListener(change);
        minutePicker.setOnValueChangedListener(change);
        root.findViewById(R.id.buttonCloseBookingTime).setOnClickListener(v -> dismiss());
        root.findViewById(R.id.buttonConfirmBookingTime).setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putInt("hour", hour);
            result.putInt("minute", minute);
            getParentFragmentManager().setFragmentResult(RESULT, result);
            dismiss();
        });
        render(root);
    }

    private void configure(NumberPicker picker, int min, int max, int value) {
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            picker.setTextSize(18 * getResources().getDisplayMetrics().scaledDensity);
            picker.setTextColor(ContextCompat.getColor(requireContext(), R.color.final_text_primary));
            picker.setSelectionDividerHeight(0);
        }
    }

    private void render(View root) {
        ((TextView) root.findViewById(R.id.buttonConfirmBookingTime)).setText(
                getString(R.string.final_schedule_time_done, formatTime(requireContext(), hour, minute)));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt("hour", hour);
        outState.putInt("minute", minute);
        super.onSaveInstanceState(outState);
    }

    @Override protected int preferredHeightDp() { return 430; }
}
