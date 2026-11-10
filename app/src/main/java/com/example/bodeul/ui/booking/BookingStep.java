package com.example.bodeul.ui.booking;

/** 저장 계약을 바꾸지 않고 예약 입력 순서만 구분한다. */
public enum BookingStep {
    PARTICIPANT, VISIT, HEALTH, PAYMENT;

    public BookingStep previous() {
        return values()[Math.max(0, ordinal() - 1)];
    }

    public BookingStep next() {
        return values()[Math.min(values().length - 1, ordinal() + 1)];
    }

    public static BookingStep restore(String value) {
        for (BookingStep step : values()) {
            if (step.name().equals(value)) return step;
        }
        return PARTICIPANT;
    }
}
