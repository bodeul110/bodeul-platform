package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.AppointmentRequest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 서버 예약 일시는 서울 날짜로 비교하고 파싱 불가 값은 임의의 날짜에 넣지 않는다. */
public final class ClientCalendarFilter {
    private ClientCalendarFilter() { }

    public static boolean matches(String appointmentAt, int year, int month, int day) {
        Calendar value = BookingAppointmentDateTime.parse(appointmentAt);
        return value != null && value.get(Calendar.YEAR) == year
                && value.get(Calendar.MONTH) == month && value.get(Calendar.DAY_OF_MONTH) == day;
    }

    public static List<AppointmentRequest> select(List<AppointmentRequest> requests, int year, int month, int day) {
        List<AppointmentRequest> selected = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            if (matches(request.getAppointmentAt(), year, month, day)) selected.add(request);
        }
        return selected;
    }
}
