package com.example.bodeul.ui.manager;

import static org.junit.Assert.*;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ManagerMatchingOverviewTest {
    @Test public void distinguishesAssignedOngoingCompletedCanceledAndRequested() {
        List<AppointmentRequest> source = new ArrayList<>();
        for (AppointmentStatus status : AppointmentStatus.values()) source.add(request(status.name(), status, "2026-11-09 10:30"));
        ManagerMatchingOverview model = new ManagerMatchingOverview(source);
        assertEquals(5, model.count(ManagerMatchingOverview.Filter.ALL));
        assertEquals(2, model.count(ManagerMatchingOverview.Filter.ONGOING));
        assertEquals(1, model.count(ManagerMatchingOverview.Filter.COMPLETED));
        assertEquals(1, model.count(ManagerMatchingOverview.Filter.CANCELED));
    }

    @Test public void emptyIsZeroAndDoesNotInventDemoCounts() {
        ManagerMatchingOverview model = new ManagerMatchingOverview(Collections.emptyList());
        for (ManagerMatchingOverview.Filter filter : ManagerMatchingOverview.Filter.values()) assertEquals(0, model.count(filter));
    }

    @Test public void deduplicatesAndIgnoresMissingRecords() {
        AppointmentRequest request = request("one", AppointmentStatus.MATCHED, "2026-11-09 10:30");
        assertEquals(1, new ManagerMatchingOverview(Arrays.asList(request, null, request,
                request("", AppointmentStatus.MATCHED, "2026-11-09 10:30"))).count(ManagerMatchingOverview.Filter.ALL));
    }

    @Test public void capsSortedSnapshotAtOneHundred() {
        List<AppointmentRequest> source = new ArrayList<>();
        for (int i = 0; i < 120; i++) source.add(request("id-" + i, AppointmentStatus.COMPLETED, "2026-11-09 10:30"));
        source.add(request("newer", AppointmentStatus.MATCHED, "2026-11-10 10:30"));
        ManagerMatchingOverview model = new ManagerMatchingOverview(source);
        assertEquals(100, model.count(ManagerMatchingOverview.Filter.ALL));
        assertEquals("newer", model.select(ManagerMatchingOverview.Filter.ALL, null).get(0).getId());
        source.clear();
        assertEquals(100, model.count(ManagerMatchingOverview.Filter.ALL));
    }

    @Test public void dateAndStatusFiltersComposeWithoutMutatingTheSnapshot() {
        ManagerMatchingOverview model = new ManagerMatchingOverview(Arrays.asList(
                request("today", AppointmentStatus.MATCHED, "2026-11-09 10:30"),
                request("done", AppointmentStatus.COMPLETED, "2026-11-09T11:30:00"),
                request("other", AppointmentStatus.MATCHED, "2026-11-10 10:30")));
        assertEquals(2, model.select(ManagerMatchingOverview.Filter.ALL, "2026-11-09").size());
        assertEquals("today", model.select(ManagerMatchingOverview.Filter.ONGOING, "2026-11-09").get(0).getId());
        assertEquals(0, model.select(ManagerMatchingOverview.Filter.ALL, "2026-11-08").size());
        assertEquals(3, model.count(ManagerMatchingOverview.Filter.ALL));
    }

    @Test public void invalidDatesStayInHistoryButNeverAppearAsAnotherDay() {
        for (String value : Arrays.asList("", null, "2026-02-30 10:30", "2026-11-09 25:00",
                "2026-11-09 10:30:99", "2026-11-09T23:30:00Z")) {
            ManagerMatchingOverview model = new ManagerMatchingOverview(Collections.singletonList(request("bad", AppointmentStatus.MATCHED, value)));
            assertTrue(model.hasUndatedRequests());
            assertEquals(0, model.select(ManagerMatchingOverview.Filter.ALL, "2026-11-09").size());
            assertEquals(1, model.count(ManagerMatchingOverview.Filter.ALL));
        }
    }

    @Test(expected = UnsupportedOperationException.class) public void filteredListsAreImmutable() {
        new ManagerMatchingOverview(Collections.emptyList()).select(ManagerMatchingOverview.Filter.ALL, null).clear();
    }

    private static AppointmentRequest request(String id, AppointmentStatus status, String date) {
        return new AppointmentRequest(id, "patient", "", "합성 병원", "내과", date, "정문", "", status, "manager");
    }
}
