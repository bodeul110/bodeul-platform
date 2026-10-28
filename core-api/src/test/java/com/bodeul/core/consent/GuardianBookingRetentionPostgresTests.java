package com.bodeul.core.consent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 서비스 DB와 분리한 CI 전용 DB에서 파기 집계·권한·복구 경계를 확인한다. */
@Tag("postgres-booking-approval")
class GuardianBookingRetentionPostgresTests {
    private DriverManagerDataSource ownerDataSource;
    private JdbcTemplate owner;
    private JdbcTemplate retention;

    @BeforeEach
    void prepare() {
        assertThat(System.getenv("BOOKING_TEST_DB_URL"))
                .matches("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/bodeul_guardian_booking_test$");
        ownerDataSource = dataSource(null);
        owner = new JdbcTemplate(ownerDataSource);
        assertThat(owner.queryForObject("select current_database()", String.class))
                .isEqualTo("bodeul_guardian_booking_test");
        owner.execute("truncate bodeul.retention_job_runs");
        retention = new JdbcTemplate(dataSource("bodeul_retention_runtime"));
    }

    @Test
    void oldCountsRemainCompatibleAndDoNotPretendGuardianDraftsWereChecked() throws Exception {
        UUID id = begin("DRY_RUN", 0);
        assertThat(finish(id, "COMPLETED", oldCounts(), null)).isTrue();
        assertThat(owner.queryForObject("select guardian_draft_checked from bodeul.retention_job_runs", Boolean.class))
                .isFalse();
        assertThat(monthly().get("guardian_draft_run_count")).isEqualTo(0L);
        assertThat(monthly().get("guardian_drafts_remaining")).isEqualTo(0L);
    }

    @Test
    void countsPersistWithoutBodiesAndMonthlyBacklogUsesLatestEnabledRun() throws Exception {
        Map<String, Object> first = counts(502, 500, 2);
        UUID failed = begin("APPLY", 10);
        assertThat(finish(failed, "FAILED", first, "PURGE_GUARDIAN_DRAFTS")).isTrue();
        UUID retry = begin("APPLY", 5);
        assertThat(finish(retry, "COMPLETED", counts(2, 2, 0), null)).isTrue();
        assertThat(finish(begin("DRY_RUN", 0), "COMPLETED", oldCounts(), null)).isTrue();
        Map<String, Object> monthly = monthly();
        assertThat(monthly.get("guardian_draft_run_count")).isEqualTo(2L);
        assertThat(monthly.get("guardian_draft_candidates")).isEqualTo(504L);
        assertThat(monthly.get("guardian_drafts_purged")).isEqualTo(502L);
        assertThat(monthly.get("guardian_drafts_remaining")).isEqualTo(0L);
        assertThat(monthly.get("failed_run_count")).isEqualTo(1L);
        assertThat(finish(retry, "COMPLETED", counts(99, 99, 0), null)).isFalse();
        assertThat(monthly()).isEqualTo(monthly);
    }

    @Test
    void incompleteInvalidOrSensitiveCountPayloadsCannotFinishAJob() throws Exception {
        UUID id = begin("DRY_RUN", 0);
        Map<String, Object> partial = oldCounts();
        partial.put("guardianDraftCandidates", 2);
        assertThatThrownBy(() -> finish(id, "COMPLETED", partial, null)).isInstanceOf(DataAccessException.class);
        for (Object invalid : new Object[] {null, -1, 1.5, "2", 1000000000}) {
            Map<String, Object> payload = counts(2, 0, 2);
            payload.put("guardianDraftsRemaining", invalid);
            assertThatThrownBy(() -> finish(id, "COMPLETED", payload, null)).isInstanceOf(DataAccessException.class);
        }
        Map<String, Object> sensitive = counts(2, 0, 2);
        sensitive.put("appointmentBody", Map.of("memo", "합성 원문"));
        assertThatThrownBy(() -> finish(id, "COMPLETED", sensitive, null)).isInstanceOf(DataAccessException.class);
        assertThat(owner.queryForObject("select status from bodeul.retention_job_runs", String.class)).isEqualTo("RUNNING");
    }

