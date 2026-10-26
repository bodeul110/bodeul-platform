package com.example.bodeul.data;

import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MockManagerRepositorySummaryTest {
    private MockManagerRepository repository;
    private CompanionSession session;

    @Before
    public void setUp() {
        MockBodeulRepository store = new MockBodeulRepository() {
            @Override public synchronized HospitalGuide getHospitalGuide(
                    String hospitalName, String departmentName) {
                return new HospitalGuide("summary-test", 1L, hospitalName, departmentName,
                        Arrays.asList(
                                new GuideStep("CONSULTATION_SUMMARY", 7, "진료 요약", ""),
                                new GuideStep("PAYMENT_EVIDENCE", 8, "수납", "")));
            }
        };
        repository = new MockManagerRepository(store);
        session = store.getPrimaryManagerSession("manager-1");
        session.setCurrentStepOrder(7);
        session.applyServerGuideProgress("CONSULTATION_SUMMARY", true, true, "");
    }

    @Test
    public void savesAndClearsOnlyCurrentSummary() {
        RecordingCallback saved = save(session.getId(), "진료 요약");
        assertNotNull(saved.result);
        assertNull(saved.error);
        assertEquals("진료 요약", session.getFieldPhotoNote());
        assertNotNull(save(session.getId(), "").result);
        assertEquals("", session.getFieldPhotoNote());
    }

    @Test
    public void differentSessionIsRejectedWithoutWrite() {
        String before = session.getFieldPhotoNote();
        RecordingCallback rejected = save("other-session", "덮어쓰면 안 됨");
        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, rejected.error);
        assertNull(rejected.result);
        assertEquals(before, session.getFieldPhotoNote());
    }

    @Test
    public void changedStepIsRejectedWithoutWrite() {
        String before = session.getFieldPhotoNote();
        session.setCurrentStepOrder(8);
        session.applyServerGuideProgress("PAYMENT_EVIDENCE", true, true, "");
        RecordingCallback rejected = save(session.getId(), "덮어쓰면 안 됨");
        assertEquals(ManagerRepository.MESSAGE_STALE_GUIDE_STEP, rejected.error);
        assertNull(rejected.result);
        assertEquals(before, session.getFieldPhotoNote());
    }

    private RecordingCallback save(String sessionId, String note) {
        RecordingCallback callback = new RecordingCallback();
        repository.saveConsultationSummaryNote(
                "manager-1", sessionId, "CONSULTATION_SUMMARY", note, callback);
        return callback;
    }

    private static final class RecordingCallback implements RepositoryCallback<ManagerDashboard> {
        ManagerDashboard result;
        String error;
        @Override public void onSuccess(ManagerDashboard result) { this.result = result; }
        @Override public void onError(String message) { error = message; }
    }
}
