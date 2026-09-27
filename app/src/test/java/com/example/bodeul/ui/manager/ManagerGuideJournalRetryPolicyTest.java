package com.example.bodeul.ui.manager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.Collections;

public class ManagerGuideJournalRetryPolicyTest {

    @Test
    public void failedStatus_orServerError_showsRetry() {
        CompanionSession failed = session();
        failed.applyCompletionState(
                1L, "일지", "FAILED", 1, "", 2L, Collections.emptyList());
        assertTrue(ManagerGuideJournalRetryPolicy.shouldShowRetry(failed));

        CompanionSession error = session();
        error.applyCompletionState(
                1L, "일지", "PENDING", 1, "생성 실패", 2L, Collections.emptyList());
        assertTrue(ManagerGuideJournalRetryPolicy.shouldShowRetry(error));

        CompanionSession pending = session();
        pending.applyCompletionState(
                1L, "일지", "PENDING", 1, "", 2L, Collections.emptyList());
        assertTrue(ManagerGuideJournalRetryPolicy.shouldShowRetry(pending));
    }

    @Test
    public void readyOrMissingSession_doesNotShowRetry() {
        CompanionSession ready = session();
        ready.applyCompletionState(
                1L, "일지", "READY", 1, "", 2L, Collections.emptyList());
        assertFalse(ManagerGuideJournalRetryPolicy.shouldShowRetry(ready));

        CompanionSession notRequested = session();
        notRequested.applyCompletionState(
                1L, "일지", "NOT_REQUESTED", 0, "", 2L, Collections.emptyList());
        assertFalse(ManagerGuideJournalRetryPolicy.shouldShowRetry(notRequested));
        assertFalse(ManagerGuideJournalRetryPolicy.shouldShowRetry(null));
    }

    private CompanionSession session() {
        return new CompanionSession(
                "session-1",
                "request-1",
                "manager-1",
                13,
                SessionStatus.CARE_ENDED,
                "",
                "",
                "",
                "",
                "",
                false);
    }
}
