package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;

import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingTripType;

import org.junit.Test;

public class BookingPriceEstimatorTest {
    @Test
    public void newQuote_isFortyThousandWithoutOptionFeesOrLegacyDiscounts() {
        BookingPriceEstimator estimator = new BookingPriceEstimator();
        for (BookingTripType trip : BookingTripType.values()) {
            for (BookingMobilitySupport mobility : BookingMobilitySupport.values()) {
                for (BookingCouponType coupon : BookingCouponType.values()) {
                    BookingPriceSummary quote = estimator.estimate(trip, mobility, coupon);
                    assertEquals(40_000, quote.getBasePrice());
                    assertEquals(0, quote.getOptionSurchargePrice());
                    assertEquals(0, quote.getCouponDiscountPrice());
                    assertEquals(40_000, quote.getFinalPrice());
                }
            }
        }
    }
}
