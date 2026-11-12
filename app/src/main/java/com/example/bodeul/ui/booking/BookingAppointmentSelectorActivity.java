package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.bodeul.R;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** 일정 선택을 기존 서울 시간대 appointmentAt 계약으로 반환한다. */
public class BookingAppointmentSelectorActivity extends AppCompatActivity {
    private static final String EXTRA_INITIAL_APPOINTMENT_AT = "initialAppointmentAt";
    private static final String EXTRA_SELECTED_APPOINTMENT_AT = "selectedAppointmentAt";
    private Calendar selectedDate;
    private Calendar displayedMonth;
    private int selectedHour;
    private int selectedMinute;
    private boolean timeConfirmed;
    private BookingScheduleCalendarBinder calendar;

    public static Intent createIntent(Context context, @Nullable String appointmentAt) {
        return new Intent(context, BookingAppointmentSelectorActivity.class)
                .putExtra(EXTRA_INITIAL_APPOINTMENT_AT, appointmentAt == null ? "" : appointmentAt);
    }

    public static String parseResult(@Nullable Intent data) {
        String value = data == null ? null : data.getStringExtra(EXTRA_SELECTED_APPOINTMENT_AT);
        return value == null ? "" : value;
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_Bodeul_FinalBooking);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_booking_appointment_selector);
        BookingSelectorInsets.apply(this, findViewById(R.id.bookingAppointmentSelectorRoot));
        String original = getIntent().getStringExtra(EXTRA_INITIAL_APPOINTMENT_AT);
        Calendar initial = BookingAppointmentDateTime.resolveInitialSelection(original, System.currentTimeMillis());
        selectedDate = (Calendar) initial.clone();
        displayedMonth = (Calendar) initial.clone();
        selectedHour = initial.get(Calendar.HOUR_OF_DAY);
        selectedMinute = initial.get(Calendar.MINUTE);
        timeConfirmed = BookingAppointmentDateTime.parse(original) != null;
        if (savedInstanceState != null) {
            selectedDate.setTimeInMillis(savedInstanceState.getLong("date", initial.getTimeInMillis()));
            displayedMonth.setTimeInMillis(savedInstanceState.getLong("month", initial.getTimeInMillis()));
            selectedHour = savedInstanceState.getInt("hour", selectedHour);
            selectedMinute = savedInstanceState.getInt("minute", selectedMinute);
            timeConfirmed = savedInstanceState.getBoolean("confirmed", timeConfirmed);
        }
        displayedMonth.set(Calendar.DAY_OF_MONTH, 1);
        calendar = new BookingScheduleCalendarBinder(findViewById(R.id.gridBookingAppointmentCalendar));
        findViewById(R.id.buttonBookingAppointmentBack).setOnClickListener(v -> finish());
        findViewById(R.id.buttonBookingAppointmentPreviousMonth).setOnClickListener(v -> changeMonth(-1));
        findViewById(R.id.buttonBookingAppointmentNextMonth).setOnClickListener(v -> changeMonth(1));
        findViewById(R.id.buttonBookingAppointmentCustomTime).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag(BookingTimeSheet.TAG) == null) {
                BookingTimeSheet.create(selectedHour, selectedMinute).show(getSupportFragmentManager(), BookingTimeSheet.TAG);
            }
        });
        getSupportFragmentManager().setFragmentResultListener(BookingTimeSheet.RESULT, this, (key, result) -> {
            selectedHour = result.getInt("hour");
            selectedMinute = result.getInt("minute");
            timeConfirmed = true;
            render();
        });
        findViewById(R.id.buttonBookingAppointmentComplete).setOnClickListener(v -> {
            if (!timeConfirmed) return;
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_SELECTED_APPOINTMENT_AT,
                    BookingAppointmentDateTime.format(selectedDate, selectedHour, selectedMinute)));
            finish();
        });
        render();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putLong("date", selectedDate.getTimeInMillis());
        outState.putLong("month", displayedMonth.getTimeInMillis());
        outState.putInt("hour", selectedHour);
        outState.putInt("minute", selectedMinute);
        outState.putBoolean("confirmed", timeConfirmed);
        super.onSaveInstanceState(outState);
    }

    private void changeMonth(int offset) {
        displayedMonth.add(Calendar.MONTH, offset);
        render();
    }

    private void render() {
        ((TextView) findViewById(R.id.textBookingAppointmentDisplayedMonth)).setText(getString(
                R.string.final_schedule_month, displayedMonth.get(Calendar.YEAR), displayedMonth.get(Calendar.MONTH) + 1));
        Calendar today = BookingAppointmentDateTime.newCalendar();
        int current = today.get(Calendar.YEAR) * 12 + today.get(Calendar.MONTH);
        int shown = displayedMonth.get(Calendar.YEAR) * 12 + displayedMonth.get(Calendar.MONTH);
        View previous = findViewById(R.id.buttonBookingAppointmentPreviousMonth);
        previous.setEnabled(shown > current);
        previous.setAlpha(shown > current ? 1f : 0.3f);
        calendar.render(displayedMonth, selectedDate, System.currentTimeMillis(), date -> {
            selectedDate = (Calendar) date.clone();
            render();
        });
        String time = BookingTimeSheet.formatTime(this, selectedHour, selectedMinute);
        ((TextView) findViewById(R.id.buttonBookingAppointmentCustomTime)).setText(
                timeConfirmed ? time : getString(R.string.final_schedule_time_select));
        findViewById(R.id.layoutBookingAppointmentBottomAction).setVisibility(timeConfirmed ? View.VISIBLE : View.GONE);
        SimpleDateFormat dateLabel = new SimpleDateFormat("yyyy년 M월 d일(E)", Locale.KOREA);
        dateLabel.setTimeZone(selectedDate.getTimeZone());
        ((TextView) findViewById(R.id.buttonBookingAppointmentComplete)).setText(
                getString(R.string.final_schedule_done, dateLabel.format(selectedDate.getTime()), time));
    }
}
