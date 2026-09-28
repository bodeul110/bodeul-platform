package com.example.bodeul.debug;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

public class ManagerGuideLegacyPreviewRepositoryTest {
    @Test
    public void legacyMode_keepsRawStepCodesAcrossFullSevenStepFlow() {
        ManagerGuidePreviewRepository repository = new ManagerGuidePreviewRepository(
                "LEGACY_CORE_PATIENT_CONTACT",
                false,
                true);

        String[] expectedCodes = {
                "LEGACY_CORE_PATIENT_CONTACT",
                "LEGACY_CORE_RECEPTION_PREPARATION",
                "LEGACY_CORE_RECEPTION",
                "LEGACY_CORE_CONSULTATION",
                "LEGACY_CORE_PAYMENT",
                "LEGACY_CORE_PHARMACY",
                "LEGACY_CORE_RETURN_AND_CLOSE"
        };
        ManagerDashboard current = dashboard(repository);
        assertEquals(expectedCodes.length, current.getHospitalGuide().getSteps().size());
        for (int index = 0; index < expectedCodes.length; index++) {
            assertEquals(expectedCodes[index], current.getSession().getCurrentStepCode());
            if (index == expectedCodes.length - 1) {
                break;
            }
            AtomicReference<ManagerDashboard> advanced = new AtomicReference<>();
            AtomicReference<String> error = new AtomicReference<>();
            repository.advanceCurrentStep(
                    ManagerGuidePreviewRepository.MANAGER_ID,
                    current.getSession().getId(),
                    current.getSession().getCurrentStepCode(),
                    callback(advanced, error));
            assertNull(error.get());
            current = advanced.get();
        }

        assertEquals(SessionStatus.PAYMENT, current.getSession().getStatus());
        assertEquals(
                "LAST_STEP_REACHED",
                current.getSession().getAdvanceBlockedReason());

        AtomicReference<SessionReport> report = new AtomicReference<>();
        AtomicReference<String> reportError = new AtomicReference<>();
        repository.submitSessionReport(
                ManagerGuidePreviewRepository.MANAGER_ID,
                "동행 요약",
                "",
                "",
                "",
                "",
                "",
                null,
                "",
                "",
                callback(report, reportError));

        assertNull(reportError.get());
        CompanionSession completed = repository.dataRepository()
                .getManagerSessions(ManagerGuidePreviewRepository.MANAGER_ID)
                .get(0);
        assertEquals(SessionStatus.COMPLETED, completed.getStatus());
        assertEquals("SESSION_TERMINAL", completed.getAdvanceBlockedReason());
    }

    private ManagerDashboard dashboard(ManagerGuidePreviewRepository repository) {
        AtomicReference<ManagerDashboard> dashboard = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        repository.getManagerDashboard(
                ManagerGuidePreviewRepository.MANAGER_ID,
                callback(dashboard, error));
        assertNull(error.get());
        return dashboard.get();
    }

    private <T> RepositoryCallback<T> callback(
            AtomicReference<T> result,
            AtomicReference<String> error
    ) {
        return new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T value) {
                result.set(value);
            }

            @Override
            public void onError(String message) {
                error.set(message);
            }
        };
    }
}
