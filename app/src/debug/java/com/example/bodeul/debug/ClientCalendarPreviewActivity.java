package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.*;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.ClientCalendarActivity;
import java.text.SimpleDateFormat;
import java.util.*;

/** 현재 날짜의 합성 예약만 제공하는 읽기 전용 캘린더 검증 화면이다. */
public class ClientCalendarPreviewActivity extends ClientCalendarActivity {
    public int reads;
    public User requestedUser;
    public Intent lastNavigation;
    public boolean retrySucceeds;
    public void selectDate(int year, int month, int day) {
        Calendar date = Calendar.getInstance();
        date.set(year, month, day, 12, 0);
        ((android.widget.CalendarView) findViewById(com.example.bodeul.R.id.clientCalendar)).setDate(date.getTimeInMillis());
        onCalendarDateSelected(year, month, day);
    }
    @Override protected AuthRepository provideAuthRepository() { return FigmaPreviewDependencies.forBooking(this).authRepository; }
    @Override protected BookingRepository provideBookingRepository() {
        return new MockBookingRepository(new MockBodeulRepository()) {
            @Override public void getMyAppointmentRequests(User user, RepositoryCallback<List<AppointmentRequest>> callback) {
                reads++;
                requestedUser = user;
                if (getIntent().getBooleanExtra("error", false) && !retrySucceeds) { callback.onError("합성 조회 오류"); return; }
                SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA);
                date.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                callback.onSuccess(Collections.singletonList(new AppointmentRequest("synthetic-calendar", user.getId(), null,
                        "달력 합성 병원", "내과", date.format(new Date()) + " 10:30", "정문", "", AppointmentStatus.REQUESTED, null)));
            }
        };
    }
    @Override public void startActivity(Intent intent) { lastNavigation = intent; }
}
