package com.bodeul.core.appointment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class GuardianBookingDraftMigrationContractTests {
    @Test
    void immutableOriginalIsPrivateAndRetentionOnlyReceivesBoundedPurgeFunctions() throws Exception {
        String sql = new ClassPathResource("db/migration/V26__add_guardian_booking_drafts.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("primary key (guardian_user_id, client_request_id)",
                "octet_length(appointment_body::text) <= 65536", "interval '24 hours'",
                "grant select, insert on table bodeul.guardian_booking_drafts to bodeul_core_runtime",
                "enable row level security", "security definer set search_path = pg_catalog, bodeul",
                "least(p_as_of, now())", "p_limit > 500", "for update skip locked")
                .doesNotContain("grant update", "grant delete", "to anon", "to authenticated", "to service_role",
                        "to bodeul_admin_runtime", "alter table bodeul.guardian_booking_approvals");
    }

    @Test
    void rollbackRefusesToDiscardOriginalsAndIsIncludedInIsolatedDatabaseVerification() throws Exception {
        String sql = Files.readString(Path.of("db/rollback/V26__remove_guardian_booking_drafts.sql"));
        assertThat(sql).containsSubsequence("begin;", "in access exclusive mode", "if exists",
                "drop function", "drop table bodeul.guardian_booking_drafts", "commit;")
                .doesNotContain("delete from", "truncate", "cascade", "flyway_schema_history");
        assertThat(Files.readString(Path.of("db/verification/verify_guardian_booking_approval_migration.sh")))
                .contains("V26__remove_guardian_booking_drafts.sql", "to_regclass('bodeul.appointment_requests') is null");
    }
}
