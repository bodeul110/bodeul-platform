package com.bodeul.core.appointment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.appointment.AppointmentService.CreateAppointmentCommand;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.consent.GuardianBookingException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
@Profile("database")
class JdbcGuardianBookingDraftRepository implements GuardianBookingDraftRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    JdbcGuardianBookingDraftRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public void lockGuardian(UUID guardianId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("초안 저장은 트랜잭션 안에서 실행해야 합니다.");
        }
        // 같은 보호자의 동시 저장도 활성 초안 한도와 불변 요청 키를 함께 확인한다.
        jdbc.sql("select pg_advisory_xact_lock(hashtextextended(:guardian, 419))")
                .param("guardian", guardianId.toString()).query((row, index) -> true).single();
    }

    @Override
    public Optional<StoredDraft> findForActor(UUID guardianId, UUID requestId, UUID actorId) {
        return jdbc.sql("""
                select * from bodeul.guardian_booking_drafts
                where guardian_user_id = :guardian and client_request_id = :request
                  and (patient_user_id = :actor or guardian_user_id = :actor)
                """).param("guardian", guardianId).param("request", requestId).param("actor", actorId)
                .query(this::storedDraft).optional();
    }

    @Override
    public long countActive(UUID guardianId, Instant now) {
        return jdbc.sql("""
                select count(*) from bodeul.guardian_booking_drafts
                where guardian_user_id = :guardian and expires_at > :now
                """).param("guardian", guardianId).param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class).single();
    }

    @Override
    public void insert(StoredDraft draft) {
        final String body;
        try {
            body = mapper.writeValueAsString(draft.appointment());
        } catch (JsonProcessingException exception) {
            throw GuardianBookingException.unavailable();
        }
        jdbc.sql("""
                insert into bodeul.guardian_booking_drafts
                  (patient_user_id, guardian_user_id, client_request_id, appointment_body,
                   request_fingerprint, created_at, expires_at)
                values (:patient, :guardian, :request, cast(:body as jsonb), :fingerprint, :created, :expires)
                """).param("patient", draft.patientUserId()).param("guardian", draft.guardianUserId())
                .param("request", draft.appointment().clientRequestId()).param("body", body)
                .param("fingerprint", draft.fingerprint()).param("created", draft.createdAt().atOffset(ZoneOffset.UTC))
                .param("expires", draft.expiresAt().atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public List<DraftSummary> listActive(AppUser actor, Instant now) {
        return jdbc.sql("""
                select patient_user_id, guardian_user_id, client_request_id, created_at, expires_at
                from bodeul.guardian_booking_drafts
                where expires_at > :now and (
                    (:role = 'PATIENT' and patient_user_id = :actor)
                    or (:role = 'GUARDIAN' and guardian_user_id = :actor))
                order by created_at desc, guardian_user_id, client_request_id limit 50
                """).param("actor", actor.id()).param("role", actor.role().name())
                .param("now", now.atOffset(ZoneOffset.UTC)).query((row, index) -> new DraftSummary(
                        row.getObject("patient_user_id", UUID.class), row.getObject("guardian_user_id", UUID.class),
                        row.getObject("client_request_id", UUID.class), row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("expires_at").toInstant())).list();
    }

    private StoredDraft storedDraft(ResultSet row, int index) throws SQLException {
        try {
            String body = row.getString("appointment_body");
            if (body == null) throw GuardianBookingException.draftNotFound();
            CreateAppointmentCommand command = mapper.readValue(body, CreateAppointmentCommand.class);
            if (command == null || !row.getObject("client_request_id", UUID.class).equals(command.clientRequestId())) {
                throw GuardianBookingException.unavailable();
            }
            return new StoredDraft(row.getObject("patient_user_id", UUID.class), row.getObject("guardian_user_id", UUID.class),
                    command, row.getString("request_fingerprint"), row.getTimestamp("created_at").toInstant(),
                    row.getTimestamp("expires_at").toInstant());
        } catch (JsonProcessingException exception) {
            // 파싱 오류에 예약 원문을 포함하지 않는다.
            throw GuardianBookingException.unavailable();
        }
    }
}
