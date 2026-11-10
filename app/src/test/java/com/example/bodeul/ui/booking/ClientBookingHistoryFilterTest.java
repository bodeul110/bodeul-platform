package com.example.bodeul.ui.booking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ClientBookingHistoryFilterTest {
    @Test
    public void filtersMapEveryServerStatusToOneHistoryTab() {
        List<AppointmentRequest> requests = Arrays.asList(
                request("requested", AppointmentStatus.REQUESTED),
                request("matched", AppointmentStatus.MATCHED),
                request("progress", AppointmentStatus.IN_PROGRESS),
                request("completed", AppointmentStatus.COMPLETED),
                request("canceled", AppointmentStatus.CANCELED)
        );

        assertIds(ClientBookingHistoryFilter.MATCHING.select(requests), "requested");
        assertIds(ClientBookingHistoryFilter.PROGRESS.select(requests), "matched", "progress");
        assertIds(ClientBookingHistoryFilter.COMPLETED.select(requests), "completed");
        assertIds(ClientBookingHistoryFilter.CANCELED.select(requests), "canceled");
    }

    @Test
    public void selectionDoesNotMutateTheRepositoryList() {
        List<AppointmentRequest> requests = Arrays.asList(
                request("first", AppointmentStatus.MATCHED),
                request("second", AppointmentStatus.IN_PROGRESS)
        );

        List<AppointmentRequest> selected = ClientBookingHistoryFilter.PROGRESS.select(requests);

        assertIds(selected, "first", "second");
        selected.clear();
        assertEquals(2, requests.size());
        assertTrue(ClientBookingHistoryFilter.MATCHING.select(null).isEmpty());
        assertTrue(ClientBookingHistoryFilter.MATCHING.select(Collections.emptyList()).isEmpty());
    }

    private void assertIds(List<AppointmentRequest> requests, String... expectedIds) {
        assertEquals(expectedIds.length, requests.size());
        for (int index = 0; index < expectedIds.length; index++) {
            assertEquals(expectedIds[index], requests.get(index).getId());
        }
    }

    private AppointmentRequest request(String id, AppointmentStatus status) {
        return new AppointmentRequest(
                id,
                "patient",
                null,
                "서울대학교병원",
                "내과",
                "2026-11-10 10:30",
                "본관 1층",
                "",
                status,
                null
        );
    }
}
