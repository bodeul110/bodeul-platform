package com.example.bodeul.data;

import androidx.annotation.Nullable;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;

public final class MockManagerStore {
    private final MockBodeulRepository repository;

    public MockManagerStore(MockBodeulRepository repository) {
        this.repository = repository;
    }

    @Nullable
    public ManagerDashboard getManagerDashboard(String managerUserId) {
        User manager = repository.findUserById(managerUserId);
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (manager == null || session == null) {
            return null;
        }

        AppointmentRequest request = repository.findAppointmentRequest(session.getAppointmentRequestId());
        if (request == null) {
            return null;
        }

        User patient = repository.findUserById(request.getPatientUserId());
        User guardian = repository.findUserById(request.getGuardianUserId());
        HospitalGuide guide = HospitalGuideFallbackFactory.fallbackIfMissing(
                repository.getHospitalGuide(
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                request.getHospitalName(),
                request.getDepartmentName()
        );
        synchronizeCurrentStepCode(session, guide);
        SessionReport report = repository.getSessionReport(session.getId());

        if (patient == null || guardian == null) {
            return null;
        }

        return new ManagerDashboard(manager, patient, guardian, request, session, guide, report);
    }

    private void synchronizeCurrentStepCode(CompanionSession session, HospitalGuide guide) {
        String currentStepCode = "";
        for (GuideStep step : guide.getSteps()) {
            if (step.getOrder() == session.getCurrentStepOrder()) {
                currentStepCode = step.getCode();
                break;
            }
        }
        session.setCurrentStepCode(currentStepCode);
    }

    @Nullable
    public ManagerDashboard advanceManagerSession(String managerUserId) {
        ManagerDashboard dashboard = getManagerDashboard(managerUserId);
        if (dashboard == null) {
            return null;
        }

        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        if (session.getCurrentStepOrder() >= totalSteps) {
            return dashboard;
        }

        int nextStep = session.getCurrentStepOrder() + 1;
        session.setCurrentStepOrder(nextStep);
        session.setStatus(repository.resolveStepStatus(nextStep, totalSteps));
        dashboard.getAppointmentRequest().setStatus(AppointmentStatus.IN_PROGRESS);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateGuardianMessage(String managerUserId, String message) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setGuardianUpdate(message);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard appendManagerCompanionChatMessage(
            String managerUserId,
            String expectedRequestId,
            String expectedSessionId,
            String message,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        return repository.appendManagerCompanionChatMessage(
                managerUserId, expectedRequestId, expectedSessionId, message, attachments);
    }

    @Nullable
    public ManagerDashboard markManagerCompanionChatRead(
            String managerUserId, String expectedRequestId, String expectedSessionId) {
        return repository.markManagerCompanionChatRead(managerUserId, expectedRequestId, expectedSessionId);
    }

    @Nullable
    public ManagerDashboard saveCompanionLocationAlert(
            String managerUserId,
            CompanionLocationAlertStage stage
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null || stage == null) {
            return null;
        }
        if (!session.getLocationAlertStage().canAdvanceTo(stage)) {
            return getManagerDashboard(managerUserId);
        }
        session.setLocationAlertStage(stage);
        session.setLocationAlertSentAtMillis(System.currentTimeMillis());
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateLocationSummary(String managerUserId, String summary) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setLocationSummary(summary);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateSharedLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String summary
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        long capturedAtMillis = System.currentTimeMillis();
        session.setLocationSummary(summary);
        session.recordSharedLocation(latitude, longitude, summary, capturedAtMillis);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateLiveLocationSharingState(String managerUserId, boolean active) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        long startedAtMillis = session.getLiveLocationSharingStartedAtMillis();
        if (active && startedAtMillis <= 0L) {
            startedAtMillis = System.currentTimeMillis();
        }
        session.updateLiveLocationSharing(active, startedAtMillis);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateFieldPhotoNote(String managerUserId, String note) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setFieldPhotoNote(note);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateMedicationNote(String managerUserId, String note) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setMedicationNote(note);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updatePharmacySummary(String managerUserId, String summary) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPharmacySummary(summary);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPreConsultationConfirmed(preConsultationConfirmed);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updatePrescriptionCollected(
            String managerUserId,
            boolean prescriptionCollected
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPrescriptionCollected(prescriptionCollected);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updatePharmacyCompleted(String managerUserId, boolean pharmacyCompleted) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setPharmacyCompleted(pharmacyCompleted);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard updateMedicationGuidanceCompleted(
            String managerUserId,
            boolean medicationGuidanceCompleted
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }
        session.setMedicationGuidanceCompleted(medicationGuidanceCompleted);
        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public ManagerDashboard saveSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt
    ) {
        CompanionSession session = repository.getPrimaryManagerSession(managerUserId);
        if (session == null) {
            return null;
        }

        SessionReport existingReport = repository.getSessionReport(session.getId());
        if (existingReport != null) {
            repository.getMutableSessionReports().remove(existingReport);
        }

        SessionReport report = new SessionReport(
                "report-" + session.getId(),
                session.getId(),
                summary,
                treatmentNotes,
                medicationNotes,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisitAt
        );
        repository.getMutableSessionReports().add(report);
        session.updateLiveLocationSharing(false, 0L);
        session.setStatus(SessionStatus.COMPLETED);

        AppointmentRequest request = repository.findAppointmentRequest(session.getAppointmentRequestId());
        if (request != null) {
            request.setStatus(AppointmentStatus.COMPLETED);
        }

        return getManagerDashboard(managerUserId);
    }

    @Nullable
    public CompanionSession findSessionByRequestId(String requestId) {
        for (CompanionSession session : repository.getMutableCompanionSessions()) {
            if (session.getAppointmentRequestId().equals(requestId)) {
                return session;
            }
        }
        return null;
    }

    public boolean isManagerAvailable(String managerUserId) {
        for (CompanionSession session : repository.getMutableCompanionSessions()) {
            if (!session.getManagerUserId().equals(managerUserId)) {
                continue;
            }
            if (repository.isActiveSession(session)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public AppointmentRequest assignManagerToRequest(String requestId, String managerUserId) {
        AppointmentRequest request = repository.findAppointmentRequest(requestId);
        if (request == null || request.getStatus() != AppointmentStatus.REQUESTED) {
            return null;
        }
        if (!repository.hasLinkedParticipants(request) || !isManagerAvailable(managerUserId)) {
            return null;
        }
        if (findSessionByRequestId(requestId) != null) {
            return null;
        }

        request.assignManager(managerUserId);
        repository.getMutableCompanionSessions().add(new CompanionSession(
                "session-" + requestId,
                requestId,
                managerUserId,
                1,
                SessionStatus.READY,
                "",
                "",
                "",
                "",
                "",
                false
        ));
        return request;
    }
}
