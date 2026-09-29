package com.bodeul.core.consent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.DecisionReason;
import com.bodeul.core.consent.GuardianBookingApprovalRepository.RequestKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** 공개 환경에 연결하지 않고 CI의 전용 PostgreSQL DB만 사용한다. */
@Tag("postgres-booking-approval")
class GuardianBookingApprovalPostgresTests {

    private static final Instant NOW = Instant.parse("2026-09-29T00:00:00.123456Z");
    private static final UUID PATIENT = UUID.fromString("a1900000-0000-0000-0000-000000000001");
    private static final UUID GUARDIAN = UUID.fromString("a1900000-0000-0000-0000-000000000002");
    private static final String FINGERPRINT = "a".repeat(64);

    private DriverManagerDataSource ownerDataSource;
    private JdbcTemplate owner;
    private JdbcGuardianBookingApprovalRepository repository;
    private TransactionTemplate transaction;
    private RequestKey key;

    @BeforeEach
    void prepareIsolatedDatabase() {
        String url = System.getenv("BOOKING_TEST_DB_URL");
        assertThat(url).matches("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/bodeul_guardian_booking_test$");
        ownerDataSource = dataSource(null);
        owner = new JdbcTemplate(ownerDataSource);
        assertThat(owner.queryForObject("select current_database()", String.class))
                .isEqualTo("bodeul_guardian_booking_test");
        owner.execute("truncate bodeul.guardian_booking_approval_events, bodeul.guardian_booking_approvals");
        owner.update("""
                insert into bodeul.app_users (id, firebase_uid, role) values
                (?, 'booking-test-patient', 'PATIENT'), (?, 'booking-test-guardian', 'GUARDIAN')
                on conflict (id) do nothing
                """, PATIENT, GUARDIAN);
        DriverManagerDataSource runtime = dataSource("bodeul_core_runtime");
        repository = new JdbcGuardianBookingApprovalRepository(JdbcClient.create(runtime));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(runtime));
        transaction.setTimeout(15);
        key = new RequestKey(PATIENT, GUARDIAN, UUID.randomUUID());
    }

    @Test
    void initialApprovalRoundTripsAndAppendsOneSnapshot() {
        assertThat(repository.findCurrent(key)).isEqualTo(key.pending());
        ApprovalState proposed = grant(key.pending(), FINGERPRINT);
        ApprovalState saved = repository.save(proposed, 0);
        assertThat(saved).isEqualTo(proposed);
        assertThat(repository.findCurrent(key)).isEqualTo(saved);
        assertThat(eventCount()).isEqualTo(1);
        assertThat(owner.queryForObject("select action from bodeul.guardian_booking_approval_events", String.class))
                .isEqualTo("GRANTED");
        assertThat(owner.queryForObject("select actor_user_id from bodeul.guardian_booking_approval_events", UUID.class))
                .isEqualTo(PATIENT);
        assertThat(owner.queryForObject("select count(*) from bodeul.guardian_sharing_consents", Integer.class)).isZero();
        assertThat(owner.queryForObject("select count(*) from bodeul.appointment_requests", Integer.class)).isZero();
    }

    @Test
    void concurrentFirstApprovalHasExactlyOneWinner() throws Exception {
        ApprovalState first = grant(key.pending(), FINGERPRINT);
        ApprovalState second = grant(key.pending(), FINGERPRINT);
        assertOneWinner(first, second, 0);
        assertThat(repository.findCurrent(key)).isIn(first, second);
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void concurrentReapprovalAndRevocationCannotOverwriteEachOther() throws Exception {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        ApprovalState renewed = grant(initial, "b".repeat(64));
        ApprovalState revoked = revoke(initial);
        assertOneWinner(renewed, revoked, 1);
        assertThat(repository.findCurrent(key)).isIn(renewed, revoked);
        assertThat(eventCount()).isEqualTo(2);
    }

    @Test
    void sameContentReapprovalAndReturningToOlderContentDoNotReviveOldGrant() {
        ApprovalState original = repository.save(grant(key.pending(), FINGERPRINT), 0);
        ApprovalState same = repository.save(grant(original, FINGERPRINT), 1);
        assertThat(creationDecision(same, original)).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        ApprovalState changed = repository.save(grant(same, "b".repeat(64)), 2);
        ApprovalState returned = repository.save(grant(changed, FINGERPRINT), 3);
        assertThat(creationDecision(returned, original)).isEqualTo(DecisionReason.GRANT_SUPERSEDED);
        assertThat(eventCount()).isEqualTo(4);
    }

    @Test
    void revocationAndReapprovalPreserveMonotonicHistory() {
        ApprovalState original = repository.save(grant(key.pending(), FINGERPRINT), 0);
        ApprovalState revoked = repository.save(revoke(original), 1);
        assertThat(creationDecision(revoked, revoked)).isEqualTo(DecisionReason.REVOKED);
        assertThat(revoke(revoked)).isEqualTo(revoked);
        ApprovalState reapproved = repository.save(grant(revoked, FINGERPRINT), 2);
        assertThat(reapproved.version()).isEqualTo(3);
        assertThat(reapproved.grant().orElseThrow().id()).isNotEqualTo(original.grant().orElseThrow().id());
        assertThat(owner.queryForList("select action from bodeul.guardian_booking_approval_events order by version", String.class))
                .containsExactly("GRANTED", "REVOKED", "GRANTED");
        assertThatThrownBy(() -> repository.save(grant(original, FINGERPRINT), 1))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findCurrent(key)).isEqualTo(reapproved);
        assertThat(eventCount()).isEqualTo(3);
    }

    @Test
    void missingRowCannotBeInsertedWithNonzeroExpectedVersion() {
        ApprovalState nonexistent = grant(key.pending(), FINGERPRINT);
        assertThatThrownBy(() -> repository.save(grant(nonexistent, FINGERPRINT), 1))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findCurrent(key)).isEqualTo(key.pending());
        assertThat(eventCount()).isZero();
    }

    @Test
    void reapprovalCannotReuseCurrentGrantIdentity() {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        var value = initial.grant().orElseThrow();
        var reused = new AdultPatientGuardianBookingPolicy.Grant(
                value.id(), PATIENT, GUARDIAN, key.clientRequestId(), FINGERPRINT, value.policyVersion(),
                PATIENT, NOW.plusSeconds(10), value.expiresAt(), null, null, 2);
        assertRejectedTransition(initial, reused);
    }

    @Test
    void revocationCannotChangePreviouslyApprovedContent() {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        var value = initial.grant().orElseThrow();
        var altered = new AdultPatientGuardianBookingPolicy.Grant(
                value.id(), PATIENT, GUARDIAN, key.clientRequestId(), "b".repeat(64), value.policyVersion(),
                PATIENT, value.grantedAt(), value.expiresAt(), PATIENT, NOW.plusSeconds(10), 2);
        assertRejectedTransition(initial, altered);
    }

    @Test
    void reapprovalCannotPredateLatestRevocation() {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        ApprovalState revoked = repository.save(revoke(initial), 1);
        var value = initial.grant().orElseThrow();
        var backdated = new AdultPatientGuardianBookingPolicy.Grant(
                UUID.randomUUID(), PATIENT, GUARDIAN, key.clientRequestId(), FINGERPRINT, value.policyVersion(),
                PATIENT, NOW, value.expiresAt(), null, null, 3);
        assertRejectedTransition(revoked, backdated);
    }

    @Test
    void requestKeysKeepIndependentApprovalStates() {
        ApprovalState first = repository.save(grant(key.pending(), FINGERPRINT), 0);
        RequestKey anotherRequest = new RequestKey(PATIENT, GUARDIAN, UUID.randomUUID());
        ApprovalState second = repository.save(grant(anotherRequest.pending(), FINGERPRINT), 0);
        assertThat(repository.findCurrent(key)).isEqualTo(first);
        assertThat(repository.findCurrent(anotherRequest)).isEqualTo(second);
        assertThat(eventCount()).isEqualTo(2);
    }

    @Test
    void failedAuditInsertRollsBackTheStateEvenWithoutCallerTransaction() {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        // 감사 충돌을 일부러 만들어 두 번째 INSERT 실패가 앞선 UPDATE도 취소하는지 확인한다.
        owner.update("""
                insert into bodeul.guardian_booking_approval_events
                select patient_user_id, guardian_user_id, client_request_id, version + 1,
                       grant_id, request_fingerprint, policy_version, granted_by_user_id,
                       granted_at, expires_at, revoked_by_user_id, revoked_at, action, actor_user_id, occurred_at
                from bodeul.guardian_booking_approval_events
                """);
        assertThatThrownBy(() -> repository.save(grant(initial, "b".repeat(64)), 1))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.findCurrent(key)).isEqualTo(initial);
        assertThat(eventCount()).isEqualTo(2);
    }

    @Test
    void callerRollbackCancelsBothStateAndEvent() {
        transaction.executeWithoutResult(status -> {
            repository.save(grant(key.pending(), FINGERPRINT), 0);
            assertThat(repository.lockCurrent(key).version()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(repository.findCurrent(key)).isEqualTo(key.pending());
        assertThat(eventCount()).isZero();
    }

    @Test
    void creationReadLockBlocksRevocationUntilCallerCommits() throws Exception {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = transaction.execute(status -> {
                ApprovalState locked = repository.lockCurrent(key);
                var writer = executor.submit(() -> repository.save(revoke(initial), 1));
                awaitBlockedWriter();
                assertThat(writer.isDone()).isFalse();
                assertThat(creationDecision(locked, initial)).isEqualTo(DecisionReason.ALLOWED);
                return writer;
            });
            assertThat(future).isNotNull();
            assertThat(future.get(10, TimeUnit.SECONDS).version()).isEqualTo(2);
        }
        assertThat(creationDecision(repository.findCurrent(key), repository.findCurrent(key)))
                .isEqualTo(DecisionReason.REVOKED);
    }

    @Test
    void creationReadWaitsForUncommittedRevocationAndSeesRevokedState() throws Exception {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = transaction.execute(status -> {
                ApprovalState revoked = repository.save(revoke(initial), 1);
                var reader = executor.submit(() -> transaction.execute(ignored -> repository.lockCurrent(key)));
                awaitBlockedWriter();
                assertThat(reader.isDone()).isFalse();
                assertThat(revoked.version()).isEqualTo(2);
                return reader;
            });
            assertThat(future).isNotNull();
            ApprovalState current = future.get(10, TimeUnit.SECONDS);
            assertThat(creationDecision(current, current)).isEqualTo(DecisionReason.REVOKED);
        }
    }

    @Test
    void lockingMissingApprovalReturnsPendingAndDoesNotGrantPermission() {
        ApprovalState current = transaction.execute(status -> repository.lockCurrent(key));
        assertThat(current).isEqualTo(key.pending());
        assertThat(creationDecision(current, current)).isEqualTo(DecisionReason.GRANT_MISSING);
        assertThat(eventCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"anon", "authenticated", "service_role", "bodeul_admin_runtime", "bodeul_admin_service"})
    void nonCoreRolesCannotReadOrWriteApprovals(String role) {
        repository.save(grant(key.pending(), FINGERPRINT), 0);
        JdbcTemplate unauthorized = new JdbcTemplate(dataSource(role));
        for (String table : List.of("guardian_booking_approvals", "guardian_booking_approval_events")) {
            for (String privilege : List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE")) {
                assertThat(owner.queryForObject("select has_table_privilege(?, ?, ?)", Boolean.class,
                        role, "bodeul." + table, privilege)).isFalse();
            }
            assertThatThrownBy(() -> unauthorized.queryForList("select * from bodeul." + table))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Test
    void runtimeCannotRewriteAuditOrDeleteApprovalAndObjectsKeepMigrationOwnership() {
        repository.save(grant(key.pending(), FINGERPRINT), 0);
        JdbcTemplate runtime = new JdbcTemplate(dataSource("bodeul_core_runtime"));
        for (String sql : List.of(
                "update bodeul.guardian_booking_approval_events set policy_version = 'changed'",
                "delete from bodeul.guardian_booking_approval_events",
                "truncate bodeul.guardian_booking_approval_events",
                "delete from bodeul.guardian_booking_approvals")) {
            assertThatThrownBy(() -> runtime.execute(sql)).isInstanceOf(DataAccessException.class);
        }
        assertThat(owner.queryForList("""
                select pg_get_userbyid(relowner) from pg_class
                where oid in ('bodeul.guardian_booking_approvals'::regclass,
                              'bodeul.guardian_booking_approval_events'::regclass)
                  and relrowsecurity
                """, String.class)).containsExactlyInAnyOrder("bodeul_migration", "bodeul_migration");
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void databaseRejectsInvalidFingerprintVersionAndPatientIdentity() {
        repository.save(grant(key.pending(), FINGERPRINT), 0);
        for (String assignment : List.of(
                "request_fingerprint = 'invalid'", "version = 0", "guardian_user_id = patient_user_id",
                "granted_by_user_id = guardian_user_id", "expires_at = granted_at",
                "revoked_at = granted_at", "revoked_by_user_id = patient_user_id")) {
            assertThatThrownBy(() -> owner.execute("update bodeul.guardian_booking_approvals set " + assignment))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void schemaRollbackRefusesToDeleteExistingHistory() throws Exception {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        String rollback = Files.readString(Path.of("db/rollback/V24__remove_guardian_booking_approvals.sql"),
                StandardCharsets.UTF_8);
        try (Connection connection = ownerDataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> statement.execute(rollback))
                    .isInstanceOf(SQLException.class)
                    .extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("55000");
            connection.rollback();
        }
        assertThat(repository.findCurrent(key)).isEqualTo(initial);
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void guardianAuditCountsUseSelectiveIndexesWithoutPlannerHints() throws Exception {
        repository.save(grant(key.pending(), FINGERPRINT), 0);
        TransactionTemplate fixtureTransaction = new TransactionTemplate(new DataSourceTransactionManager(ownerDataSource));
        List<String> plans = fixtureTransaction.execute(status -> {
            status.setRollbackOnly();
            UUID otherGuardian = UUID.fromString("a1900000-0000-0000-0000-000000000003");
            owner.update("insert into bodeul.app_users (id, firebase_uid, role) values (?, 'booking-index-guardian', 'GUARDIAN')",
                    otherGuardian);
            // 소수 보호자의 조회 선택성을 검증한다. 합성 행은 이 트랜잭션 종료 시 모두 되돌린다.
            owner.update("""
                    insert into bodeul.guardian_booking_approvals
                        (patient_user_id, guardian_user_id, client_request_id, version, grant_id,
                         request_fingerprint, policy_version, granted_by_user_id, granted_at, expires_at)
                    select ?, ?, md5('booking-audit-index-' || fixture_id)::uuid, 1, gen_random_uuid(),
                           repeat('a', 64), 'test-v1', ?, timestamptz '2026-09-29 00:00:00+00',
                           timestamptz '2026-09-29 01:00:00+00'
                    from generate_series(1, 10000) as fixture(fixture_id)
                    """, PATIENT, otherGuardian, PATIENT);
            owner.update("""
                    insert into bodeul.guardian_booking_approval_events
                        (patient_user_id, guardian_user_id, client_request_id, version, grant_id,
                         request_fingerprint, policy_version, granted_by_user_id, granted_at, expires_at,
                         action, actor_user_id, occurred_at)
                    select patient_user_id, guardian_user_id, client_request_id, version, grant_id,
                           request_fingerprint, policy_version, granted_by_user_id, granted_at, expires_at,
                           'GRANTED', patient_user_id, granted_at
                    from bodeul.guardian_booking_approvals where guardian_user_id = ?
                    """, otherGuardian);
            owner.execute("analyze bodeul.guardian_booking_approval_events");
            owner.execute("set local role bodeul_core_runtime");
            assertThat(owner.queryForObject("select current_user", String.class)).isEqualTo("bodeul_core_runtime");
            assertThat(owner.queryForObject("""
                    select count(*) from bodeul.guardian_booking_approval_events
                    where patient_user_id = ? or guardian_user_id = ?
                    """, Integer.class, GUARDIAN, GUARDIAN)).isEqualTo(1);
            return List.of(
                    owner.queryForObject("""
                            explain (analyze, buffers, format json)
                            select count(*) from bodeul.guardian_booking_approval_events
                            where patient_user_id = ? or guardian_user_id = ?
                            """, String.class, GUARDIAN, GUARDIAN),
                    owner.queryForObject("""
                            explain (analyze, buffers, format json)
                            select count(*) from bodeul.guardian_booking_approval_events where guardian_user_id = ?
                            """, String.class, GUARDIAN));
        });
        assertThat(plans).hasSize(2);
        for (String json : plans) {
            JsonNode plan = new ObjectMapper().readTree(json).get(0).path("Plan");
            assertThat(plan.findValuesAsText("Index Name"))
                    .contains("ix_guardian_booking_approval_events_guardian");
            assertThat(plan.findValuesAsText("Node Type")).doesNotContain("Seq Scan");
        }
        JsonNode inventoryPlan = new ObjectMapper().readTree(plans.get(0)).get(0).path("Plan");
        assertThat(inventoryPlan.findValuesAsText("Index Name"))
                .contains("guardian_booking_approval_events_pkey");
        assertThat(inventoryPlan.findValuesAsText("Node Type")).contains("BitmapOr");
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void auditIndexRollbackAndReapplyPreserveApprovalAndAuditSnapshots() throws Exception {
        ApprovalState initial = repository.save(grant(key.pending(), FINGERPRINT), 0);
        String rollback = Files.readString(Path.of("db/rollback/V25__remove_guardian_booking_approval_audit_index.sql"),
                StandardCharsets.UTF_8);
        String migration = new ClassPathResource("db/migration/V25__index_guardian_booking_approval_audits.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        TransactionTemplate fixtureTransaction = new TransactionTemplate(new DataSourceTransactionManager(ownerDataSource));
        fixtureTransaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            owner.execute("set local role bodeul_migration");
            var approvals = owner.queryForList("select * from bodeul.guardian_booking_approvals");
            var audits = owner.queryForList("select * from bodeul.guardian_booking_approval_events");
            owner.execute(rollback);
            assertThat(owner.queryForObject("""
                    select to_regclass('bodeul.ix_guardian_booking_approval_events_guardian') is null
                    """, Boolean.class)).isTrue();
            assertThat(owner.queryForList("select * from bodeul.guardian_booking_approvals")).isEqualTo(approvals);
            assertThat(owner.queryForList("select * from bodeul.guardian_booking_approval_events")).isEqualTo(audits);
            owner.execute(migration);
            assertThat(owner.queryForObject("""
                    select i.indisvalid and i.indisready and pg_get_userbyid(c.relowner) = 'bodeul_migration'
                    from pg_index i join pg_class c on c.oid = i.indexrelid
                    where i.indexrelid = 'bodeul.ix_guardian_booking_approval_events_guardian'::regclass
                    """, Boolean.class)).isTrue();
            assertThat(owner.queryForList("select * from bodeul.guardian_booking_approvals")).isEqualTo(approvals);
            assertThat(owner.queryForList("select * from bodeul.guardian_booking_approval_events")).isEqualTo(audits);
        });
        assertThat(repository.findCurrent(key)).isEqualTo(initial);
        assertThat(eventCount()).isEqualTo(1);
    }

    private void assertOneWinner(ApprovalState first, ApprovalState second, long expected) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(state -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 저장 시작 대기 시간이 초과되었습니다.");
                }
                try {
                    repository.save(state, expected);
                    return true;
                } catch (OptimisticLockingFailureException conflict) {
                    return false;
                }
            })).toList();
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            int winners = 0;
            for (var future : futures) {
                if (future.get(10, TimeUnit.SECONDS)) {
                    winners++;
                }
            }
            assertThat(winners).isEqualTo(1);
        }
    }

    private void assertRejectedTransition(ApprovalState initial, AdultPatientGuardianBookingPolicy.Grant value) {
        int previousEvents = eventCount();
        ApprovalState invalid = new ApprovalState(PATIENT, GUARDIAN, key.clientRequestId(),
                value.version(), Optional.of(value));
        assertThatThrownBy(() -> repository.save(invalid, initial.version()))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findCurrent(key)).isEqualTo(initial);
        assertThat(eventCount()).isEqualTo(previousEvents);
    }

    private void awaitBlockedWriter() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (owner.queryForObject("""
                    select count(*) from pg_stat_activity where datname = current_database()
                    and application_name = 'guardian-booking-test' and wait_event_type = 'Lock'
                    """, Integer.class) == 0) {
                Thread.sleep(10);
            }
        });
    }

    private DriverManagerDataSource dataSource(String role) {
        DriverManagerDataSource result = new DriverManagerDataSource(System.getenv("BOOKING_TEST_DB_URL"),
                System.getenv().getOrDefault("BOOKING_TEST_DB_USER", "postgres"),
                System.getenv("BOOKING_TEST_DB_PASSWORD"));
        Properties properties = new Properties();
        properties.setProperty("ApplicationName", "guardian-booking-test");
        properties.setProperty("options", "-c statement_timeout=15000 -c lock_timeout=10000"
                + (role == null ? "" : " -c role=" + role));
        result.setConnectionProperties(properties);
        return result;
    }

    private int eventCount() {
        return owner.queryForObject("select count(*) from bodeul.guardian_booking_approval_events", Integer.class);
    }

    private ApprovalState grant(ApprovalState previous, String fingerprint) {
        return AdultPatientGuardianBookingPolicy.grantByPatient(previous, previous.version(), PATIENT,
                AppUserRole.PATIENT, true, AppUserRole.GUARDIAN, fingerprint,
                NOW.plusSeconds(previous.version() * 10), NOW.plusSeconds(3600), "test-v1");
    }

    private ApprovalState revoke(ApprovalState previous) {
        return AdultPatientGuardianBookingPolicy.revokeByPatient(previous, previous.version(), PATIENT,
                AppUserRole.PATIENT, NOW.plusSeconds(previous.version() * 10));
    }

    private DecisionReason creationDecision(ApprovalState current, ApprovalState candidate) {
        return AdultPatientGuardianBookingPolicy.evaluateCreation(current, candidate.grant(), GUARDIAN,
                AppUserRole.GUARDIAN, PATIENT, key.clientRequestId(), FINGERPRINT, "test-v1", NOW.plusSeconds(60)).reason();
    }
}
