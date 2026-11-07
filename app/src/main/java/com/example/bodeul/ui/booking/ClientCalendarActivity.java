package com.example.bodeul.ui.booking;

/** 본인에게 허용된 예약 목록을 달력의 선택 날짜로 좁혀 보여준다. */
public class ClientCalendarActivity extends ClientBookingHistoryActivity {
    @Override protected boolean isCalendarScreen() { return true; }
}
