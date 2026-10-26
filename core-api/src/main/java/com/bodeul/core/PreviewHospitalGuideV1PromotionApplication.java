package com.bodeul.core;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 개발 DB의 특정 코드 없는 7단계 병원 가이드를 V1 단계 계약으로 승격한다.
 */
public final class PreviewHospitalGuideV1PromotionApplication {

    static final String ACTION_ENV = "HOSPITAL_GUIDE_V1_ACTION";
    static final String TARGET_ENV = "MIGRATION_TARGET";
    static final String CONFIRM_PROJECT_ENV = "HOSPITAL_GUIDE_V1_CONFIRM_PROJECT";
    static final String CONFIRM_TARGET_ENV = "HOSPITAL_GUIDE_V1_CONFIRM_TARGET";
    static final String JDBC_URL_ENV = "MIGRATION_DB_JDBC_URL";
    static final String DB_USERNAME_ENV = "MIGRATION_DB_USERNAME";
    static final String DB_PASSWORD_ENV = "MIGRATION_DB_PASSWORD";

    static final String PREVIEW_TARGET = "preview";
    static final String PREVIEW_PROJECT = "bodeul-dev";
    static final String PREVIEW_DATABASE_REF = "parpdzttloacinyvhwmx";
    static final String HOSPITAL_NAME = "서울내과병원";
    static final String DEPARTMENT_NAME = "내과";
    static final String GUIDE_TARGET = HOSPITAL_NAME + "/" + DEPARTMENT_NAME;

    static final List<String> LEGACY_STEP_CODES = List.of(
            "LEGACY_CORE_PATIENT_CONTACT",
            "LEGACY_CORE_RECEPTION_PREPARATION",
            "LEGACY_CORE_RECEPTION",
            "LEGACY_CORE_CONSULTATION",
            "LEGACY_CORE_PAYMENT",
            "LEGACY_CORE_PHARMACY",
            "LEGACY_CORE_RETURN_AND_CLOSE");

    static final List<String> LEGACY_STEP_TITLES = List.of(
            "환자 접수",
            "접수 등록",
            "진료 접수",
            "진료 완료",
            "수납 처리",
            "약국 방문",
            "환자 귀가(서비스 종료)");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PreviewHospitalGuideV1PromotionApplication() {
    }

    public static void main(String[] args) {
        int exitCode = run(
                System.getenv(),
                System.out,
                System.err,
                PreviewHospitalGuideV1PromotionApplication::execute);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            Map<String, String> environment,
            PrintStream standardOutput,
            PrintStream errorOutput,
            PromotionExecutor executor
    ) {
        try {
            PromotionAction action = promotionAction(requiredEnvironment(environment, ACTION_ENV));
            String target = requiredEnvironment(environment, TARGET_ENV);
            String confirmedProject = requiredEnvironment(environment, CONFIRM_PROJECT_ENV);
            String confirmedGuideTarget = requiredEnvironment(environment, CONFIRM_TARGET_ENV);
            if (!PREVIEW_TARGET.equals(target)
                    || !PREVIEW_PROJECT.equals(confirmedProject)
                    || !GUIDE_TARGET.equals(confirmedGuideTarget)) {
                throw new PromotionValidationException("개발 환경과 병원 가이드 확인값이 일치하지 않습니다.");
            }

            String jdbcUrl = requiredEnvironment(environment, JDBC_URL_ENV);
            String databaseUsername = requiredEnvironment(environment, DB_USERNAME_ENV);
            try {
                DatabaseMigrationReadinessApplication.validateConnectionTarget(
                        jdbcUrl,
                        databaseUsername,
                        PREVIEW_DATABASE_REF);
            } catch (IllegalStateException exception) {
                throw new PromotionValidationException("개발 DB 연결 대상이 허용 목록과 일치하지 않습니다.");
            }

            DatabaseConfig databaseConfig = new DatabaseConfig(
                    jdbcUrl,
                    databaseUsername,
                    requiredEnvironment(environment, DB_PASSWORD_ENV));
            PromotionSummary summary = executor.execute(databaseConfig, action);
            standardOutput.println(summary.format());
            return 0;
        } catch (PromotionValidationException exception) {
            errorOutput.println("개발 병원 가이드 V1 전환을 중단했습니다. " + exception.getMessage());
            return 2;
        } catch (Exception exception) {
            reportExecutionFailure(exception, errorOutput);
            return 3;
        }
    }