    @ParameterizedTest
    @ValueSource(strings = {"anon", "authenticated", "service_role", "bodeul_core_runtime", "bodeul_admin_runtime"})
    void onlyRetentionRoleCanFinishJobAndPurgeWithoutDirectTableAccess(String role) {
        assertThat(owner.queryForObject("select has_function_privilege(?, ?, 'EXECUTE')", Boolean.class,
                role, "bodeul.finish_retention_job(uuid,text,timestamptz,jsonb,text)")).isFalse();
        assertThat(owner.queryForObject("select has_table_privilege(?, ?, 'SELECT')", Boolean.class,
                "bodeul_retention_runtime", "bodeul.guardian_booking_drafts")).isFalse();
        assertThat(owner.queryForObject("select has_table_privilege(?, ?, 'UPDATE')", Boolean.class,
                "bodeul_retention_runtime", "bodeul.guardian_booking_drafts")).isFalse();
        assertThat(owner.queryForObject("select has_function_privilege(?, ?, 'EXECUTE')", Boolean.class,
                role, "bodeul.purge_expired_guardian_booking_drafts(timestamptz,integer)")).isFalse();
        assertThatThrownBy(() -> retention.queryForList("select * from bodeul.guardian_booking_drafts"))
                .isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"RUNNING", "COMPLETED"})
    void rollbackRefusesRunningJobsAndEvenZeroCountGuardianHistory(String status) throws Exception {
        UUID id = begin("DRY_RUN", 0);
        if (status.equals("COMPLETED")) assertThat(finish(id, status, counts(0, 0, 0), null)).isTrue();
        String rollback = rollbackSql();
        try (var connection = ownerDataSource.getConnection(); var statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute(rollback)).isInstanceOf(SQLException.class);
            statement.execute("rollback");
        }
        assertThat(owner.queryForObject("select status from bodeul.retention_job_runs", String.class)).isEqualTo(status);
        assertThat(monthly()).containsKey("guardian_draft_candidates");
    }

    @Test
    void emptyGuardianHistoryRollbackAndReapplyPreserveExistingCountsAndPermissions() throws Exception {
        Map<String, Object> old = oldCounts();
        old.put("adminAuditCandidates", 7);
        assertThat(finish(begin("DRY_RUN", 0), "COMPLETED", old, null)).isTrue();
        String migration = new ClassPathResource("db/migration/V27__add_guardian_booking_retention_counts.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        owner.execute(rollbackSql());
        try {
            assertThat(monthly()).doesNotContainKey("guardian_draft_candidates");
            assertThat(monthly().get("admin_audit_candidates")).isEqualTo(7L);
            assertThat(finish(begin("DRY_RUN", 0), "COMPLETED", oldCounts(), null)).isTrue();
        } finally {
            owner.execute(migration);
        }
        assertThat(monthly().get("admin_audit_candidates")).isEqualTo(7L);
        assertThat(owner.queryForObject("""
                select pg_get_userbyid(proowner) from pg_proc
                where oid = 'bodeul.retention_monthly_summary(date)'::regprocedure
                """, String.class)).isEqualTo("bodeul_migration");
        assertThat(owner.queryForObject("select has_function_privilege(?, ?, 'EXECUTE')", Boolean.class,
                "bodeul_admin_runtime", "bodeul.retention_monthly_summary(date)")).isTrue();
    }

    private UUID begin(String mode, int secondsAgo) {
        return retention.queryForObject("""
                select bodeul.begin_retention_job(?, now() - (? * interval '1 second'), now() - (? * interval '1 second'))
                """, UUID.class, mode, secondsAgo, secondsAgo);
    }

    private boolean finish(UUID id, String status, Map<String, Object> counts, String stage) throws Exception {
        return Boolean.TRUE.equals(retention.queryForObject("""
                select bodeul.finish_retention_job(?, ?, now(), ?::jsonb, ?)
                """, Boolean.class, id, status, new ObjectMapper().writeValueAsString(counts), stage));
    }

    private Map<String, Object> monthly() {
        return retention.queryForMap("select * from bodeul.retention_monthly_summary(current_date)");
    }

    private Map<String, Object> counts(int candidates, int purged, int remaining) {
        Map<String, Object> result = oldCounts();
        result.put("guardianDraftCandidates", candidates);
        result.put("guardianDraftsPurged", purged);
        result.put("guardianDraftsRemaining", remaining);
        return result;
    }

    private Map<String, Object> oldCounts() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String key : List.of("postgresMessageCandidates", "postgresAttachmentCandidates", "postgresLocationCandidates",
                "postgresLegalHoldSkips", "firestoreMessageCandidates", "firestoreAttachmentCandidates",
                "firestoreLocationCandidates", "firestoreLegalHoldSkips", "managerDocumentCandidates",
                "managerDocumentLegalHoldSkips", "messagesRedacted", "attachmentsDeleted", "attachmentDeleteFailures",
                "locationsDeleted", "firestoreMessagesRedacted", "firestoreAttachmentsDeleted",
                "firestoreAttachmentDeleteFailures", "firestoreLocationsCleared", "managerDocumentsDeleted",
                "managerDocumentDeleteFailures", "adminAuditCandidates", "adminAuditsDeleted")) counts.put(key, 0);
        return counts;
    }

    private String rollbackSql() throws Exception {
        return Files.readString(Path.of("db/rollback/V27__remove_guardian_booking_retention_counts.sql"), StandardCharsets.UTF_8);
    }

    private DriverManagerDataSource dataSource(String role) {
        DriverManagerDataSource result = new DriverManagerDataSource(System.getenv("BOOKING_TEST_DB_URL"),
                System.getenv().getOrDefault("BOOKING_TEST_DB_USER", "postgres"), System.getenv("BOOKING_TEST_DB_PASSWORD"));
        Properties properties = new Properties();
        properties.setProperty("options", "-c statement_timeout=15000 -c lock_timeout=10000"
                + (role == null ? "" : " -c role=" + role));
        result.setConnectionProperties(properties);
        return result;
    }
}
