package com.bodeul.core.consent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;
import com.bodeul.core.consent.GuardianBookingApprovalRepository.RequestKey;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class JdbcGuardianBookingApprovalRepositoryTests {

    private final JdbcClient jdbc = mock(JdbcClient.class);
    private final JdbcGuardianBookingApprovalRepository repository = new JdbcGuardianBookingApprovalRepository(jdbc);
    private final RequestKey key = new RequestKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Test
    void lockWithoutTransactionFailsBeforeQuery() {
        assertThatThrownBy(() -> repository.lockCurrent(key)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void lockInReadOnlyTransactionFailsBeforeQuery() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        try {
            assertThatThrownBy(() -> repository.lockCurrent(key)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(jdbc);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void pendingStateCannotResetStoredApproval() {
        assertThatThrownBy(() -> repository.save(key.pending(), 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void invalidExpectedVersionFailsBeforeQuery() {
        ApprovalState granted = grant();
        for (long expected : new long[] {-1, 1, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> repository.save(granted, expected))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void initialStateCannotBeARevocation() {
        var original = grant().grant().orElseThrow();
        var revoked = new AdultPatientGuardianBookingPolicy.Grant(
                original.id(), key.patientUserId(), key.guardianUserId(), key.clientRequestId(),
                original.requestFingerprint(), original.policyVersion(), key.patientUserId(),
                original.grantedAt(), original.expiresAt(), key.patientUserId(), original.grantedAt(), 1);
        var state = new ApprovalState(key.patientUserId(), key.guardianUserId(), key.clientRequestId(),
                1, Optional.of(revoked));
        assertThatThrownBy(() -> repository.save(state, 0)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    private ApprovalState grant() {
        Instant now = Instant.parse("2026-09-29T00:00:00Z");
        return AdultPatientGuardianBookingPolicy.grantByPatient(key.pending(), 0, key.patientUserId(),
                AppUserRole.PATIENT, true, AppUserRole.GUARDIAN, "a".repeat(64), now, now.plusSeconds(60), "test-v1");
    }
}
