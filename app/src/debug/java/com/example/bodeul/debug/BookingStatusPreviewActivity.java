package com.example.bodeul.debug;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.ui.booking.BookingStatusActivity;

/** 합성 저장소와 화면 이동 기록만 사용하는 예약 상세 계측 진입점이다. */
public final class BookingStatusPreviewActivity extends BookingStatusActivity {
    static AuthRepository testAuth;
    static BookingRepository testBooking;
    Intent lastStartedIntent;
    boolean mapUnavailable;

    @Override protected AuthRepository provideAuthRepository() {
        if (testAuth == null) throw new IllegalStateException("합성 인증 저장소가 필요합니다.");
        return testAuth;
    }

    @Override protected BookingRepository provideBookingRepository() {
        if (testBooking == null) throw new IllegalStateException("합성 예약 저장소가 필요합니다.");
        return testBooking;
    }

    @Override public void startActivity(Intent intent, @Nullable Bundle options) {
        if (mapUnavailable && Intent.ACTION_VIEW.equals(intent.getAction())) {
            throw new ActivityNotFoundException("합성 지도 미설치 상태");
        }
        lastStartedIntent = intent;
    }
}
