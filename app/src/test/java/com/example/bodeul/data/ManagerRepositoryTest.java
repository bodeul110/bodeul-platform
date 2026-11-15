package com.example.bodeul.data;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.SessionStatus;

import org.junit.Test;

public class ManagerRepositoryTest {
    @Test
    public void matchesChatExpectation_requiresBothPinnedIds() {
        CompanionSession session = chatSession(SessionStatus.MEETING);
        assertTrue(ManagerRepository.matchesChatExpectation(session, "appointment-1", "session-1"));
        assertFalse(ManagerRepository.matchesChatExpectation(session, "appointment-2", "session-1"));
        assertFalse(ManagerRepository.matchesChatExpectation(session, "appointment-1", "session-2"));
        assertFalse(ManagerRepository.matchesChatExpectation(session, null, "session-1"));
        assertFalse(ManagerRepository.matchesChatExpectation(session, "appointment-1", " "));
        assertFalse(ManagerRepository.matchesChatExpectation(null, "appointment-1", "session-1"));
    }

    @Test
    public void matchesChatExpectation_rejectsClosedOrUnknownState() {
        for (SessionStatus status : new SessionStatus[]{
                SessionStatus.CARE_ENDED, SessionStatus.COMPLETED, SessionStatus.CANCELED, null}) {
            assertFalse(ManagerRepository.matchesChatExpectation(chatSession(status), "appointment-1", "session-1"));
        }
    }

    private CompanionSession chatSession(SessionStatus status) {
        return new CompanionSession("session-1", "appointment-1", "manager-1", 1,
                status, "", "", "", "", "", false);
    }

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

        session.applyServerGuideProgress(
                "LEGACY_CORE_PHARMACY", true, true, "");
        assertTrue(ManagerRepository.matchesMedicationExpectation(
                session,
                "session-1",
                "LEGACY_CORE_PHARMACY"));
        assertFalse(ManagerRepository.matchesMedicationExpectation(
                session,
                "session-1",
                "MEDICATION_CONFIRMATION"));
        assertFalse(ManagerRepository.matchesMedicationExpectation(
                session,
                "session-2",
                "LEGACY_CORE_PHARMACY"));
        assertFalse(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-1",
                "LEGACY_CORE_PHARMACY",
                CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE));
        assertFalse(ManagerRepository.matchesVitalsExpectation(
                session,
                "session-1",
                "LEGACY_CORE_PHARMACY"));
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

        session.applyServerGuideProgress("LEGACY_CORE_PAYMENT", true, true, "");
        assertTrue(ManagerRepository.matchesPaymentExpectation(
                session, "session-1", "LEGACY_CORE_PAYMENT"));
        assertFalse(ManagerRepository.matchesPaymentExpectation(
                session, "session-1", "PAYMENT_EVIDENCE"));
        assertFalse(ManagerRepository.matchesPaymentExpectation(
                session, "session-2", "LEGACY_CORE_PAYMENT"));
        assertFalse(ManagerRepository.matchesArtifactExpectation(
                session,
                "session-1",
                "LEGACY_CORE_PAYMENT",
                CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE));
    }

    @Test
    public void matchesConsultationExpectation_acceptsLegacyAliasButKeepsRawIdentity() {
        CompanionSession session = new CompanionSession(
                "session-1",
                "appointment-1",
                "manager-1",
                4,
                SessionStatus.IN_TREATMENT,
                "",
                "",
                "",
                "",
                "",
                false);
        session.applyServerGuideProgress(
                "LEGACY_CORE_CONSULTATION", true, true, "");

        assertTrue(ManagerRepository.matchesConsultationExpectation(
                session, "session-1", "LEGACY_CORE_CONSULTATION"));
        assertFalse(ManagerRepository.matchesConsultationExpectation(
                session, "session-1", "CONSULTATION_SUPPORT"));
    }
}
