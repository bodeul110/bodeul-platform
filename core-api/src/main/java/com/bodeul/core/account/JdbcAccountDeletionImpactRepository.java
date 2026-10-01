package com.bodeul.core.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database")
class JdbcAccountDeletionImpactRepository implements AccountDeletionImpactRepository {

    private static final String INSPECT_ACCOUNT = """
            select *
            from bodeul.account_deletion_postgres_inventory(:userId)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    JdbcAccountDeletionImpactRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostgreSqlImpact inspect(UUID userId) {
        PostgreSqlImpact impact = jdbcTemplate.queryForObject(
                INSPECT_ACCOUNT,
                new MapSqlParameterSource("userId", userId),
                (resultSet, rowNumber) -> new PostgreSqlImpact(
                        requiredCount(resultSet, "profile_count"),
                        requiredCount(resultSet, "appointment_count"),
                        requiredCount(resultSet, "active_appointment_count"),
                        requiredCount(resultSet, "companion_session_count"),
                        requiredCount(resultSet, "active_companion_session_count"),
                        requiredCount(resultSet, "session_report_count"),
                        requiredCount(resultSet, "appointment_follow_up_count"),
                        requiredCount(resultSet, "assignment_audit_count"),
                        requiredCount(resultSet, "related_chat_message_count"),
                        requiredCount(resultSet, "sent_chat_message_count"),
                        requiredCount(resultSet, "related_chat_attachment_count"),
                        requiredCount(resultSet, "related_chat_read_receipt_count"),
                        requiredCount(resultSet, "related_location_count"),
                        requiredCount(resultSet, "active_legal_hold_count"),
                        requiredCount(resultSet, "bank_transfer_payment_count"),
                        requiredCount(resultSet, "payment_event_count")));
        if (impact == null) {
            throw new DataRetrievalFailureException("계정 삭제 영향도 집계 결과를 확인할 수 없습니다.");
        }
        return impact;
    }

    @Override
    public Optional<BookingApprovalImpact> inspectBookingApprovals(UUID userId) {
        // V24 적용 전에도 기존 집계는 유지하되 새 저장소를 0건으로 오인하지 않는다.
        Boolean schemaReady = jdbcTemplate.queryForObject("""
                select to_regclass('bodeul.guardian_booking_approvals') is not null
                   and to_regclass('bodeul.guardian_booking_approval_events') is not null
                """, new MapSqlParameterSource(), Boolean.class);
        if (schemaReady == null) throw new DataRetrievalFailureException("승인 저장소 적용 상태를 확인할 수 없습니다.");
        if (!schemaReady) return Optional.empty();
        BookingApprovalImpact impact = jdbcTemplate.queryForObject("""
                select
                    (select count(*) from bodeul.guardian_booking_approvals
                     where patient_user_id = :userId or guardian_user_id = :userId) as approval_count,
                    (select count(*) from bodeul.guardian_booking_approvals
                     where (patient_user_id = :userId or guardian_user_id = :userId)
                       and revoked_at is null and granted_at <= now() and expires_at > now()) as active_approval_count,
                    (select count(*) from bodeul.guardian_booking_approval_events
                     where patient_user_id = :userId or guardian_user_id = :userId) as audit_count
                """, new MapSqlParameterSource("userId", userId),
                (resultSet, row) -> new BookingApprovalImpact(requiredCount(resultSet, "approval_count"),
                        requiredCount(resultSet, "active_approval_count"), requiredCount(resultSet, "audit_count")));
        if (impact == null) throw new DataRetrievalFailureException("승인 영향도 집계를 확인할 수 없습니다.");
        return Optional.of(impact);
    }

    private long requiredCount(ResultSet resultSet, String column) throws SQLException {
        Long count = resultSet.getObject(column, Long.class);
        if (count == null || count < 0) {
            throw new DataRetrievalFailureException(
                    "계정 삭제 영향도 집계 열을 확인할 수 없습니다: " + column);
        }
        return count;
    }
}
