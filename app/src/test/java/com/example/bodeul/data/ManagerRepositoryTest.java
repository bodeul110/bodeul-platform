package com.example.bodeul.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

public class ManagerRepositoryTest {
    @Test
    public void matchesAdvanceExpectation_requiresFreshSessionAndStep() {
        CompanionSession session = new CompanionSession(
                "session-1",
                "appointment-1",
                "manager-1",
                2,
                SessionStatus.MEETING,
                "",
                "",
                "",
                "",
                "",
                false);
        session.applyServerGuideProgress("HOSPITAL_ROUTE", true, true, "");

        assertTrue(ManagerRepository.matchesAdvanceExpectation(
                session,
                " session-1 ",
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                session,
                "session-2",
                "HOSPITAL_ROUTE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                session,
                "session-1",
                "RECEPTION_QUEUE"));
        assertFalse(ManagerRepository.matchesAdvanceExpectation(
                null,
                "session-1",
                "HOSPITAL_ROUTE"));
    }

    @Test
    public void matchesMedicationExpectation_requiresMedicationStepAndFreshSession() {
        CompanionSession session = new CompanionSession(
                "session-1",
                "appointment-1",
                "manager-1",
                11,
                SessionStatus.PAYMENT,
                "",
                "",
                "",
                "",
                "",
                false);
        session.applyServerGuideProgress(
                "MEDICATION_CONFIRMATION", true, true, "");

        assertTrue(ManagerRepository.matchesMedicationExpectation(
                session,
                " session-1 ",
                "MEDICATION_CONFIRMATION"));
        assertFalse(ManagerRepository.matchesMedicationExpectation(
                session,
                "session-2",
                "MEDICATION_CONFIRMATION"));
        assertFalse(ManagerRepository.matchesMedicationExpectation(
                session,
                "session-1",
                "PRESCRIPTION_DOCUMENTS"));
    }

    @Test
    public void matchesPaymentAndArtifactExpectations_requirePurposeStepAndFreshSession() {
        CompanionSession session = new CompanionSession(
                "session-1",
                "appointment-1",
                "manager-1",
                8,
                SessionStatus.PAYMENT,
                "",
                "",
                "",
                "",
                "",
                false);
        session.applyServerGuideProgress("PAYMENT_EVIDENCE", true, true, "");

        assertTrue(ManagerRepository.matchesPaymentExpectation(
                session, "session-1", "PAYMENT_EVIDENCE"));
        assertTrue(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-1",
                "PAYMENT_EVIDENCE",
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE));
        assertFalse(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-2",
                "PAYMENT_EVIDENCE",
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE));
        assertFalse(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-1",
                "PAYMENT_EVIDENCE",
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE));

        session.applyServerGuideProgress("PRESCRIPTION_DOCUMENTS", true, true, "");
        assertTrue(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-1",
                "PRESCRIPTION_DOCUMENTS",
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE));
        assertFalse(ManagerRepository.matchesPaymentExpectation(
                session, "session-1", "PRESCRIPTION_DOCUMENTS"));
    }
}
