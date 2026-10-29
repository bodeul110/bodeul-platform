package com.bodeul.core.consent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianBookingRetentionMigrationContractTests {
    @Test
    void purgeUsesLockedApplyJobAndFinalizationKeepsDatabaseCounts() throws Exception {
        String sql = new ClassPathResource("db/migration/V27__add_guardian_booking_retention_counts.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("p_job_id uuid, p_limit integer", "where id = p_job_id for update",
                "v_job.mode <> 'APPLY' or v_job.status <> 'RUNNING'",
                "bodeul.purge_expired_guardian_booking_drafts(v_job.as_of, p_limit)",
                "guardian_drafts_purged = guardian_drafts_purged + v_purged",
                "guardian_drafts_purged = run.guardian_drafts_purged");
        assertThat(sql).doesNotContain("guardian_drafts_purged = coalesce((p_counts");
    }

    @Test
    void migrationAddsOnlyCountHistoryWithoutChangingDraftsOrGivingTableAccess() throws Exception {
        String sql = new ClassPathResource("db/migration/V27__add_guardian_booking_retention_counts.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("guardian_draft_checked boolean not null default false",
                "guardian_draft_candidates integer not null default 0",
                "guardian_drafts_purged integer not null default 0",
                "guardian_drafts_remaining integer not null default 0",
                "create or replace function bodeul.finish_retention_job",
                "count(*) filter (where run.guardian_draft_checked)",
                "order by latest.started_at desc, latest.id desc limit 1",
                "set search_path = pg_catalog, pg_temp",
                "to bodeul_retention_runtime, bodeul_admin_runtime");
        assertThat(sql).doesNotContain("appointment_body", "grant select", "grant update", "alter table bodeul.guardian_booking_drafts");
    }

    @Test
    void rollbackStopsForRunningOrRecordedJobsAndRestoresOldContract() throws Exception {
        String rollback = Files.readString(Path.of("db/rollback/V27__remove_guardian_booking_retention_counts.sql"),
                StandardCharsets.UTF_8);
        assertThat(rollback).contains("lock table bodeul.retention_job_runs in access exclusive mode",
                "status = 'RUNNING' or guardian_draft_checked", "drop column guardian_drafts_remaining",
                "create or replace function bodeul.finish_retention_job", "to bodeul_retention_runtime, bodeul_admin_runtime");
        assertThat(rollback).doesNotContain("delete from", "truncate", "cascade");
        String verifier = Files.readString(Path.of("db/verification/verify_guardian_booking_approval_migration.sh"),
                StandardCharsets.UTF_8);
        assertThat(verifier.indexOf("--file db/rollback/V27__"))
                .isGreaterThan(0).isLessThan(verifier.indexOf("--file db/rollback/V26__"));
    }
}
