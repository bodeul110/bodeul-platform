package com.example.bodeul.ui.booking;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 방문 날짜와 시간 선택 로직을 화면 밖으로 분리한다.
 */
public final class BookingAppointmentSelector {
    public interface OnAppointmentChangedListener {
        void onAppointmentChanged(String appointmentAt);
    }

    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";

    private final AppCompatActivity activity;
    private final TextInputLayout layoutAppointmentAt;
    private final TextInputEditText inputAppointmentAt;
    private final View appointmentSummaryAction;
    private final TextView textAppointmentError;
    private final MaterialButton buttonQuickToday;
    private final MaterialButton buttonQuickTomorrow;
    private final MaterialButton buttonQuickDayAfterTomorrow;
    private final MaterialButton buttonQuickMorning;
    private final MaterialButton buttonQuickAfternoon;
    private final MaterialButton buttonQuickLateAfternoon;
    private final ActivityResultLauncher<Intent> appointmentSelectorLauncher;
    private final Runnable beforeOpenListener;
    @Nullable
    private OnAppointmentChangedListener appointmentChangedListener;

    public BookingAppointmentSelector(
            AppCompatActivity activity,
            TextInputLayout layoutAppointmentAt,
            TextInputEditText inputAppointmentAt,
            View appointmentSummaryAction,
            TextView textAppointmentError,
            MaterialButton buttonQuickToday,
            MaterialButton buttonQuickTomorrow,
            MaterialButton buttonQuickDayAfterTomorrow,
            MaterialButton buttonQuickMorning,
            MaterialButton buttonQuickAfternoon,
            MaterialButton buttonQuickLateAfternoon,
            ActivityResultLauncher<Intent> appointmentSelectorLauncher,
            Runnable beforeOpenListener
    ) {
        this.activity = activity;
        this.layoutAppointmentAt = layoutAppointmentAt;
        this.inputAppointmentAt = inputAppointmentAt;
        this.appointmentSummaryAction = appointmentSummaryAction;
        this.textAppointmentError = textAppointmentError;
        this.buttonQuickToday = buttonQuickToday;
        this.buttonQuickTomorrow = buttonQuickTomorrow;
        this.buttonQuickDayAfterTomorrow = buttonQuickDayAfterTomorrow;
        this.buttonQuickMorning = buttonQuickMorning;
        this.buttonQuickAfternoon = buttonQuickAfternoon;
        this.buttonQuickLateAfternoon = buttonQuickLateAfternoon;
        this.appointmentSelectorLauncher = appointmentSelectorLauncher;
        this.beforeOpenListener = beforeOpenListener;

        configureAppointmentPicker();
        configureQuickAppointmentButtons();
    }

    public String getAppointmentAt() {
        return inputAppointmentAt.getText() == null
                ? ""
                : inputAppointmentAt.getText().toString().trim();
    }

    public void setAppointmentAt(String appointmentAt) {
        inputAppointmentAt.setText(appointmentAt);
        layoutAppointmentAt.setError(null);
        textAppointmentError.setVisibility(View.GONE);
        refreshQuickAppointmentButtons();
        dispatchAppointmentChanged();
    }

    public void clear() {
        inputAppointmentAt.setText(null);
        layoutAppointmentAt.setError(null);
        textAppointmentError.setVisibility(View.GONE);
        refreshQuickAppointmentButtons();
        dispatchAppointmentChanged();
    }

    public void setOnAppointmentChangedListener(
            @Nullable OnAppointmentChangedListener appointmentChangedListener
    ) {
        this.appointmentChangedListener = appointmentChangedListener;
        dispatchAppointmentChanged();
    }

    public void setEnabled(boolean enabled) {
        inputAppointmentAt.setEnabled(enabled);
        appointmentSummaryAction.setEnabled(enabled);
        buttonQuickToday.setEnabled(enabled);
        buttonQuickTomorrow.setEnabled(enabled);
        buttonQuickDayAfterTomorrow.setEnabled(enabled);
        buttonQuickMorning.setEnabled(enabled);
        buttonQuickAfternoon.setEnabled(enabled);
        buttonQuickLateAfternoon.setEnabled(enabled);
    }

    public boolean validateRequiredAndFormat() {
        String appointmentAt = getAppointmentAt();
        if (TextUtils.isEmpty(appointmentAt)) {
            layoutAppointmentAt.setError(activity.getString(R.string.error_required_field));
            showAppointmentError(R.string.booking_main_visit_datetime_error);
            return false;
        }
        if (BookingAppointmentDateTime.parse(appointmentAt) == null) {
            layoutAppointmentAt.setError(activity.getString(R.string.error_booking_appointment_format));
            showAppointmentError(R.string.error_booking_appointment_format);
            return false;
        }
        layoutAppointmentAt.setError(null);
        textAppointmentError.setVisibility(View.GONE);
        return true;
    }

