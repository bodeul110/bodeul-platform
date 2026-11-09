package com.example.bodeul.data;

import static org.junit.Assert.*;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import java.util.List;
import org.junit.Test;

public class MockManagerAppointmentsTest {
    @Test public void returnsOnlyAssignedRequestsAndNeverUnassignedForBlankId() {
        MockBodeulRepository source = new MockBodeulRepository();
        MockManagerRepository repository = new MockManagerRepository(source);
        for (String id : new String[]{null, "", "unknown", "manager-1", "manager-2"}) {
            repository.getManagerAppointments(id, new RepositoryCallback<List<AppointmentRequest>>() {
                @Override public void onSuccess(List<AppointmentRequest> result) {
                    for (AppointmentRequest request : result) assertEquals(id, request.getManagerUserId());
                    long expected = source.getAppointmentRequests().stream()
                            .filter(request -> id != null && !id.isEmpty() && id.equals(request.getManagerUserId())).count();
                    assertEquals(expected, result.size());
                }
                @Override public void onError(String message) { fail(message); }
            });
        }
    }
}
