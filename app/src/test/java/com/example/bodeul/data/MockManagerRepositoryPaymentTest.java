package com.example.bodeul.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.ManagerDashboard;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class MockManagerRepositoryPaymentTest {
    private MockManagerRepository repository;
    private CompanionSession session;

    @Before
    public void setUp() {
        MockBodeulRepository store = new MockBodeulRepository();
        repository = new MockManagerRepository(store);
        session = store.getPrimaryManagerSession("manager-1");
        session.setCurrentStepOrder(8);
        session.applyServerGuideProgress("PAYMENT_EVIDENCE", true, true, "");
    }

    @Test
    public void savePaymentNote_rejectsDifferentSessionWithoutMutation() {
        String original = session.getFieldPhotoNote();
        RecordingCallback callback = new RecordingCallback();

        repository.savePaymentEvidenceNote(
                "manager-1",
                "session-2",
                "PAYMENT_EVIDENCE",
                "변경되면 안 되는 메모",
                callback);

        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, callback.error);
        assertNull(callback.result);
        assertEquals(original, session.getFieldPhotoNote());
    }

    @Test
    public void clearPaymentArtifact_rejectsChangedStepWithoutMutation() {
        session.applyCompletionState(
                session.getCareEndedAtMillis(),
                session.getManagerJournal(),
                session.getReportGenerationStatus(),
                session.getReportGenerationAttempts(),
                session.getReportGenerationLastError(),
                session.getReportGenerationUpdatedAtMillis(),
                Collections.singletonList(new CompanionSessionArtifact(
                        "payment-artifact",
                        CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                        "receipt.jpg",
                        "image/jpeg",
                        1024L,
                        1L)));
        assertEquals(1, session.getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());

        session.setCurrentStepOrder(9);
        session.applyServerGuideProgress("PHARMACY_ROUTE", true, true, "");
        RecordingCallback cleared = new RecordingCallback();
        repository.clearSessionArtifacts(
                "manager-1",
                session.getId(),
                "PAYMENT_EVIDENCE",
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE,
                cleared);

        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, cleared.error);
        assertNull(cleared.result);
        assertEquals(1, session.getArtifacts(
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE).size());
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
