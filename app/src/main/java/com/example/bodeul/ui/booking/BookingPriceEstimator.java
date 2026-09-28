package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingTripType;

/**
 * 신규 예약의 MVP 기본 2시간 견적을 계산한다. 기존 예약은 저장된 금액을 표시한다.
 */
public final class BookingPriceEstimator {
    private static final int BASE_PRICE = 40_000;

    public BookingPriceSummary estimate(
            BookingTripType tripType,
            BookingMobilitySupport mobilitySupport,
            BookingCouponType couponType
    ) {
        // 이동 조건은 준비 정보로 유지하며 미확정 추가요금·쿠폰은 계산하지 않는다.
        return new BookingPriceSummary(BASE_PRICE, 0, 0, BASE_PRICE);
    }
}
