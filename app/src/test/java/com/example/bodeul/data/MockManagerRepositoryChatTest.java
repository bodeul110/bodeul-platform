package com.example.bodeul.data;

import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

public class MockManagerRepositoryChatTest {
    private MockManagerRepository repository;
    private CompanionSession opened;
    private CompanionSession assigned;
    private int initialMessages;
    private long initialReadAt;

    @Before public void setup() {
        MockBodeulRepository store = new MockBodeulRepository() {
            @Override CompanionSession getPrimaryManagerSession(String managerUserId) {
                return assigned == null ? super.getPrimaryManagerSession(managerUserId) : assigned;
            }
        };
        repository = new MockManagerRepository(store);
        opened = store.getPrimaryManagerSession("manager-1");
        initialMessages = opened.getChatMessages().size();
        initialReadAt = opened.getManagerChatReadAtMillis();
    }

    @Test public void sendsAndMarksOnlyTheExpectedOpenSession() {
        RecordingCallback callback = send(opened.getAppointmentRequestId(), opened.getId());
        assertNull(callback.error);
        assertNotNull(callback.result);
        assertEquals(initialMessages + 1, opened.getChatMessages().size());
        assertTrue(opened.getManagerChatReadAtMillis() > 0);
        repository.markCompanionChatRead("manager-1", opened.getAppointmentRequestId(), opened.getId());
        assertTrue(opened.getManagerChatReadAtMillis() > 0);
    }

    @Test public void reassignmentDoesNotRedirectSendOrRead() {
        assigned = new CompanionSession("session-b", "request-b", "manager-1", 1,
                SessionStatus.MEETING, "", "", "", "", "", false);
        assertRejectedWithoutWrite(opened.getAppointmentRequestId(), opened.getId());
        assertTrue(assigned.getChatMessages().isEmpty());
        assertEquals(0L, assigned.getManagerChatReadAtMillis());
    }

    @Test public void differentRequestOrSessionDoesNotWrite() {
        assertRejectedWithoutWrite("another-request", opened.getId());
        assertRejectedWithoutWrite(opened.getAppointmentRequestId(), "another-session");
    }

    @Test public void closedSessionDoesNotWrite() {
        for (SessionStatus status : new SessionStatus[]{
                SessionStatus.CARE_ENDED, SessionStatus.COMPLETED, SessionStatus.CANCELED}) {
            opened.setStatus(status);
            assertRejectedWithoutWrite(opened.getAppointmentRequestId(), opened.getId());
        }
    }

    private void assertRejectedWithoutWrite(String requestId, String sessionId) {
        RecordingCallback callback = send(requestId, sessionId);
        assertEquals(ManagerRepository.MESSAGE_STALE_CHAT_SESSION, callback.error);
        assertNull(callback.result);
        repository.markCompanionChatRead("manager-1", requestId, sessionId);
        assertEquals(initialMessages, opened.getChatMessages().size());
        assertEquals(initialReadAt, opened.getManagerChatReadAtMillis());
    }

    private RecordingCallback send(String requestId, String sessionId) {
        RecordingCallback callback = new RecordingCallback();
        repository.sendCompanionChatMessage("manager-1", requestId, sessionId,
                "이 동행에만 전송", Collections.emptyList(), callback);
        return callback;
    }

    private static class RecordingCallback implements RepositoryCallback<ManagerDashboard> {
        ManagerDashboard result;
        String error;
        @Override public void onSuccess(ManagerDashboard result) { this.result = result; }
        @Override public void onError(String error) { this.error = error; }
    }
}
