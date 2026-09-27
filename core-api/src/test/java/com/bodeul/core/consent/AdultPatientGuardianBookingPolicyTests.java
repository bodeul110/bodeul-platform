package com.bodeul.core.consent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentCreateFingerprint;
import com.bodeul.core.auth.AppUserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdultPatientGuardianBookingPolicyTests {

    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID GUARDIAN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REQUEST = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);
    private static final String POLICY = "booking-test-v1";

    private static final String FINGERPRINT = fingerprint("기준 병원", START, "상태 요약", "복약 요약", "NONE");

    @Test
    void patientGrantsCreationForOneRequestWithoutAnExistingAppointment() {
        Grant grant = grant();

        assertThat(grant.id()).isNotNull();
        assertThat(grant.patientUserId()).isEqualTo(PATIENT);
        assertThat(grant.guardianUserId()).isEqualTo(GUARDIAN);
        assertThat(grant.clientRequestId()).isEqualTo(REQUEST);
        assertThat(grant.requestFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(grant.grantedByUserId()).isEqualTo(PATIENT);
        assertThat(grant.policyVersion()).isEqualTo(POLICY);
        assertThat(grant.revokedAt()).isNull();
        assertThat(grant.version()).isZero();
        assertThat(evaluate(grant, START)).isEqualTo(new Decision(true, DecisionReason.ALLOWED));
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void guardianManagerAndAdminCannotGrantOnBehalfOfPatient(AppUserRole role) {
        assertThatThrownBy(() -> grantByPatient(
                PATIENT, role, true, PATIENT, GUARDIAN, AppUserRole.GUARDIAN,
                REQUEST, FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherPatientAndUnconfirmedAdultCannotGrant() {
        assertThatThrownBy(() -> grantByPatient(
                OTHER, AppUserRole.PATIENT, true, PATIENT, GUARDIAN, AppUserRole.GUARDIAN,
                REQUEST, FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grantByPatient(
                PATIENT, AppUserRole.PATIENT, false, PATIENT, GUARDIAN, AppUserRole.GUARDIAN,
                REQUEST, FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "GUARDIAN", mode = EnumSource.Mode.EXCLUDE)
    void recipientMustHaveGuardianRole(AppUserRole role) {
        assertThatThrownBy(() -> grantByPatient(
                PATIENT, AppUserRole.PATIENT, true, PATIENT, GUARDIAN, role,
                REQUEST, FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingGrantIsDenied() {
        assertThat(evaluateCreation(Optional.empty(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START))
                .isEqualTo(new Decision(false, DecisionReason.GRANT_MISSING));
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "GUARDIAN", mode = EnumSource.Mode.EXCLUDE)
    void roleChangeCannotReuseGrant(AppUserRole role) {
        assertThat(evaluateCreation(Optional.of(grant()), GUARDIAN, role,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason())
                .isEqualTo(DecisionReason.REQUESTER_NOT_GUARDIAN);
    }

    @Test
    void anotherPatientGuardianRequestAndPolicyAreDenied() {
        assertThat(evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                OTHER, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.PATIENT_MISMATCH);
        assertThat(evaluateCreation(Optional.of(grant()), OTHER, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.GUARDIAN_MISMATCH);
        assertThat(evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, OTHER, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.REQUEST_MISMATCH);
        assertThat(evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, "booking-test-v2", START).reason())
                .isEqualTo(DecisionReason.POLICY_VERSION_MISMATCH);
    }

    @Test
    void onlyHalfOpenApprovalWindowIsAllowed() {
        assertThat(evaluate(grant(), START.minusNanos(1)).reason()).isEqualTo(DecisionReason.NOT_YET_ACTIVE);
        assertThat(evaluate(grant(), START).allowed()).isTrue();
        assertThat(evaluate(grant(), END.minusNanos(1)).allowed()).isTrue();
        assertThat(evaluate(grant(), END).reason()).isEqualTo(DecisionReason.EXPIRED);
        assertThat(evaluate(grant(), END.plusSeconds(1)).reason()).isEqualTo(DecisionReason.EXPIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hospital", "time", "health", "medication", "mobility"})
    void changedBodyNeedsNewApprovalEvenBeforeFirstInsert(String changedField) {
        String changedFingerprint = fingerprint(
                changedField.equals("hospital") ? "다른 병원" : "기준 병원",
                changedField.equals("time") ? START.plusSeconds(3600) : START,
                changedField.equals("health") ? "변경된 건강 상태" : "상태 요약",
                changedField.equals("medication") ? "변경된 복약 정보" : "복약 요약",
                changedField.equals("mobility") ? "WHEELCHAIR" : "NONE");

        assertThat(changedFingerprint).isNotEqualTo(FINGERPRINT);
        assertThat(evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, changedFingerprint, POLICY, START).reason())
                .isEqualTo(DecisionReason.REQUEST_CONTENT_MISMATCH);

        Grant approvedAgain = grantByPatient(PATIENT, AppUserRole.PATIENT, true,
                PATIENT, GUARDIAN, AppUserRole.GUARDIAN, REQUEST, changedFingerprint,
                START, END, POLICY);
        assertThat(evaluateCreation(Optional.of(approvedAgain), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, changedFingerprint, POLICY, START).allowed()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "client-provided-body", "1234"})
    void invalidFingerprintCannotBeStoredOrEvaluated(String fingerprint) {
        assertThatThrownBy(() -> grantByPatient(PATIENT, AppUserRole.PATIENT, true,
                PATIENT, GUARDIAN, AppUserRole.GUARDIAN, REQUEST, fingerprint,
                START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluateCreation(Optional.of(grant()), GUARDIAN,
                AppUserRole.GUARDIAN, PATIENT, REQUEST, fingerprint, POLICY, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patientRevocationIsIdempotentAndRejectsEvenBackdatedCreation() {
        Grant original = grant();
        Grant revoked = revokeByPatient(original, PATIENT, AppUserRole.PATIENT, START.plusSeconds(10));

        assertThat(revoked.id()).isEqualTo(original.id());
        assertThat(revoked.clientRequestId()).isEqualTo(REQUEST);
        assertThat(revoked.requestFingerprint()).isEqualTo(original.requestFingerprint());
        assertThat(revoked.revokedByUserId()).isEqualTo(PATIENT);
        assertThat(revoked.version()).isEqualTo(1);
        assertThat(evaluate(revoked, START).reason()).isEqualTo(DecisionReason.REVOKED);
        assertThat(evaluate(revoked, START.plusSeconds(10)).reason()).isEqualTo(DecisionReason.REVOKED);
        assertThat(revokeByPatient(revoked, PATIENT, AppUserRole.PATIENT, START.plusSeconds(20)))
                .isSameAs(revoked);
        assertThat(original.revokedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void onlyPatientRoleCanRevoke(AppUserRole role) {
        assertThatThrownBy(() -> revokeByPatient(grant(), PATIENT, role, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherPatientAndTimeBeforeApprovalCannotRevoke() {
        assertThatThrownBy(() -> revokeByPatient(grant(), OTHER, AppUserRole.PATIENT, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revokeByPatient(grant(), PATIENT, AppUserRole.PATIENT, START.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedStoredGrantIsRejected() {
        assertThatThrownBy(() -> stored(PATIENT, null, null, END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Grant(UUID.randomUUID(), PATIENT, GUARDIAN, REQUEST, FINGERPRINT,
                POLICY, OTHER, START, END, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, PATIENT, null, END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, START, END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, OTHER, START, END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, PATIENT, START.minusNanos(1), END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, START, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, START.minusSeconds(1), 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, END, -1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, END, 0, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absentRequestIdentityOrCurrentPolicyCannotAllowCreation() {
        assertThatThrownBy(() -> new Grant(UUID.randomUUID(), PATIENT, GUARDIAN, null, FINGERPRINT,
                POLICY, PATIENT, START, END, null, null, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, null, FINGERPRINT, POLICY, START)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> evaluateCreation(Optional.of(grant()), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, " ", START)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contradictoryDecisionIsRejected() {
        assertThatThrownBy(() -> new Decision(true, DecisionReason.REVOKED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Decision(false, DecisionReason.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Grant grant() {
        return grantByPatient(PATIENT, AppUserRole.PATIENT, true, PATIENT, GUARDIAN,
                AppUserRole.GUARDIAN, REQUEST, FINGERPRINT, START, END, " " + POLICY + " ");
    }

    private static String fingerprint(String hospital, Instant appointmentAt,
            String health, String medication, String mobility) {
        return AppointmentCreateFingerprint.from(new AppointmentCreateFingerprint.CreateRequest(
                GUARDIAN, AppUserRole.GUARDIAN, REQUEST,
                "대상 환자", "01000000000", null, health, medication,
                hospital, "내과", 37.5, 127.0, appointmentAt,
                "병원 입구", "주의 사항", mobility, "ROUND_TRIP", "ANY", "BANK_TRANSFER", null));
    }

    private static Decision evaluate(Grant grant, Instant at) {
        return evaluateCreation(Optional.of(grant), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, at);
    }

    private static Grant stored(UUID guardian, UUID revokedBy, Instant revokedAt,
            Instant expiresAt, long version, String policyVersion) {
        return new Grant(UUID.randomUUID(), PATIENT, guardian, REQUEST, FINGERPRINT, policyVersion,
                PATIENT, START, expiresAt, revokedBy, revokedAt, version);
    }
}
