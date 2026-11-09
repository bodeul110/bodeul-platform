package com.example.bodeul.ui.booking;

import static org.junit.Assert.*;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import java.util.*;
import org.junit.Test;

public class ClientCalendarFilterTest {
    @Test public void exactSeoulDateDoesNotFollowDeviceTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertTrue(ClientCalendarFilter.matches("2026-11-07 00:15", 2026, Calendar.NOVEMBER, 7));
            assertFalse(ClientCalendarFilter.matches("2026-11-07 00:15", 2026, Calendar.NOVEMBER, 6));
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void invalidAndUndatedValuesAreNeverAssignedToSelectedDate() {
        for (String value : new String[]{null, "", "2026-02-30 12:30", "2026-11-07", "2026-11-07 24:00", "2026-11-07 10:30 trailing"}) {
            assertFalse(ClientCalendarFilter.matches(value, 2026, Calendar.NOVEMBER, 7));
        }
    }
    @Test public void leapDayAndYearBoundaryAreStrict() {
        assertTrue(ClientCalendarFilter.matches("2028-02-29 10:30", 2028, Calendar.FEBRUARY, 29));
        assertFalse(ClientCalendarFilter.matches("2027-02-29 10:30", 2027, Calendar.FEBRUARY, 29));
        assertFalse(ClientCalendarFilter.matches("2026-12-31 23:59", 2027, Calendar.JANUARY, 1));
    }
    @Test public void selectKeepsRepositoryOrderAndAllStatusesWithoutMutatingSource() {
        List<AppointmentRequest> requests = new ArrayList<>();
        for (AppointmentStatus status : AppointmentStatus.values()) requests.add(request(status.name(), "2026-11-07 10:30", status));
        List<AppointmentRequest> expected = new ArrayList<>(requests);
        requests.add(request("tomorrow", "2026-11-08 10:30", AppointmentStatus.REQUESTED));
        List<AppointmentRequest> selected = ClientCalendarFilter.select(requests, 2026, Calendar.NOVEMBER, 7);
        assertEquals(expected, selected);
        selected.clear();
        assertEquals(expected.size() + 1, requests.size());
        assertTrue(ClientCalendarFilter.select(Collections.emptyList(), 2026, Calendar.NOVEMBER, 7).isEmpty());
    }
    private AppointmentRequest request(String id, String date, AppointmentStatus status) {
        return new AppointmentRequest(id, "synthetic-patient", null, "합성 병원", "내과", date, "정문", "", status, null);
    }
}
