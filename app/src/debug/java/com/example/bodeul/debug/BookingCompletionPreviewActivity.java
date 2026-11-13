package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.ui.booking.BookingCompletionActivity;

/** 이동 목적지만 기록해 완료 화면 검사 중 서버·외부 지도에 접근하지 않는다. */
public class BookingCompletionPreviewActivity extends BookingCompletionActivity {
    public Intent lastStartedIntent;

    @Override public void startActivity(Intent intent) {
        lastStartedIntent = intent;
    }
}