    private static PromotionAction promotionAction(String value) throws PromotionValidationException {
        try {
            return PromotionAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new PromotionValidationException("지원하는 작업은 status, apply입니다.");
        }
    }

    private static String requiredEnvironment(Map<String, String> environment, String name)
            throws PromotionValidationException {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new PromotionValidationException(name + " 환경변수가 비어 있습니다.");
        }
        return value.trim();
    }

    static PromotionSummary execute(DatabaseConfig config, PromotionAction action) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                config.jdbcUrl(), config.username(), config.password())) {
            boolean readOnly = action == PromotionAction.STATUS;
            connection.setAutoCommit(false);
            connection.setReadOnly(readOnly);
            try {
                configureTransaction(connection, readOnly);
                GuideRecord before = readTargetGuide(connection, action == PromotionAction.APPLY);
                StepInspection beforeInspection = inspectGuide(before);

                int affectedRows = 0;
                GuideRecord after = before;
                StepInspection afterInspection = beforeInspection;
                if (action == PromotionAction.APPLY && !beforeInspection.ready()) {
                    if (!beforeInspection.promotable()) {
                        throw new PromotionValidationException("대상 가이드는 안전하게 전환할 수 없는 상태입니다.");
                    }

                    String promotedSteps = promoteLegacySteps(before.stepsJson());
                    affectedRows = updateGuide(connection, before, promotedSteps);
                    after = readTargetGuide(connection, true);
                    afterInspection = inspectGuide(after);
                    verifyPromotion(before, after, before.stepsJson(), after.stepsJson(), afterInspection);
                }

                if (readOnly) {
                    connection.rollback();
                } else {
                    connection.commit();
                }
                return new PromotionSummary(
                        action,
                        affectedRows,
                        after.revision(),
                        after.contractVersion(),
                        afterInspection.stepCount(),
                        afterInspection.promotable(),
                        afterInspection.ready());
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    exception.addSuppressed(rollbackException);
                }
                throw exception;
            }
        }
    }

    private static void configureTransaction(Connection connection, boolean readOnly) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            if (readOnly) {
                statement.execute("set transaction read only");
            }
            statement.execute("set local role bodeul_migration");
            statement.execute("set local lock_timeout = '5s'");
            statement.execute("set local statement_timeout = '60s'");
        }
    }

    private static GuideRecord readTargetGuide(Connection connection, boolean lockForUpdate)
            throws SQLException, PromotionValidationException {
        String sql = """
                select
                    id,
                    revision,
                    step_contract_version,
                    steps::text as steps_json,
                    bodeul.is_valid_guide_steps_v1(steps) as valid_steps,
                    bodeul.is_valid_guide_step_media_v1(steps) as valid_media
                from bodeul.hospital_guides
                where hospital_name = ? and department_name = ?
                order by id
                limit 2
                """ + (lockForUpdate ? " for update" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(60);
            statement.setString(1, HOSPITAL_NAME);
            statement.setString(2, DEPARTMENT_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new PromotionValidationException("대상 병원·진료과 가이드가 없습니다.");
                }
                GuideRecord guide = new GuideRecord(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getLong("revision"),
                        resultSet.getInt("step_contract_version"),
                        resultSet.getString("steps_json"),
                        resultSet.getBoolean("valid_steps"),
                        resultSet.getBoolean("valid_media"));
                if (resultSet.next()) {
                    throw new PromotionValidationException("대상 병원·진료과 가이드가 한 건이 아닙니다.");
                }
                return guide;
            }
        }
    }

    private static int updateGuide(Connection connection, GuideRecord before, String promotedSteps)
            throws SQLException, PromotionValidationException {
        try (PreparedStatement statement = connection.prepareStatement("""
                update bodeul.hospital_guides
                set steps = ?::jsonb,
                    step_contract_version = 1
                where id = ?::uuid
                  and revision = ?
                  and step_contract_version = 0
                  and steps = ?::jsonb
                """)) {
            statement.setQueryTimeout(60);
            statement.setString(1, promotedSteps);
            statement.setObject(2, before.id());
            statement.setLong(3, before.revision());
            statement.setString(4, before.stepsJson());
            int affectedRows = statement.executeUpdate();
            if (affectedRows != 1) {
                throw new PromotionValidationException("동시 변경이 감지되어 대상 가이드를 수정하지 않았습니다.");
            }
            return affectedRows;
        }
    }

    static StepInspection inspectGuide(GuideRecord guide) throws PromotionValidationException {
        if (guide.contractVersion() == 0) {
            if (!guide.validMedia()) {
                throw new PromotionValidationException("기존 가이드의 미디어 정보가 V1 계약과 호환되지 않습니다.");
            }
            JsonNode promoted = parseSteps(promoteLegacySteps(guide.stepsJson()));
            return new StepInspection(promoted.size(), true, false);
        }
        if (guide.contractVersion() == 1) {
            JsonNode steps = parseSteps(guide.stepsJson());
            validateReadySteps(steps);
            if (!guide.validSteps() || !guide.validMedia()) {
                throw new PromotionValidationException("V1 가이드가 DB 검증 함수를 통과하지 못했습니다.");
            }
            return new StepInspection(steps.size(), false, true);
        }
        throw new PromotionValidationException("지원하지 않는 단계 계약 버전입니다.");
    }

    static String promoteLegacySteps(String stepsJson) throws PromotionValidationException {
        JsonNode root = parseSteps(stepsJson);
        if (!root.isArray() || root.size() != LEGACY_STEP_CODES.size()) {
            throw new PromotionValidationException("코드 없는 기존 가이드는 정확히 7단계여야 합니다.");
        }

        ArrayNode promoted = OBJECT_MAPPER.createArrayNode();
        for (int index = 0; index < root.size(); index++) {
            JsonNode step = root.get(index);
            if (!step.isObject()) {
                throw new PromotionValidationException("모든 가이드 단계는 객체여야 합니다.");
            }
            JsonNode title = step.get("title");
            JsonNode description = step.get("description");
            if (title == null || !title.isTextual() || title.asText().isBlank()) {
                throw new PromotionValidationException("모든 가이드 단계에는 비어 있지 않은 제목이 필요합니다.");
            }
            if (!LEGACY_STEP_TITLES.get(index).equals(title.asText())) {
                throw new PromotionValidationException("기존 가이드 단계 제목 순서가 승인된 7단계 기준과 다릅니다.");
            }
            if (description == null || !description.isTextual()) {
                throw new PromotionValidationException("모든 가이드 단계에는 문자열 설명이 필요합니다.");
            }
            if (step.has("code")) {
                throw new PromotionValidationException("기존 가이드에 예상하지 않은 단계 코드가 있습니다.");
            }
            JsonNode order = step.get("order");
            if (order != null
                    && (!order.isIntegralNumber()
                    || !order.canConvertToInt()
                    || order.asInt() != index + 1)) {
                throw new PromotionValidationException("기존 가이드 단계 순서가 배열 위치와 일치하지 않습니다.");
            }

            ObjectNode converted = ((ObjectNode) step).deepCopy();
            converted.remove("code");
            converted.put("code", LEGACY_STEP_CODES.get(index));
            converted.put("order", index + 1);
            promoted.add(converted);
        }
        return writeSteps(promoted);
    }

    private static void validateReadySteps(JsonNode steps) throws PromotionValidationException {
        if (!steps.isArray() || steps.size() != LEGACY_STEP_CODES.size()) {
            throw new PromotionValidationException("전환된 V1 가이드는 정확히 7단계여야 합니다.");
        }
        for (int index = 0; index < steps.size(); index++) {
            JsonNode step = steps.get(index);
            if (!step.isObject()
                    || !LEGACY_STEP_CODES.get(index).equals(step.path("code").asText())
                    || !step.path("order").isIntegralNumber()
                    || !step.path("order").canConvertToInt()
                    || step.path("order").asInt() != index + 1
                    || !step.path("title").isTextual()
                    || step.path("title").asText().isBlank()
                    || !step.path("description").isTextual()) {
                throw new PromotionValidationException("V1 가이드 단계 코드·순서·표시 정보가 예상 계약과 다릅니다.");
            }
        }
    }

    private static void verifyPromotion(
            GuideRecord before,
            GuideRecord after,
            String beforeSteps,
            String afterSteps,
            StepInspection inspection
    ) throws PromotionValidationException {
        if (!before.id().equals(after.id())
                || after.revision() != before.revision() + 1
                || after.contractVersion() != 1
                || !inspection.ready()
                || inspection.stepCount() != LEGACY_STEP_CODES.size()) {
            throw new PromotionValidationException("전환 뒤 가이드 revision과 V1 계약 검증에 실패했습니다.");
        }
        verifyStepContentPreserved(beforeSteps, afterSteps);
    }

    static void verifyStepContentPreserved(String beforeSteps, String afterSteps)
            throws PromotionValidationException {
        JsonNode before = parseSteps(beforeSteps);
        JsonNode after = parseSteps(afterSteps);
        if (!before.isArray() || !after.isArray() || before.size() != after.size()) {
            throw new PromotionValidationException("전환 전후 단계 수가 달라졌습니다.");
        }
        for (int index = 0; index < before.size(); index++) {
            ObjectNode beforeStep = ((ObjectNode) before.get(index)).deepCopy();
            ObjectNode afterStep = ((ObjectNode) after.get(index)).deepCopy();
            beforeStep.remove(List.of("code", "order"));
            afterStep.remove(List.of("code", "order"));
            if (!beforeStep.equals(afterStep)) {
                throw new PromotionValidationException("전환 과정에서 기존 제목·설명 또는 추가 정보가 변경되었습니다.");
            }
        }
    }

    private static JsonNode parseSteps(String stepsJson) throws PromotionValidationException {
        try {
            return OBJECT_MAPPER.readTree(stepsJson);
        } catch (JsonProcessingException exception) {
            throw new PromotionValidationException("가이드 단계 JSON을 해석할 수 없습니다.");
        }
    }

    private static String writeSteps(JsonNode steps) throws PromotionValidationException {
        try {
            return OBJECT_MAPPER.writeValueAsString(steps);
        } catch (JsonProcessingException exception) {
            throw new PromotionValidationException("V1 가이드 단계 JSON을 만들 수 없습니다.");
        }
    }

    private static void reportExecutionFailure(Exception exception, PrintStream errorOutput) {
        SQLException sqlException = findSqlException(exception);
        if (sqlException != null) {
            String sqlState = sqlException.getSQLState();
            errorOutput.println("개발 병원 가이드 V1 DB 작업에 실패했습니다. SQLSTATE="
                    + (sqlState == null ? "unknown" : sqlState));
            return;
        }
        errorOutput.println("개발 병원 가이드 V1 작업에 실패했습니다. 원인 유형="
                + exception.getClass().getSimpleName());
    }

    private static SQLException findSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
            current = current.getCause();
        }
        return null;
    }

    enum PromotionAction {
        STATUS,
        APPLY
    }

    record DatabaseConfig(String jdbcUrl, String username, String password) {
    }

    record GuideRecord(
            UUID id,
            long revision,
            int contractVersion,
            String stepsJson,
            boolean validSteps,
            boolean validMedia
    ) {
    }

    record StepInspection(int stepCount, boolean promotable, boolean ready) {
    }

    record PromotionSummary(
            PromotionAction action,
            int affectedRows,
            long revision,
            int contractVersion,
            int stepCount,
            boolean promotable,
            boolean ready
    ) {
        String format() {
            return ("action=%s affectedRows=%d revision=%d contractVersion=%d "
                    + "stepCount=%d promotable=%s ready=%s")
                    .formatted(
                            action,
                            affectedRows,
                            revision,
                            contractVersion,
                            stepCount,
                            promotable,
                            ready);
        }
    }

    @FunctionalInterface
    interface PromotionExecutor {
        PromotionSummary execute(DatabaseConfig databaseConfig, PromotionAction action) throws Exception;
    }

    static final class PromotionValidationException extends Exception {
        PromotionValidationException(String message) {
            super(message);
        }
    }
}
