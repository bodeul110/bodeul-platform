package com.example.bodeul.ui.booking;

import static org.junit.Assert.*;
import org.junit.Test;

public class BookingCompletionSnapshotTest {
    @Test public void mapKeepsSelectedCoordinatesAndHospitalName() {
        assertEquals("37.5,127.1(선택 병원)", snapshot(37.5, 127.1).getHospitalMapQuery());
    }

    @Test public void missingOrInvalidCoordinatesUseHospitalNameOnly() {
        for (double[] coordinates : new double[][] {
                {0, 0}, {Double.NaN, 127}, {37, Double.POSITIVE_INFINITY}, {91, 127}, {37, 181}}) {
            assertEquals("선택 병원", snapshot(coordinates[0], coordinates[1]).getHospitalMapQuery());
        }
    }

    @Test public void absentValuesDoNotInventPaymentOrPlace() {
        BookingCompletionSnapshot snapshot = new BookingCompletionSnapshot(
                null, null, 0, 0, null, null, null, null, null, null, null, null, -1, null, null, null, null);
        assertEquals("", snapshot.getRequestId());
        assertEquals("", snapshot.getMeetingPlace());
        assertEquals("", snapshot.getPaymentStatusCode());
        assertEquals(0, snapshot.getFinalPrice());
    }

    private static BookingCompletionSnapshot snapshot(double latitude, double longitude) {
        return new BookingCompletionSnapshot("request", " 선택 병원 ", latitude, longitude,
                "내과", "2028-02-29 10:17", "병원 앞", "", "INDEPENDENT", "ONE_WAY",
                "CARD", "NONE", 40000, "PENDING", "", "", "");
    }
}
