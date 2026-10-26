package com.bodeul.core.consent;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.ApprovalState;
import com.bodeul.core.consent.AdultPatientGuardianBookingPolicy.Grant;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("database")
class JdbcGuardianBookingApprovalRepository implements GuardianBookingApprovalRepository {

    private static final String KEY_CONDITION = """
            patient_user_id = :patientUserId
            and guardian_user_id = :guardianUserId
            and client_request_id = :clientRequestId
            """;

    private static final String INSERT_STATE = """
            insert into bodeul.guardian_booking_approvals (
                patient_user_id, guardian_user_id, client_request_id, version,
                grant_id, request_fingerprint, policy_version, granted_by_user_id,
                granted_at, expires_at, revoked_by_user_id, revoked_at
            ) values (
                :patientUserId, :guardianUserId, :clientRequestId, :version,
                :grantId, :fingerprint, :policyVersion, :grantedBy,
                :grantedAt, :expiresAt, :revokedBy, :revokedAt
            )
            on conflict (patient_user_id, guardian_user_id, client_request_id) do nothing
            returning *
            """;

    private static final String UPDATE_STATE = """
            update bodeul.guardian_booking_approvals
            set version = :version, grant_id = :grantId,
                request_fingerprint = :fingerprint, policy_version = :policyVersion,
                granted_by_user_id = :grantedBy, granted_at = :grantedAt,
                expires_at = :expiresAt, revoked_by_user_id = :revokedBy, revoked_at = :revokedAt
            where
            """ + KEY_CONDITION + """
                and version = :expectedVersion
                and (
                    (cast(:revokedAt as timestamptz) is null
                     and grant_id <> :grantId
                     and :grantedAt >= coalesce(revoked_at, granted_at))
                    or
                    (cast(:revokedAt as timestamptz) is not null
                     and revoked_at is null and grant_id = :grantId
                     and request_fingerprint = :fingerprint and policy_version = :policyVersion
                     and granted_by_user_id = :grantedBy and granted_at = :grantedAt
                     and expires_at = :expiresAt)
                )
            returning *
            """;

    // 상태 변경과 감사 INSERT는 한 문장이므로 감사 제약 위반도 상태 변경을 함께 취소한다.
    private static final String APPEND_EVENT = """
            insert into bodeul.guardian_booking_approval_events (
                patient_user_id, guardian_user_id, client_request_id, version,
                grant_id, request_fingerprint, policy_version, granted_by_user_id,
                granted_at, expires_at, revoked_by_user_id, revoked_at,
                action, actor_user_id, occurred_at
            )
            select patient_user_id, guardian_user_id, client_request_id, version,
                   grant_id, request_fingerprint, policy_version, granted_by_user_id,
                   granted_at, expires_at, revoked_by_user_id, revoked_at,
                   case when revoked_at is null then 'GRANTED' else 'REVOKED' end,
                   patient_user_id, coalesce(revoked_at, granted_at)
            from changed
            returning *
            """;

    private final JdbcClient jdbcClient;

    JdbcGuardianBookingApprovalRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ApprovalState findCurrent(RequestKey key) {
        return find(key, false);
    }

    @Override
    public ApprovalState lockCurrent(RequestKey key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("최신 승인 잠금은 쓰기 트랜잭션 안에서만 사용할 수 있습니다.");
        }
        return find(key, true);
    }

    private ApprovalState find(RequestKey key, boolean lock) {
        Objects.requireNonNull(key, "예약 생성 승인 요청 키가 필요합니다.");
        return bindKey(jdbcClient.sql("select * from bodeul.guardian_booking_approvals where "
                        + KEY_CONDITION + (lock ? " for update" : "")), key)
                .query(this::mapState)
                .optional()
                .orElseGet(key::pending);
    }

    @Override
    public ApprovalState save(ApprovalState next, long expectedVersion) {
        Objects.requireNonNull(next, "다음 승인 상태가 필요합니다.");
        Grant grant = next.grant().orElseThrow(
                () -> new IllegalArgumentException("빈 승인 상태를 저장하거나 기존 승인을 초기화할 수 없습니다."));
        if (expectedVersion < 0 || expectedVersion == Long.MAX_VALUE
                || next.version() != expectedVersion + 1
                || (expectedVersion == 0 && grant.revokedAt() != null)) {
            throw new IllegalArgumentException("현재 버전 바로 다음의 승인·철회만 저장할 수 있습니다.");
        }
        String change = expectedVersion == 0 ? INSERT_STATE : UPDATE_STATE;
        return bindKey(jdbcClient.sql("with changed as (" + change + ") " + APPEND_EVENT),
                        new RequestKey(next.patientUserId(), next.guardianUserId(), next.clientRequestId()))
                .param("version", next.version())
                .param("expectedVersion", expectedVersion)
                .param("grantId", grant.id())
                .param("fingerprint", grant.requestFingerprint())
                .param("policyVersion", grant.policyVersion())
                .param("grantedBy", grant.grantedByUserId())
                .param("grantedAt", grant.grantedAt().atOffset(ZoneOffset.UTC))
                .param("expiresAt", grant.expiresAt().atOffset(ZoneOffset.UTC))
                .param("revokedBy", grant.revokedByUserId())
                .param("revokedAt", grant.revokedAt() == null ? null : grant.revokedAt().atOffset(ZoneOffset.UTC))
                .query(this::mapState)
                .optional()
                .orElseThrow(() -> new OptimisticLockingFailureException(
                        "예약 생성 승인이 변경되었습니다. 최신 상태를 다시 확인해 주세요."));
    }

    private JdbcClient.StatementSpec bindKey(JdbcClient.StatementSpec statement, RequestKey key) {
        return statement.param("patientUserId", key.patientUserId())
                .param("guardianUserId", key.guardianUserId())
                .param("clientRequestId", key.clientRequestId());
    }

    private ApprovalState mapState(ResultSet row, int rowNumber) throws SQLException {
        Grant grant = new Grant(
                row.getObject("grant_id", UUID.class),
                row.getObject("patient_user_id", UUID.class),
                row.getObject("guardian_user_id", UUID.class),
                row.getObject("client_request_id", UUID.class),
                row.getString("request_fingerprint"), row.getString("policy_version"),
                row.getObject("granted_by_user_id", UUID.class),
                row.getTimestamp("granted_at").toInstant(), row.getTimestamp("expires_at").toInstant(),
                row.getObject("revoked_by_user_id", UUID.class),
                row.getTimestamp("revoked_at") == null ? null : row.getTimestamp("revoked_at").toInstant(),
                row.getLong("version"));
        return new ApprovalState(grant.patientUserId(), grant.guardianUserId(), grant.clientRequestId(),
                grant.version(), Optional.of(grant));
    }
}
