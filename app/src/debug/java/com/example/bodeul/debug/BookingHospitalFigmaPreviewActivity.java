package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.ui.booking.BookingHospitalSelectorActivity;
import com.example.bodeul.ui.booking.BookingHospitalSelectorCoordinator;

/** 합성 검색 응답만 사용하는 계측 테스트 진입점이다. 외부 지도와 서버를 열지 않는다. */
public final class BookingHospitalFigmaPreviewActivity extends BookingHospitalSelectorActivity {
    static BookingRepository testRepository;
    static BookingHospitalSelectorCoordinator.PlaceSearch testSearch;
    Intent mapIntent;

    @Override protected BookingRepository provideBookingRepository() {
        if (testRepository == null) throw new IllegalStateException("합성 병원 저장소가 필요합니다.");
        return testRepository;
    }

    @Override protected BookingHospitalSelectorCoordinator.PlaceSearch providePlaceSearch() {
        if (testSearch == null) throw new IllegalStateException("합성 장소 검색이 필요합니다.");
        return testSearch;
    }

    @Override public void startActivity(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                && "geo".equals(intent.getData().getScheme())) {
            mapIntent = intent;
            return;
        }
        super.startActivity(intent);
    }
}
