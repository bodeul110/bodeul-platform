package com.bodeul.core.consent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class GuardianBookingApprovalMigrationContractTests {

    @Test
    void privateCurrentStateAndAppendOnlyAuditStaySeparateFromInformationSharing() throws IOException {
        String sql = new ClassPathResource("db/migration/V24__add_guardian_booking_approvals.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("primary key (patient_user_id, guardian_user_id, client_request_id)")
                .contains("primary key (patient_user_id, guardian_user_id, client_request_id, version)")
                .contains("check (version >= 1)")
                .contains("grant select, insert on table bodeul.guardian_booking_approval_events to bodeul_core_runtime")
                .contains("alter table bodeul.guardian_booking_approvals enable row level security")
                .contains("alter table bodeul.guardian_booking_approval_events enable row level security")
                .doesNotContain("grant delete", "to anon", "to authenticated", "to service_role", "to bodeul_admin_runtime")
                .doesNotContain("alter table bodeul.appointment_requests", "alter table bodeul.guardian_sharing_consents")
                .doesNotContain("security definer");
    }

    @Test
    void rollbackLocksBeforeCheckingHistoryAndNeverDeletesIt() throws IOException {
        String sql = Files.readString(Path.of("db/rollback/V24__remove_guardian_booking_approvals.sql"), StandardCharsets.UTF_8);
        assertThat(sql).containsSubsequence("begin;", "in access exclusive mode", "if exists", "errcode = '55000'",
                        "drop table bodeul.guardian_booking_approval_events", "drop table bodeul.guardian_booking_approvals", "commit;")
                .doesNotContain("delete from", "truncate", "cascade", "flyway_schema_history");
    }

    @Test
    void auditGuardianIndexIsAddedInAFollowUpMigrationWithoutChangingPrivileges() throws IOException {
        String sql = new ClassPathResource("db/migration/V25__index_guardian_booking_approval_audits.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("create index ix_guardian_booking_approval_events_guardian")
                .contains("on bodeul.guardian_booking_approval_events (guardian_user_id)")
                .doesNotContain("grant ", "revoke ", "alter table", "create policy", "drop ", "delete from", "truncate");
    }

    @Test
    void auditIndexRollbackOnlyDropsTheNewIndex() throws IOException {
        String sql = Files.readString(Path.of("db/rollback/V25__remove_guardian_booking_approval_audit_index.sql"),
                StandardCharsets.UTF_8);
        assertThat(sql)
                .contains("drop index if exists bodeul.ix_guardian_booking_approval_events_guardian;")
                .doesNotContain("drop table", "delete from", "truncate", "cascade", "grant ", "revoke ",
                        "flyway_schema_history");
    }

    @Test
    void ciExecutesRepositoryRacesOnIsolatedPostgresAndThenChecksEmptyRollback() throws IOException {
        String script = Files.readString(Path.of("db/verification/verify_guardian_booking_approval_migration.sh"), StandardCharsets.UTF_8);
        String workflow = Files.readString(Path.of("../.github/workflows/core-api.yml"), StandardCharsets.UTF_8);
        assertThat(script).contains("bodeul_guardian_booking_test", "createdb", "127.0.0.1", "localhost",
                "./gradlew migrateDatabase", "./gradlew guardianBookingApprovalPostgresTest",
                "V25__remove_guardian_booking_approval_audit_index.sql", "V24__remove_guardian_booking_approvals.sql",
                "to_regclass('bodeul.guardian_sharing_consents') is null");
        assertThat(workflow).contains("image: postgres:17", "bash db/verification/verify_guardian_booking_approval_migration.sh");
    }
}
