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
        ApprovalState state = approved();
        Grant grant = state.grant().orElseThrow();

        assertThat(grant.id()).isNotNull();
        assertThat(grant.patientUserId()).isEqualTo(PATIENT);
        assertThat(grant.guardianUserId()).isEqualTo(GUARDIAN);
        assertThat(grant.clientRequestId()).isEqualTo(REQUEST);
        assertThat(grant.requestFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(grant.grantedByUserId()).isEqualTo(PATIENT);
        assertThat(grant.policyVersion()).isEqualTo(POLICY);
        assertThat(grant.revokedAt()).isNull();
        assertThat(grant.version()).isEqualTo(1);
        assertThat(state.version()).isEqualTo(grant.version());
        assertThat(evaluate(state, START)).isEqualTo(new Decision(true, DecisionReason.ALLOWED));
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void guardianManagerAndAdminCannotGrantOnBehalfOfPatient(AppUserRole role) {
        assertThatThrownBy(() -> grantByPatient(
                pending(), 0, PATIENT, role, true, AppUserRole.GUARDIAN,
                FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anotherPatientAndUnconfirmedAdultCannotGrant() {
        assertThatThrownBy(() -> grantByPatient(
                pending(), 0, OTHER, AppUserRole.PATIENT, true, AppUserRole.GUARDIAN,
                FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> grantByPatient(
                pending(), 0, PATIENT, AppUserRole.PATIENT, false, AppUserRole.GUARDIAN,
                FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "GUARDIAN", mode = EnumSource.Mode.EXCLUDE)
    void recipientMustHaveGuardianRole(AppUserRole role) {
        assertThatThrownBy(() -> grantByPatient(
                pending(), 0, PATIENT, AppUserRole.PATIENT, true, role,
                FINGERPRINT, START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingGrantIsDenied() {
        assertThat(evaluateCreation(pending(), Optional.empty(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START))
                .isEqualTo(new Decision(false, DecisionReason.GRANT_MISSING));
        ApprovalState state = approved();
        assertThat(evaluateCreation(state, Optional.empty(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.GRANT_MISSING);
        assertThat(evaluateCreation(pending(), state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.GRANT_MISSING);
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "GUARDIAN", mode = EnumSource.Mode.EXCLUDE)
    void roleChangeCannotReuseGrant(AppUserRole role) {
        ApprovalState state = approved();
        assertThat(evaluateCreation(state, state.grant(), GUARDIAN, role,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason())
                .isEqualTo(DecisionReason.REQUESTER_NOT_GUARDIAN);
    }

    @Test
    void anotherPatientGuardianRequestAndPolicyAreDenied() {
        ApprovalState state = approved();
        assertThat(evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                OTHER, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.PATIENT_MISMATCH);
        assertThat(evaluateCreation(state, state.grant(), OTHER, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.GUARDIAN_MISMATCH);
        assertThat(evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, OTHER, FINGERPRINT, POLICY, START).reason()).isEqualTo(DecisionReason.REQUEST_MISMATCH);
        assertThat(evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, "booking-test-v2", START).reason())
                .isEqualTo(DecisionReason.POLICY_VERSION_MISMATCH);
    }

    @Test
    void onlyHalfOpenApprovalWindowIsAllowed() {
        ApprovalState state = approved();
        assertThat(evaluate(state, START.minusNanos(1)).reason()).isEqualTo(DecisionReason.NOT_YET_ACTIVE);
        assertThat(evaluate(state, START).allowed()).isTrue();
        assertThat(evaluate(state, END.minusNanos(1)).allowed()).isTrue();
        assertThat(evaluate(state, END).reason()).isEqualTo(DecisionReason.EXPIRED);
        assertThat(evaluate(state, END.plusSeconds(1)).reason()).isEqualTo(DecisionReason.EXPIRED);
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

        ApprovalState original = approved();
        Grant oldGrant = original.grant().orElseThrow();
        assertThat(changedFingerprint).isNotEqualTo(FINGERPRINT);
        assertThat(evaluateCreation(original, original.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, changedFingerprint, POLICY, START).reason())
                .isEqualTo(DecisionReason.REQUEST_CONTENT_MISMATCH);

        ApprovalState approvedAgain = reapprove(original, changedFingerprint, START);
        assertThat(approvedAgain.version()).isEqualTo(2);
        assertThat(approvedAgain.grant().orElseThrow().id()).isNotEqualTo(oldGrant.id());
        assertThat(evaluateCreation(approvedAgain, approvedAgain.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, changedFingerprint, POLICY, START).allowed()).isTrue();
        assertThat(evaluate(approvedAgain, START).reason()).isEqualTo(DecisionReason.REQUEST_CONTENT_MISMATCH);
        assertThat(evaluateCandidate(approvedAgain, oldGrant, FINGERPRINT, START).reason())
                .isEqualTo(DecisionReason.GRANT_SUPERSEDED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "client-provided-body", "1234"})
    void invalidFingerprintCannotBeStoredOrEvaluated(String fingerprint) {
        ApprovalState state = approved();
        assertThatThrownBy(() -> grantByPatient(pending(), 0, PATIENT, AppUserRole.PATIENT, true,
                AppUserRole.GUARDIAN, fingerprint,
                START, END, POLICY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluateCreation(state, state.grant(), GUARDIAN,
                AppUserRole.GUARDIAN, PATIENT, REQUEST, fingerprint, POLICY, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patientRevocationIsIdempotentAndRejectsEvenBackdatedCreation() {
        ApprovalState original = approved();
        Grant oldGrant = original.grant().orElseThrow();
        ApprovalState revokedState = revokeByPatient(original, original.version(),
                PATIENT, AppUserRole.PATIENT, START.plusSeconds(10));
        Grant revoked = revokedState.grant().orElseThrow();

        assertThat(revoked.id()).isEqualTo(oldGrant.id());
        assertThat(revoked.clientRequestId()).isEqualTo(REQUEST);
        assertThat(revoked.requestFingerprint()).isEqualTo(oldGrant.requestFingerprint());
        assertThat(revoked.revokedByUserId()).isEqualTo(PATIENT);
        assertThat(revoked.version()).isEqualTo(2);
        assertThat(evaluate(revokedState, START).reason()).isEqualTo(DecisionReason.REVOKED);
        assertThat(evaluate(revokedState, START.plusSeconds(10)).reason()).isEqualTo(DecisionReason.REVOKED);
        assertThat(evaluateCandidate(revokedState, oldGrant, FINGERPRINT, START).reason())
                .isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        assertThat(revokeByPatient(revokedState, revokedState.version(),
                PATIENT, AppUserRole.PATIENT, START.plusSeconds(20))).isSameAs(revokedState);
        assertThat(oldGrant.revokedAt()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AppUserRole.class, names = "PATIENT", mode = EnumSource.Mode.EXCLUDE)
    void onlyPatientRoleCanRevoke(AppUserRole role) {
        assertThatThrownBy(() -> revokeByPatient(approved(), 1, PATIENT, role, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherPatientAndTimeBeforeApprovalCannotRevoke() {
        assertThatThrownBy(() -> revokeByPatient(approved(), 1, OTHER, AppUserRole.PATIENT, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revokeByPatient(approved(), 1, PATIENT, AppUserRole.PATIENT, START.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedStoredGrantIsRejected() {
        assertThatThrownBy(() -> stored(PATIENT, null, null, END, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Grant(UUID.randomUUID(), PATIENT, GUARDIAN, REQUEST, FINGERPRINT,
                POLICY, OTHER, START, END, null, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, PATIENT, null, END, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, START, END, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, OTHER, START, END, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, PATIENT, START.minusNanos(1), END, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, START, 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, START.minusSeconds(1), 1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, END, -1, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, END, 0, POLICY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> stored(GUARDIAN, null, null, END, 1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void absentRequestIdentityOrCurrentPolicyCannotAllowCreation() {
        ApprovalState state = approved();
        assertThatThrownBy(() -> new Grant(UUID.randomUUID(), PATIENT, GUARDIAN, null, FINGERPRINT,
                POLICY, PATIENT, START, END, null, null, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, null, FINGERPRINT, POLICY, START)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, " ", START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluateCreation(null, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, START)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void sameBodyReapprovalStillRejectsPreviousGrantIdentity() {
        ApprovalState original = approved();
        ApprovalState replacement = reapprove(original, FINGERPRINT, START.plusSeconds(1));

        assertThat(evaluate(replacement, START.plusSeconds(1)).allowed()).isTrue();
        assertThat(evaluateCandidate(replacement, original.grant().orElseThrow(),
                FINGERPRINT, START.plusSeconds(1)).reason()).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
    }

    @Test
    void changingBodyBackDoesNotReviveAnEarlierGeneration() {
        ApprovalState first = approved();
        String changed = fingerprint("다른 병원", START, "상태 요약", "복약 요약", "NONE");
        ApprovalState second = reapprove(first, changed, START.plusSeconds(1));
        ApprovalState third = reapprove(second, FINGERPRINT, START.plusSeconds(2));

        assertThat(third.version()).isEqualTo(3);
        assertThat(evaluate(third, START.plusSeconds(2)).allowed()).isTrue();
        assertThat(evaluateCandidate(third, first.grant().orElseThrow(), FINGERPRINT, START.plusSeconds(2)).reason())
                .isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        assertThat(evaluateCandidate(third, second.grant().orElseThrow(), changed, START.plusSeconds(2)).reason())
                .isEqualTo(DecisionReason.GRANT_SUPERSEDED);
    }

    @Test
    void staleGrantAndRevokeCommandsCannotReplaceCurrentState() {
        ApprovalState original = approved();
        ApprovalState replacement = reapprove(original, FINGERPRINT, START.plusSeconds(1));

        assertThatThrownBy(() -> grantByPatient(replacement, original.version(), PATIENT,
                AppUserRole.PATIENT, true, AppUserRole.GUARDIAN, FINGERPRINT, START.plusSeconds(2), END, POLICY))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("최신 상태");
        assertThatThrownBy(() -> revokeByPatient(replacement, original.version(),
                PATIENT, AppUserRole.PATIENT, START.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("최신 상태");
        assertThat(evaluate(replacement, START.plusSeconds(2)).allowed()).isTrue();
    }

    @Test
    void sameVersionCompetingReplacementDoesNotAuthorizeLosingGrant() {
        ApprovalState original = approved();
        ApprovalState winner = reapprove(original, FINGERPRINT, START.plusSeconds(1));
        ApprovalState loser = reapprove(original, FINGERPRINT, START.plusSeconds(1));

        assertThat(winner.version()).isEqualTo(loser.version());
        assertThat(winner.grant().orElseThrow().id()).isNotEqualTo(loser.grant().orElseThrow().id());
        assertThat(evaluateCandidate(winner, loser.grant().orElseThrow(),
                FINGERPRINT, START.plusSeconds(1)).reason()).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        assertThat(evaluate(winner, START.plusSeconds(1)).allowed()).isTrue();
    }

    @Test
    void candidateWithCurrentIdAndVersionCannotSubstituteAnotherBody() {
        ApprovalState current = approved();
        Grant grant = current.grant().orElseThrow();
        String changed = fingerprint("다른 병원", START, "상태 요약", "복약 요약", "NONE");
        Grant substituted = new Grant(grant.id(), PATIENT, GUARDIAN, REQUEST, changed,
                POLICY, PATIENT, START, END, null, null, grant.version());

        assertThat(evaluateCandidate(current, substituted, changed, START).reason())
                .isEqualTo(DecisionReason.GRANT_SUPERSEDED);
    }

    @Test
    void reapprovalAfterRevocationPreservesGenerationAndRejectsOldSnapshots() {
        ApprovalState original = approved();
        ApprovalState revoked = revokeByPatient(original, 1, PATIENT, AppUserRole.PATIENT, START.plusSeconds(1));
        ApprovalState replacement = reapprove(revoked, FINGERPRINT, START.plusSeconds(2));

        assertThat(replacement.version()).isEqualTo(3);
        assertThat(replacement.grant().orElseThrow().revokedAt()).isNull();
        assertThat(evaluate(replacement, START.plusSeconds(2)).allowed()).isTrue();
        assertThat(evaluateCandidate(replacement, original.grant().orElseThrow(),
                FINGERPRINT, START.plusSeconds(2)).reason()).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        assertThat(evaluateCandidate(replacement, revoked.grant().orElseThrow(),
                FINGERPRINT, START.plusSeconds(2)).reason()).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
    }

    @Test
    void reapprovalCannotPrecedeLastApprovalOrRevocation() {
        ApprovalState original = approved();
        ApprovalState revoked = revokeByPatient(original, 1, PATIENT, AppUserRole.PATIENT, START.plusSeconds(10));

        assertThatThrownBy(() -> reapprove(original, FINGERPRINT, START.minusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재승인 시각");
        assertThatThrownBy(() -> reapprove(revoked, FINGERPRINT, START.plusSeconds(9)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("재승인 시각");
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "guardian", "request", "version"})
    void currentStateCannotContainAnApprovalForAnotherKeyOrVersion(String mismatch) {
        ApprovalState current = approved();

        assertThatThrownBy(() -> new ApprovalState(
                mismatch.equals("patient") ? OTHER : PATIENT,
                mismatch.equals("guardian") ? OTHER : GUARDIAN,
                mismatch.equals("request") ? OTHER : REQUEST,
                mismatch.equals("version") ? 2 : current.version(), current.grant()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("요청 키·버전");
    }

    @Test
    void emptyStateCannotDiscardARevisionAndPendingStateCannotBeRevoked() {
        assertThat(pending().version()).isZero();
        assertThat(pending().grant()).isEmpty();
        assertThatThrownBy(() -> new ApprovalState(PATIENT, GUARDIAN, REQUEST, 1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApprovalState(PATIENT, GUARDIAN, REQUEST, -1, Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApprovalState.pending(PATIENT, PATIENT, REQUEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> revokeByPatient(pending(), 0, PATIENT, AppUserRole.PATIENT, START))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("철회할");
    }

    @Test
    void revisionOverflowFailsClosedForReapprovalAndRevocation() {
        Grant maximum = stored(GUARDIAN, null, null, END, Long.MAX_VALUE, POLICY);
        ApprovalState current = new ApprovalState(PATIENT, GUARDIAN, REQUEST, Long.MAX_VALUE, Optional.of(maximum));

        assertThatThrownBy(() -> reapprove(current, FINGERPRINT, START)).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> revokeByPatient(current, Long.MAX_VALUE, PATIENT, AppUserRole.PATIENT, START))
                .isInstanceOf(ArithmeticException.class);
        assertThat(current.version()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void contradictoryDecisionIsRejected() {
        assertThatThrownBy(() -> new Decision(true, DecisionReason.REVOKED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Decision(false, DecisionReason.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ApprovalState pending() {
        return ApprovalState.pending(PATIENT, GUARDIAN, REQUEST);
    }

    private static ApprovalState approved() {
        return grantByPatient(pending(), 0, PATIENT, AppUserRole.PATIENT, true,
                AppUserRole.GUARDIAN, FINGERPRINT, START, END, " " + POLICY + " ");
    }

    private static ApprovalState reapprove(ApprovalState current, String fingerprint, Instant at) {
        return grantByPatient(current, current.version(), PATIENT, AppUserRole.PATIENT, true,
                AppUserRole.GUARDIAN, fingerprint, at, END, POLICY);
    }

    private static String fingerprint(String hospital, Instant appointmentAt,
            String health, String medication, String mobility) {
        return AppointmentCreateFingerprint.from(new AppointmentCreateFingerprint.CreateRequest(
                GUARDIAN, AppUserRole.GUARDIAN, REQUEST,
                "대상 환자", "01000000000", null, health, medication,
                hospital, "내과", 37.5, 127.0, appointmentAt,
                "병원 입구", "주의 사항", mobility, "ROUND_TRIP", "ANY", "BANK_TRANSFER", null));
    }

    private static Decision evaluate(ApprovalState state, Instant at) {
        return evaluateCreation(state, state.grant(), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, FINGERPRINT, POLICY, at);
    }

    private static Decision evaluateCandidate(ApprovalState current, Grant candidate, String fingerprint, Instant at) {
        return evaluateCreation(current, Optional.of(candidate), GUARDIAN, AppUserRole.GUARDIAN,
                PATIENT, REQUEST, fingerprint, POLICY, at);
    }

    private static Grant stored(UUID guardian, UUID revokedBy, Instant revokedAt,
            Instant expiresAt, long version, String policyVersion) {
        return new Grant(UUID.randomUUID(), PATIENT, guardian, REQUEST, FINGERPRINT, policyVersion,
                PATIENT, START, expiresAt, revokedBy, revokedAt, version);
    }
}
