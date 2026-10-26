package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;

import org.junit.Before;
import org.junit.Test;

public class MockManagerRepositoryMedicationTest {
    private MockBodeulRepository store;
    private MockManagerRepository repository;
    private CompanionSession session;

    @Before
    public void setUp() {
        store = new MockBodeulRepository();
        repository = new MockManagerRepository(store);
        session = store.getPrimaryManagerSession("manager-1");
        session.setCurrentStepOrder(11);
        session.applyServerGuideProgress(
                "MEDICATION_CONFIRMATION", true, true, "");
    }

    @Test
    public void saveMedicationNote_rejectsDifferentSessionWithoutMutation() {
        String original = session.getMedicationNote();
        RecordingCallback callback = new RecordingCallback();

        repository.saveMedicationNote(
                "manager-1",
                "session-2",
                "MEDICATION_CONFIRMATION",
                "변경되면 안 되는 메모",
                callback);

        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, callback.error);
        assertNull(callback.result);
        assertEquals(original, session.getMedicationNote());
    }

    @Test
    public void updateMedicationGuidance_rejectsDifferentStepWithoutMutation() {
        assertFalse(session.isMedicationGuidanceCompleted());
        RecordingCallback callback = new RecordingCallback();

        repository.updateMedicationGuidanceCompleted(
                "manager-1",
                "session-1",
                "CARE_END",
                true,
                callback);

        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, callback.error);
        assertNull(callback.result);
        assertFalse(session.isMedicationGuidanceCompleted());
    }

    private static final class RecordingCallback
            implements RepositoryCallback<ManagerDashboard> {
        private ManagerDashboard result;
        private String error;

        @Override
        public void onSuccess(ManagerDashboard result) {
            this.result = result;
        }

        @Override
        public void onError(String message) {
            error = message;
        }
    }
}
