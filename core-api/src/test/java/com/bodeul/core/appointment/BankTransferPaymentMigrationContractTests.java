package com.bodeul.core.appointment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransferPaymentMigrationContractTests {

    @Test
    void ledgerSnapshotsServerPriceAndRejectsRepricingWithoutHardcodedTariff() throws IOException {
        String sql = fileText("src/main/resources/db/migration/V22__add_bank_transfer_payment_contract.sql");
        int start = sql.indexOf("create function bodeul.initialize_bank_transfer_payment()");
        int end = sql.indexOf("alter function bodeul.initialize_bank_transfer_payment()", start);
        assertThat(sql.substring(start, end))
                .contains("new.final_price")
                .doesNotContain("69000", "69_000", "40000", "40_000");
        assertThat(sql).contains("if new.final_price is distinct from old.final_price then");
    }

    @Test
    void migrationSeparatesPrivateLedgerAndRuntimeFunctions() throws IOException {
        String sql = fileText("src/main/resources/db/migration/V22__add_bank_transfer_payment_contract.sql");

        assertThat(sql)
                .contains("'bank_transfer'")
                .contains("'awaiting_deposit'")
                .contains("'deposit_confirmed'")
                .contains("'review_required'")
                .contains("'refund_requested'")
                .contains("'refunded'")
                .contains("appointment_bank_transfer_payments")
                .contains("appointment_payment_events")
                .contains("create_request_fingerprint")
                .contains("guard_appointment_create_request_fingerprint")
                .contains("revoke all on table bodeul.appointment_bank_transfer_payments")
                .contains("revoke all on table bodeul.appointment_payment_events")
                .contains("get_bank_transfer_payment")
                .contains("set_bank_transfer_depositor")
                .contains("transition_appointment_bank_transfer_payment")
                .contains("to bodeul_core_runtime")
                .contains("to bodeul_admin_runtime")
                .contains("p_operation_id::text || ':' || v_normalized_name")
                .contains("using errcode = 'p0003'")
                .contains("record_admin_access_audit")
                .contains("무통장입금 상세 원장이 없어 예약 취소를 중단합니다.")
                .contains("v_previous_status = 'canceled'")
                .contains("취소된 예약의 검토 대상 입금은 환불 요청으로만 변경할 수 있습니다.")
                .contains("검토할 실제 입금액이 필요합니다.")
                .contains("v_payment_status_code <> 'deposit_confirmed'")
                .doesNotContain("status = case when p_target_status = 'canceled'");
    }

    @Test
    void migrationExtendsAccountDeletionInventoryWithoutReturningPrivateValues() throws IOException {
        String migration = fileText(
                "src/main/resources/db/migration/V22__add_bank_transfer_payment_contract.sql");
        String rollback = fileText("db/rollback/V22__remove_bank_transfer_payment_contract.sql");

        assertThat(migration)
                .contains("bank_transfer_payment_count bigint")
                .contains("payment_event_count bigint")
                .contains("payment.confirmed_by_admin_user_id = p_user_id")
                .contains("event.actor_user_id = p_user_id")
                .contains("drop function bodeul.account_deletion_postgres_inventory(uuid)");
        assertThat(rollback)
                .contains("drop column create_request_fingerprint")
                .contains("create function bodeul.account_deletion_postgres_inventory")
                .contains("active_legal_hold_count bigint")
                .contains("기술적 차단 사실만 조회하는 함수")
                .doesNotContain("bank_transfer_payment_count bigint");
    }

    @Test
    void rollbackIsFailClosedAndRestoresTheLatestAssignmentContract() throws IOException {
        String sql = fileText("db/rollback/V22__remove_bank_transfer_payment_contract.sql");

        assertThat(sql)
                .contains("using errcode = '55000'")
                .contains("lock table")
                .contains("in access exclusive mode")
                .contains("drop table bodeul.appointment_payment_events")
                .contains("drop table bodeul.appointment_bank_transfer_payments")
                .contains("create or replace function bodeul.assign_companion_session")
                .contains("guide_steps_snapshot")
                .doesNotContain("deposit_confirmed");
        assertThat(sql.indexOf("lock table"))
                .isLessThan(sql.indexOf("if exists"));
    }

    @Test
    void databaseVerifierCoversPrivilegesRetriesMatchingAndRollbackAtomicity() throws IOException {
        String checks = fileText("db/verification/018_bank_transfer_payment_checks.sql");
        String verifier = fileText("db/verification/verify_bank_transfer_payment_migration.sh");
        String rollbackChecks = fileText("db/verification/019_bank_transfer_payment_rollback_checks.sql");
        String rollbackAtomicityChecks = fileText(
                "db/verification/021_bank_transfer_payment_rollback_atomicity_checks.sql");

        assertThat(checks)
                .contains("has_table_privilege")
                .contains("has_function_privilege")
                .contains("direct dml 권한이 노출됐습니다.")
                .contains("같은 operation_id의 다른 입금자명이 허용됐습니다.")
                .contains("developer 역할이 입금 상태를 변경했습니다.")
                .contains("입금 확인 전 무통장입금 예약이 매칭됐습니다.")
                .contains("관리자 성공 재시도에서 결제 또는 관리자 감사가 중복됐습니다.")
                .contains("정확한 금액의 이상 입금이 검토 뒤 환불 요청으로 전이되지 않았습니다.")
                .contains("취소된 예약의 지연 입금이 확인 완료 상태로 변경됐습니다.")
                .contains("취소 뒤 지연 입금이 검토를 거쳐 refund_requested 상태로 전이되지 않았습니다.")
                .contains("원장 누락으로 실패한 예약 취소가 일부 반영됐습니다.")
                .contains("core runtime이 예약 생성 요청 지문을 변경했습니다.")
                .contains("환자 계정 삭제 영향도에 무통장입금 상세와 이벤트가 모두 반영되지 않았습니다.")
                .contains("운영 관리자 계정 삭제 영향도에 결제 확인 상세와 감사 이벤트가 반영되지 않았습니다.")
                .contains("rollback;");
        assertThat(verifier)
                .contains("020_bank_transfer_payment_rollback_failure_fixture.sql")
                .contains("021_bank_transfer_payment_rollback_atomicity_checks.sql")
                .contains("v22__remove_bank_transfer_payment_contract.sql");
        assertThat(rollbackChecks)
                .contains("jsonb_object_keys")
                .contains("<> 14")
                .contains("array_agg(argument.argument_name order by argument.ordinal_position)")
                .contains("'profile_count'")
                .contains("'active_legal_hold_count'")
                .contains("pg_get_userbyid(proc.proowner) = 'bodeul_migration'")
                .contains("proc.prosecdef")
                .contains("proc.provolatile = 's'")
                .contains("search_path=bodeul, pg_temp")
                .contains("aclexplode")
                .contains("has_function_privilege");
        assertThat(rollbackAtomicityChecks)
                .contains("50000000-0000-0000-0000-000000000002")
                .contains("일부 객체 또는 운영 자료를 제거했습니다.");
    }

    @Test
    void domainRejectionChecksDoNotSwallowTheirOwnAssertionFailures() throws IOException {
        String checks = fileText("db/verification/018_bank_transfer_payment_checks.sql");
        long guardedAssertions = Pattern.compile(
                "raise exception '[^']+'\\s+using errcode = 'p0004';"
                        + "\\s+exception\\s+when sqlstate 'p0001' then null;")
                .matcher(checks)
                .results()
                .count();

        assertThat(guardedAssertions).isEqualTo(3);
    }

    @Test
    void appointmentUpdatePreservesConfirmedBankTransferState() throws IOException {
        String repository = fileText(
                "src/main/java/com/bodeul/core/appointment/JdbcAppointmentRepository.java");

        assertThat(repository)
                .contains("when payment_method_code = 'bank_transfer' then payment_status_code")
                .contains("when payment_method_code = 'bank_transfer' then payment_approval_code")
                .contains("and status = 'requested'");
    }

    private String fileText(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8).toLowerCase();
    }
}