    private void configureAppointmentPicker() {
        inputAppointmentAt.setOnClickListener(view -> openAppointmentSelector());
        appointmentSummaryAction.setOnClickListener(view -> openAppointmentSelector());
        layoutAppointmentAt.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layoutAppointmentAt.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar);
        layoutAppointmentAt.setEndIconOnClickListener(view -> openAppointmentSelector());
        layoutAppointmentAt.setOnClickListener(view -> openAppointmentSelector());
    }

    private void configureQuickAppointmentButtons() {
        buttonQuickToday.setOnClickListener(view -> applyQuickAppointmentDate(0));
        buttonQuickTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(1));
        buttonQuickDayAfterTomorrow.setOnClickListener(view -> applyQuickAppointmentDate(2));
        buttonQuickMorning.setOnClickListener(view -> applyQuickAppointmentTime(10, 0));
        buttonQuickAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(14, 0));
        buttonQuickLateAfternoon.setOnClickListener(view -> applyQuickAppointmentTime(16, 0));
        refreshQuickAppointmentButtons();
    }

    private void openAppointmentSelector() {
        beforeOpenListener.run();
        appointmentSelectorLauncher.launch(BookingAppointmentSelectorActivity.createIntent(
                activity,
                getAppointmentAt()
        ));
    }

    private void applyQuickAppointmentDate(int dayOffset) {
        Calendar baseCalendar = resolveBaseAppointmentCalendar();
        Calendar targetCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        targetCalendar.add(Calendar.DAY_OF_MONTH, dayOffset);

        baseCalendar.set(Calendar.YEAR, targetCalendar.get(Calendar.YEAR));
        baseCalendar.set(Calendar.MONTH, targetCalendar.get(Calendar.MONTH));
        baseCalendar.set(Calendar.DAY_OF_MONTH, targetCalendar.get(Calendar.DAY_OF_MONTH));
        inputAppointmentAt.setText(formatAppointmentAt(baseCalendar.getTimeInMillis()));
        layoutAppointmentAt.setError(null);
        textAppointmentError.setVisibility(View.GONE);
        refreshQuickAppointmentButtons();
        dispatchAppointmentChanged();
    }

    private void applyQuickAppointmentTime(int hourOfDay, int minute) {
        Calendar baseCalendar = resolveBaseAppointmentCalendar();
        baseCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
        baseCalendar.set(Calendar.MINUTE, minute);
        baseCalendar.set(Calendar.SECOND, 0);
        baseCalendar.set(Calendar.MILLISECOND, 0);
        inputAppointmentAt.setText(formatAppointmentAt(baseCalendar.getTimeInMillis()));
        layoutAppointmentAt.setError(null);
        textAppointmentError.setVisibility(View.GONE);
        refreshQuickAppointmentButtons();
        dispatchAppointmentChanged();
    }

    private void refreshQuickAppointmentButtons() {
        Calendar selectedCalendar = parseAppointmentCalendar(getAppointmentAt());
        int selectedDateOffset = resolveSelectedDateOffset(selectedCalendar);
        bindQuickButtonStyle(buttonQuickToday, selectedDateOffset == 0);
        bindQuickButtonStyle(buttonQuickTomorrow, selectedDateOffset == 1);
        bindQuickButtonStyle(buttonQuickDayAfterTomorrow, selectedDateOffset == 2);

        boolean hasSelectedTime = selectedCalendar != null;
        bindQuickButtonStyle(
                buttonQuickMorning,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 10
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonQuickAfternoon,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 14
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
        bindQuickButtonStyle(
                buttonQuickLateAfternoon,
                hasSelectedTime
                        && selectedCalendar.get(Calendar.HOUR_OF_DAY) == 16
                        && selectedCalendar.get(Calendar.MINUTE) == 0
        );
    }

    private int resolveSelectedDateOffset(@Nullable Calendar selectedCalendar) {
        if (selectedCalendar == null) {
            return -1;
        }

        Calendar todayCalendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        normalizeDateOnly(todayCalendar);

        Calendar selectedDateCalendar = (Calendar) selectedCalendar.clone();
        normalizeDateOnly(selectedDateCalendar);
        long diffMillis = selectedDateCalendar.getTimeInMillis() - todayCalendar.getTimeInMillis();
        long dayMillis = 24L * 60L * 60L * 1000L;
        if (diffMillis < 0L || diffMillis % dayMillis != 0L) {
            return -1;
        }

        long dayOffset = diffMillis / dayMillis;
        return dayOffset <= 2L ? (int) dayOffset : -1;
    }

    private void normalizeDateOnly(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private void bindQuickButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.figma_mvp_primary)
            ));
            button.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.figma_mvp_primary)
            ));
            button.setTextColor(ContextCompat.getColor(activity, R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.figma_mvp_surface)
        ));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(activity, R.color.figma_mvp_divider)
        ));
        button.setTextColor(ContextCompat.getColor(activity, R.color.figma_mvp_primary));
    }

    private Calendar resolveBaseAppointmentCalendar() {
        Calendar parsedCalendar = parseAppointmentCalendar(getAppointmentAt());
        if (parsedCalendar != null) {
            return parsedCalendar;
        }

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(SEOUL_TIME_ZONE), Locale.KOREA);
        calendar.set(Calendar.HOUR_OF_DAY, 10);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar;
    }

    @Nullable
    private Calendar parseAppointmentCalendar(String appointmentAt) {
        return BookingAppointmentDateTime.parse(appointmentAt);
    }

    private String formatAppointmentAt(long appointmentAtMillis) {
        return BookingAppointmentDateTime.format(appointmentAtMillis);
    }

    private void dispatchAppointmentChanged() {
        if (appointmentChangedListener != null) {
            appointmentChangedListener.onAppointmentChanged(getAppointmentAt());
        }
    }

    private void showAppointmentError(int messageResId) {
        textAppointmentError.setText(messageResId);
        textAppointmentError.setVisibility(View.VISIBLE);
    }
}
