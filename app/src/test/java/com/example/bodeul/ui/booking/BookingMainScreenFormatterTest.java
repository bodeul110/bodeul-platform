package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BookingMainScreenFormatterTest {
    @Test
    public void formatAppointment_splitsApiValueIntoFigmaDateAndTimeCards() {
        BookingMainScreenFormatter.DisplayValue value =
                BookingMainScreenFormatter.formatAppointment("2026-08-14 10:00");

        assertEquals("2026. 8. 14\n(금)", value.getDateText());
        assertEquals("오전 10:00", value.getTimeText());
    }

    @Test
    public void formatAppointment_keepsAfternoonMeaning() {
        BookingMainScreenFormatter.DisplayValue value =
                BookingMainScreenFormatter.formatAppointment("2026-08-14 16:30");

        assertEquals("오후 4:30", value.getTimeText());
    }

    @Test
    public void formatAppointment_invalidValueReturnsEmptySummary() {
        assertTrue(BookingMainScreenFormatter.formatAppointment("2026/08/14 10:00").isEmpty());
        assertTrue(BookingMainScreenFormatter.formatAppointment("").isEmpty());
    }
}
