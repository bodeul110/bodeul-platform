package com.bodeul.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 공개 환경에 연결하지 않고 CI의 전용 PostgreSQL DB만 사용한다. */
@Tag("postgres-guide-promotion")
class PreviewHospitalGuideV1PromotionPostgresTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final UUID PATIENT_ID = UUID.fromString("a2600000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("a2600000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_ID = UUID.fromString("a2600000-0000-0000-0000-000000000003");
    private static final UUID TARGET_GUIDE_ID = UUID.fromString("a2600000-0000-0000-0000-000000000010");
    private static final UUID OTHER_GUIDE_ID = UUID.fromString("a2600000-0000-0000-0000-000000000011");
    private static final UUID OLD_APPOINTMENT_ID = UUID.fromString("a2600000-0000-0000-0000-000000000020");
    private static final UUID NEW_APPOINTMENT_ID = UUID.fromString("a2600000-0000-0000-0000-000000000021");

    private static final String LEGACY_STEPS = """
            [
              {"title":"환자 접수","description":"환자 상태를 확인합니다.","custom":"keep"},
              {"title":"접수 등록","description":"접수 정보를 확인합니다."},
              {"title":"진료 접수","description":"진료 접수를 진행합니다."},
              {"title":"진료 완료","description":"진료 결과를 정리합니다."},
              {"title":"수납 처리","description":"수납 여부를 확인합니다."},
              {"title":"약국 방문","description":"처방전을 확인합니다."},
              {"title":"환자 귀가(서비스 종료)","description":"귀가 상황을 공유합니다."}
            ]
            """;

    private PreviewHospitalGuideV1PromotionApplication.DatabaseConfig databaseConfig;

    @BeforeEach
    void prepareIsolatedDatabase() throws Exception {
        String url = System.getenv("GUIDE_PROMOTION_TEST_DB_URL");
        String username = System.getenv("GUIDE_PROMOTION_TEST_DB_USERNAME");
        String password = System.getenv("GUIDE_PROMOTION_TEST_DB_PASSWORD");
        assertThat(url)
                .matches("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/bodeul_guide_empty\\?sslmode=disable$");
        assertThat(username).isEqualTo("postgres");
        databaseConfig = new PreviewHospitalGuideV1PromotionApplication.DatabaseConfig(url, username, password);

        try (Connection connection = ownerConnection()) {
            cleanFixture(connection);
            insertFixture(connection);
        }
    }

    @AfterEach
    void cleanIsolatedDatabase() throws Exception {
        if (databaseConfig == null) {
            return;
        }
        try (Connection connection = ownerConnection()) {
            cleanFixture(connection);
        }
    }

    @Test
    void statusApplyAndRetryPreserveExistingSnapshotAndAffectOnlyTargetGuide() throws Exception {
        UUID oldSessionId = assign(OLD_APPOINTMENT_ID, "legacy snapshot");
        SessionSnapshot oldBefore = readSession(oldSessionId);
        GuideState targetBefore = readGuide(TARGET_GUIDE_ID);
        GuideState otherBefore = readGuide(OTHER_GUIDE_ID);

        PreviewHospitalGuideV1PromotionApplication.PromotionSummary status =
                PreviewHospitalGuideV1PromotionApplication.execute(
                        databaseConfig,
                        PreviewHospitalGuideV1PromotionApplication.PromotionAction.STATUS);

        assertThat(status).isEqualTo(new PreviewHospitalGuideV1PromotionApplication.PromotionSummary(
                PreviewHospitalGuideV1PromotionApplication.PromotionAction.STATUS,
                0,
                1,
                0,
                7,
                true,
                false));
        assertThat(readGuide(TARGET_GUIDE_ID)).isEqualTo(targetBefore);

        PreviewHospitalGuideV1PromotionApplication.PromotionSummary applied =
                PreviewHospitalGuideV1PromotionApplication.execute(
                        databaseConfig,
                        PreviewHospitalGuideV1PromotionApplication.PromotionAction.APPLY);

        assertThat(applied).isEqualTo(new PreviewHospitalGuideV1PromotionApplication.PromotionSummary(
                PreviewHospitalGuideV1PromotionApplication.PromotionAction.APPLY,
                1,
                2,
                1,
                7,
                false,
                true));

        GuideState targetAfter = readGuide(TARGET_GUIDE_ID);
        assertThat(targetAfter.revision()).isEqualTo(2);
        assertThat(targetAfter.contractVersion()).isEqualTo(1);
        assertThat(targetAfter.validSteps()).isTrue();
        assertThat(targetAfter.validMedia()).isTrue();
        assertPromotedContent(targetBefore.steps(), targetAfter.steps());
        assertThat(readGuide(OTHER_GUIDE_ID)).isEqualTo(otherBefore);
        assertThat(readSession(oldSessionId)).isEqualTo(oldBefore);
        assertThat(oldBefore.source()).isEqualTo("LEGACY_HOSPITAL_GUIDE_V0");
        assertThat(oldBefore.contractVersion()).isZero();
        assertThat(oldBefore.guideRevision()).isEqualTo(1);

        UUID newSessionId = assign(NEW_APPOINTMENT_ID, "promoted snapshot");
        SessionSnapshot newSnapshot = readSession(newSessionId);
        assertThat(newSnapshot.source()).isEqualTo("HOSPITAL_GUIDE_STEP_CODE_V1");
        assertThat(newSnapshot.guideId()).isEqualTo(TARGET_GUIDE_ID);
        assertThat(newSnapshot.contractVersion()).isEqualTo(1);
        assertThat(newSnapshot.guideRevision()).isEqualTo(2);
        assertThat(OBJECT_MAPPER.readTree(newSnapshot.steps()).get(0).path("code").asText())
                .isEqualTo("LEGACY_CORE_PATIENT_CONTACT");

        PreviewHospitalGuideV1PromotionApplication.PromotionSummary retried =
                PreviewHospitalGuideV1PromotionApplication.execute(
                        databaseConfig,
                        PreviewHospitalGuideV1PromotionApplication.PromotionAction.APPLY);
        assertThat(retried.affectedRows()).isZero();
        assertThat(retried.revision()).isEqualTo(2);
        assertThat(retried.ready()).isTrue();
        assertThat(readGuide(TARGET_GUIDE_ID)).isEqualTo(targetAfter);
    }

    private void insertFixture(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    insert into bodeul.app_users (id, firebase_uid, role, name) values
                      ('a2600000-0000-0000-0000-000000000001', 'guide-promotion-patient', 'PATIENT', '검증 환자'),
                      ('a2600000-0000-0000-0000-000000000002', 'guide-promotion-manager', 'MANAGER', '검증 매니저'),
                      ('a2600000-0000-0000-0000-000000000003', 'guide-promotion-admin', 'ADMIN', '검증 관리자')
                    """);
            statement.executeUpdate("""
                    insert into bodeul.admin_role_assignments (
                        admin_user_id, admin_role, granted_by_admin_user_id, grant_reason
                    ) values (
                        'a2600000-0000-0000-0000-000000000003',
                        'OPERATIONS',
                        null,
                        '병원 가이드 V1 전환 검증'
                    )
                    """);
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                insert into bodeul.hospital_guides (
                    id, hospital_name, department_name, steps, step_contract_version
                ) values (?, ?, ?, ?::jsonb, ?)
                """)) {
            statement.setObject(1, TARGET_GUIDE_ID);
            statement.setString(2, PreviewHospitalGuideV1PromotionApplication.HOSPITAL_NAME);
            statement.setString(3, PreviewHospitalGuideV1PromotionApplication.DEPARTMENT_NAME);
            statement.setString(4, LEGACY_STEPS);
            statement.setInt(5, 0);
            statement.executeUpdate();

            statement.setObject(1, OTHER_GUIDE_ID);
            statement.setString(2, "무관병원");
            statement.setString(3, "외과");
            statement.setString(4, """
                    [{"code":"UNRELATED_STEP","order":1,"title":"무관 단계","description":"보존"}]
                    """);
            statement.setInt(5, 1);
            statement.executeUpdate();
        }

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(appointmentInsertSql(OLD_APPOINTMENT_ID, "2026-11-12T01:00:00Z", 1794445200000L));
            statement.executeUpdate(appointmentInsertSql(NEW_APPOINTMENT_ID, "2026-11-13T01:00:00Z", 1794531600000L));
        }
    }

    private String appointmentInsertSql(UUID appointmentId, String appointmentAt, long epochMillis) {
        return """
                insert into bodeul.appointment_requests (
                    id, patient_user_id, requester_user_id, requester_role, patient_name,
                    hospital_name, department_name, appointment_at, appointment_at_epoch_millis,
                    appointment_date_key, mobility_support_code, trip_type_code,
                    manager_gender_preference_code, status, payment_method_code, coupon_code,
                    payment_status_code, created_at, updated_at
                ) values (
                    '%s', '%s', '%s', 'PATIENT', '검증 환자', '%s', '%s', '%s', %d,
                    '%s', 'INDEPENDENT', 'ONE_WAY', 'ANY', 'REQUESTED', 'CARD', 'NONE',
                    'AUTHORIZED', '2026-10-26T00:00:00Z', '2026-10-26T00:00:00Z'
                )
                """.formatted(
                appointmentId,
                PATIENT_ID,
                PATIENT_ID,
                PreviewHospitalGuideV1PromotionApplication.HOSPITAL_NAME,
                PreviewHospitalGuideV1PromotionApplication.DEPARTMENT_NAME,
                appointmentAt,
                epochMillis,
                appointmentAt.substring(0, 10));
    }

    private UUID assign(UUID appointmentId, String reason) throws SQLException {
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select bodeul.assign_companion_session(?::uuid, ?::uuid, ?::uuid, 0, ?)")) {
            statement.setObject(1, appointmentId);
            statement.setObject(2, MANAGER_ID);
            statement.setObject(3, ADMIN_ID);
            statement.setString(4, reason);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getObject(1, UUID.class);
            }
        }
    }

    private GuideState readGuide(UUID guideId) throws Exception {
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select revision,
                            step_contract_version,
                            steps::text,
                            bodeul.is_valid_guide_steps_v1(steps),
                            bodeul.is_valid_guide_step_media_v1(steps)
                     from bodeul.hospital_guides
                     where id = ?::uuid
                     """)) {
            statement.setObject(1, guideId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new GuideState(
                        resultSet.getLong(1),
                        resultSet.getInt(2),
                        OBJECT_MAPPER.readTree(resultSet.getString(3)),
                        resultSet.getBoolean(4),
                        resultSet.getBoolean(5));
            }
        }
    }

    private SessionSnapshot readSession(UUID sessionId) throws SQLException {
        try (Connection connection = ownerConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     select guide_id,
                            guide_snapshot_source,
                            guide_step_contract_version,
                            guide_revision,
                            guide_steps_snapshot::text
                     from bodeul.companion_sessions
                     where id = ?::uuid
                     """)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return new SessionSnapshot(
                        resultSet.getObject(1, UUID.class),
                        resultSet.getString(2),
                        resultSet.getInt(3),
                        resultSet.getLong(4),
                        resultSet.getString(5));
            }
        }
    }

    private void assertPromotedContent(JsonNode before, JsonNode after) {
        assertThat(after).hasSize(7);
        for (int index = 0; index < after.size(); index++) {
            assertThat(after.get(index).path("code").asText())
                    .isEqualTo(PreviewHospitalGuideV1PromotionApplication.LEGACY_STEP_CODES.get(index));
            assertThat(after.get(index).path("order").asInt()).isEqualTo(index + 1);
            assertThat(after.get(index).path("title")).isEqualTo(before.get(index).path("title"));
            assertThat(after.get(index).path("description")).isEqualTo(before.get(index).path("description"));
        }
        assertThat(after.get(0).path("custom").asText()).isEqualTo("keep");
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                databaseConfig.jdbcUrl(),
                databaseConfig.username(),
                databaseConfig.password());
    }

    private void cleanFixture(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    delete from bodeul.companion_session_assignment_audits
                    where appointment_request_id in (
                        'a2600000-0000-0000-0000-000000000020',
                        'a2600000-0000-0000-0000-000000000021'
                    )
                    """);
            statement.executeUpdate("""
                    delete from bodeul.companion_sessions
                    where appointment_request_id in (
                        'a2600000-0000-0000-0000-000000000020',
                        'a2600000-0000-0000-0000-000000000021'
                    )
                    """);
            statement.executeUpdate("""
                    delete from bodeul.appointment_requests
                    where id in (
                        'a2600000-0000-0000-0000-000000000020',
                        'a2600000-0000-0000-0000-000000000021'
                    )
                    """);
            statement.executeUpdate("""
                    delete from bodeul.hospital_guides
                    where id in (
                        'a2600000-0000-0000-0000-000000000010',
                        'a2600000-0000-0000-0000-000000000011'
                    )
                    """);
            statement.executeUpdate("""
                    delete from bodeul.admin_role_assignments
                    where admin_user_id = 'a2600000-0000-0000-0000-000000000003'
                    """);
            statement.executeUpdate("""
                    delete from bodeul.app_users
                    where id in (
                        'a2600000-0000-0000-0000-000000000001',
                        'a2600000-0000-0000-0000-000000000002',
                        'a2600000-0000-0000-0000-000000000003'
                    )
                    """);
        }
    }

    record GuideState(
            long revision,
            int contractVersion,
            JsonNode steps,
            boolean validSteps,
            boolean validMedia
    ) {
    }

    record SessionSnapshot(UUID guideId, String source, int contractVersion, long guideRevision, String steps) {
    }
}
