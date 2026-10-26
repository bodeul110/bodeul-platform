package com.example.bodeul.ui.booking;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 예약 메인 화면의 날짜·시간 요약을 Core API 문자열 계약과 분리해 표시한다.
 */
final class BookingMainScreenFormatter {
    private static final String SEOUL_TIME_ZONE = "Asia/Seoul";
    private static final String[] KOREAN_WEEKDAYS = {
            "", "일", "월", "화", "수", "목", "금", "토"
    };

    private BookingMainScreenFormatter() {
    }

    @NonNull
    static DisplayValue formatAppointment(String appointmentAt) {
        Calendar calendar = BookingAppointmentDateTime.parse(appointmentAt);
        if (calendar == null) {
            return new DisplayValue("", "");
        }
        String date = String.format(
                Locale.KOREA,
                "%d. %d. %d\n(%s)",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH),
                KOREAN_WEEKDAYS[calendar.get(Calendar.DAY_OF_WEEK)]
        );
        SimpleDateFormat timeFormatter = new SimpleDateFormat("a h:mm", Locale.KOREA);
        timeFormatter.setTimeZone(TimeZone.getTimeZone(SEOUL_TIME_ZONE));
        return new DisplayValue(date, timeFormatter.format(calendar.getTime()));
    }

    static final class DisplayValue {
        private final String dateText;
        private final String timeText;

        DisplayValue(String dateText, String timeText) {
            this.dateText = dateText;
            this.timeText = timeText;
        }

        String getDateText() {
            return dateText;
        }

        String getTimeText() {
            return timeText;
        }

        boolean isEmpty() {
            return dateText.isEmpty() || timeText.isEmpty();
        }
    }
}
