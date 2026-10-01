package com.bodeul.core.appointment;

final class AppointmentPricePolicy {
    static final int BASE_PRICE = 40_000;
    static final String VERSION = "mvp-fixed-40000-v1";

    private AppointmentPricePolicy() { }

    static void requireConfirmation(AppointmentService.CreateAppointmentCommand command) {
        if (!VERSION.equals(command.pricePolicyVersion()) || command.expectedFinalPrice() == null
                || command.expectedFinalPrice() != BASE_PRICE) {
            throw AppointmentException.priceConfirmationRequired();
        }
    }
}
