package com.example.bodeul.domain.model;

/** 신규 예약의 표시 금액과 서버에 확인할 가격 계약을 함께 관리한다. */
public final class BookingPricePolicy {
    public static final String VERSION = "mvp-fixed-40000-v1";
    public static final int BASE_PRICE = 40_000;

    private BookingPricePolicy() {
    }
}
