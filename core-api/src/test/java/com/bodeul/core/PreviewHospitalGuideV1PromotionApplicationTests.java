package com.bodeul.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewHospitalGuideV1PromotionApplicationTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LEGACY_STEPS = """
            [
              {"order":1,"title":"환자 접수","description":"환자 상태를 확인합니다.","custom":"keep-1"},
              {"order":2,"title":"접수 등록","description":"접수 정보를 확인합니다."},
              {"order":3,"title":"진료 접수","description":"진료 접수를 진행합니다."},
              {"order":4,"title":"진료 완료","description":"진료 결과를 정리합니다."},
              {"order":5,"title":"수납 처리","description":"수납 여부를 확인합니다."},
              {"order":6,"title":"약국 방문","description":"처방전을 확인합니다."},
              {"order":7,"title":"환자 귀가(서비스 종료)","description":"귀가 상황을 공유합니다.","extra":{"safe":true}}
            ]
            """;

    @Test
    void runsOnlyAgainstConfirmedPreviewTarget() {
        AtomicReference<PreviewHospitalGuideV1PromotionApplication.PromotionAction> executedAction =
                new AtomicReference<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = PreviewHospitalGuideV1PromotionApplication.run(
                environment("status"),
                new PrintStream(output, true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    executedAction.set(action);
                    return summary(action, 0, 4, 0, true, false);
                });

        assertThat(exitCode).isZero();
        assertThat(executedAction)
                .hasValue(PreviewHospitalGuideV1PromotionApplication.PromotionAction.STATUS);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("action=STATUS")
                .contains("contractVersion=0")
                .contains("promotable=true")
                .doesNotContain("migration-password")
                .doesNotContain("parpdzttloacinyvhwmx");
        assertThat(error.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void rejectsProductionWrongProjectAndWrongGuideTarget() {
        Map<String, String> production = mutableEnvironment("status");
        production.put(PreviewHospitalGuideV1PromotionApplication.TARGET_ENV, "production");
        Map<String, String> wrongProject = mutableEnvironment("apply");
        wrongProject.put(PreviewHospitalGuideV1PromotionApplication.CONFIRM_PROJECT_ENV, "bodeul-prod-110");
        Map<String, String> wrongGuide = mutableEnvironment("status");
        wrongGuide.put(PreviewHospitalGuideV1PromotionApplication.CONFIRM_TARGET_ENV, "다른병원/내과");

        assertThat(runWithoutDatabase(production)).isEqualTo(2);
        assertThat(runWithoutDatabase(wrongProject)).isEqualTo(2);
        assertThat(runWithoutDatabase(wrongGuide)).isEqualTo(2);
    }

    @Test
    void rejectsUnknownActionAndUnapprovedDatabaseTarget() {
        Map<String, String> productionDatabase = mutableEnvironment("status");
        productionDatabase.put(
                PreviewHospitalGuideV1PromotionApplication.JDBC_URL_ENV,
                "jdbc:postgresql://db.aaaaaaaaaaaaaaaaaaaa.supabase.co:5432/postgres?sslmode=require");

        assertThat(runWithoutDatabase(environment("delete"))).isEqualTo(2);
        assertThat(runWithoutDatabase(productionDatabase)).isEqualTo(2);
    }

    @Test
    void databaseErrorsExposeOnlySqlState() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = PreviewHospitalGuideV1PromotionApplication.run(
                environment("apply"),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    throw new SQLException("sensitive guide row", "40001", 0);
                });

        assertThat(exitCode).isEqualTo(3);
        assertThat(error.toString(StandardCharsets.UTF_8))
                .contains("SQLSTATE=40001")
                .doesNotContain("sensitive guide row")
                .doesNotContain("migration-password")
                .doesNotContain("parpdzttloacinyvhwmx");
    }

    @Test
    void unexpectedExecutorErrorsDoNotExposeTheirMessage() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();

        int exitCode = PreviewHospitalGuideV1PromotionApplication.run(
                environment("status"),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(error, true, StandardCharsets.UTF_8),
                (databaseConfig, action) -> {
                    throw new IllegalStateException("jdbc:postgresql://secret password=exposed");
                });

        assertThat(exitCode).isEqualTo(3);
        assertThat(error.toString(StandardCharsets.UTF_8))
                .contains("원인 유형=IllegalStateException")
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("password=exposed");
    }

    @Test
    void addsLegacyCodesAndPreservesExistingContent() throws Exception {
        String promoted = PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(LEGACY_STEPS);
        JsonNode before = OBJECT_MAPPER.readTree(LEGACY_STEPS);
        JsonNode after = OBJECT_MAPPER.readTree(promoted);

        assertThat(after).hasSize(7);
        for (int index = 0; index < after.size(); index++) {
            assertThat(after.get(index).path("code").asText())
                    .isEqualTo(PreviewHospitalGuideV1PromotionApplication.LEGACY_STEP_CODES.get(index));
            assertThat(after.get(index).path("order").asInt()).isEqualTo(index + 1);
            assertThat(after.get(index).path("title").asText())
                    .isEqualTo(before.get(index).path("title").asText());
            assertThat(after.get(index).path("description").asText())
                    .isEqualTo(before.get(index).path("description").asText());
        }
        assertThat(after.get(0).path("custom").asText()).isEqualTo("keep-1");
        assertThat(after.get(6).path("extra").path("safe").asBoolean()).isTrue();
        PreviewHospitalGuideV1PromotionApplication.verifyStepContentPreserved(LEGACY_STEPS, promoted);
    }

    @Test
    void rejectsUnexpectedLegacyShapesWithoutGuessingMeaning() {
        List<String> sixSteps = new ArrayList<>();
        for (int index = 0; index < 6; index++) {
            sixSteps.add("{\"title\":\"단계\",\"description\":\"설명\"}");
        }
        String wrongOrder = LEGACY_STEPS.replace("\"order\":2", "\"order\":7");
        String fractionalOrder = LEGACY_STEPS.replace("\"order\":1", "\"order\":1.5");
        String existingCode = LEGACY_STEPS.replace(
                "{\"order\":1",
                "{\"code\":\"UNKNOWN\",\"order\":1");
        String nullCode = LEGACY_STEPS.replace(
                "{\"order\":1",
                "{\"code\":null,\"order\":1");
        String blankTitle = LEGACY_STEPS.replace("\"title\":\"환자 접수\"", "\"title\":\" \"");
        String swappedMeaning = LEGACY_STEPS
                .replace("\"title\":\"환자 접수\"", "\"title\":\"__TEMP__\"")
                .replace("\"title\":\"약국 방문\"", "\"title\":\"환자 접수\"")
                .replace("\"title\":\"__TEMP__\"", "\"title\":\"약국 방문\"");
        String numericDescription = LEGACY_STEPS.replace(
                "\"description\":\"환자 상태를 확인합니다.\"",
                "\"description\":1");

        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(
                "[" + String.join(",", sixSteps) + "]"))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(wrongOrder))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(fractionalOrder))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(existingCode))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(nullCode))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(blankTitle))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(swappedMeaning))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(numericDescription))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class);
    }

    @Test
    void recognizesOnlyTheExactPromotedV1ContractAsReady() throws Exception {
        String promoted = PreviewHospitalGuideV1PromotionApplication.promoteLegacySteps(LEGACY_STEPS);
        PreviewHospitalGuideV1PromotionApplication.GuideRecord readyGuide =
                new PreviewHospitalGuideV1PromotionApplication.GuideRecord(
                        UUID.randomUUID(),
                        5,
                        1,
                        promoted,
                        true,
                        true);
        PreviewHospitalGuideV1PromotionApplication.GuideRecord legacyGuide =
                new PreviewHospitalGuideV1PromotionApplication.GuideRecord(
                        UUID.randomUUID(),
                        4,
                        0,
                        LEGACY_STEPS,
                        false,
                        true);

        assertThat(PreviewHospitalGuideV1PromotionApplication.inspectGuide(readyGuide))
                .isEqualTo(new PreviewHospitalGuideV1PromotionApplication.StepInspection(7, false, true));
        assertThat(PreviewHospitalGuideV1PromotionApplication.inspectGuide(legacyGuide))
                .isEqualTo(new PreviewHospitalGuideV1PromotionApplication.StepInspection(7, true, false));
    }

    @Test
    void rejectsLegacyGuideWithIncompatibleMediaMetadata() {
        PreviewHospitalGuideV1PromotionApplication.GuideRecord invalidMediaGuide =
                new PreviewHospitalGuideV1PromotionApplication.GuideRecord(
                        UUID.randomUUID(),
                        4,
                        0,
                        LEGACY_STEPS,
                        false,
                        false);

        assertThatThrownBy(() -> PreviewHospitalGuideV1PromotionApplication.inspectGuide(invalidMediaGuide))
                .isInstanceOf(PreviewHospitalGuideV1PromotionApplication.PromotionValidationException.class)
                .hasMessageContaining("미디어");
    }

    @Test
    void protectedWorkflowKeepsHospitalGuidePromotionSeparateFromFlyway() throws Exception {
        String workflow = Files.readString(
                Path.of("../.github/workflows/core-api-migration.yml"),
                StandardCharsets.UTF_8);

        assertThat(workflow)
                .contains("hospital_guide_v1_action:")
                .contains("if: inputs.hospital_guide_v1_action == 'none'")
                .contains("if: inputs.hospital_guide_v1_action != 'none'")
                .contains("refs/heads/dev")
                .contains("runPreviewHospitalGuideV1Promotion")
                .contains("서울내과병원/내과")
                .contains("environment: core-api-migration-${{ inputs.target }}");
    }

    private int runWithoutDatabase(Map<String, String> environment) {
        return PreviewHospitalGuideV1PromotionApplication.run(
                environment,
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()),
                (databaseConfig, action) -> {
                    throw new AssertionError("검증 실패 시 DB 실행기가 호출되면 안 됩니다.");
                });
    }

    private Map<String, String> mutableEnvironment(String action) {
        return new HashMap<>(environment(action));
    }

    private Map<String, String> environment(String action) {
        return Map.of(
                PreviewHospitalGuideV1PromotionApplication.ACTION_ENV, action,
                PreviewHospitalGuideV1PromotionApplication.TARGET_ENV,
                PreviewHospitalGuideV1PromotionApplication.PREVIEW_TARGET,
                PreviewHospitalGuideV1PromotionApplication.CONFIRM_PROJECT_ENV,
                PreviewHospitalGuideV1PromotionApplication.PREVIEW_PROJECT,
                PreviewHospitalGuideV1PromotionApplication.CONFIRM_TARGET_ENV,
                PreviewHospitalGuideV1PromotionApplication.GUIDE_TARGET,
                PreviewHospitalGuideV1PromotionApplication.JDBC_URL_ENV,
                "jdbc:postgresql://db.parpdzttloacinyvhwmx.supabase.co:5432/postgres?sslmode=require",
                PreviewHospitalGuideV1PromotionApplication.DB_USERNAME_ENV, "bodeul_migrator",
                PreviewHospitalGuideV1PromotionApplication.DB_PASSWORD_ENV, "migration-password");
    }

    private PreviewHospitalGuideV1PromotionApplication.PromotionSummary summary(
            PreviewHospitalGuideV1PromotionApplication.PromotionAction action,
            int affectedRows,
            long revision,
            int contractVersion,
            boolean promotable,
            boolean ready
    ) {
        return new PreviewHospitalGuideV1PromotionApplication.PromotionSummary(
                action,
                affectedRows,
                revision,
                contractVersion,
                7,
                promotable,
                ready);
    }
}
