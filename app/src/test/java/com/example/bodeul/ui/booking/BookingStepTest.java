package com.example.bodeul.ui.booking;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BookingStepTest {
    @Test public void forwardAndBackDoNotEscapeTheFlow() {
        assertEquals(BookingStep.PARTICIPANT, BookingStep.PARTICIPANT.previous());
        assertEquals(BookingStep.PAYMENT, BookingStep.PAYMENT.next());
        assertEquals(BookingStep.VISIT, BookingStep.PARTICIPANT.next());
        assertEquals(BookingStep.HEALTH, BookingStep.PAYMENT.previous());
    }

    @Test public void invalidSavedStageStartsFromParticipant() {
        assertEquals(BookingStep.PARTICIPANT, BookingStep.restore(null));
        assertEquals(BookingStep.PARTICIPANT, BookingStep.restore("old-screen"));
        assertEquals(BookingStep.HEALTH, BookingStep.restore("HEALTH"));
    }
}
