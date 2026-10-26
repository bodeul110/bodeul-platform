package com.bodeul.core.appointment;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppCheckTokenVerifier;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "bodeul.app-check.mode=observe")
@AutoConfigureMockMvc
@ActiveProfiles({"local", "appointment-test"})
@Import(AppointmentApiIntegrationTests.AppointmentApiTestConfiguration.class)
class AppointmentApiIntegrationTests {

    private static final UUID USER_ID = UUID.fromString("aab5363c-4021-4390-af5d-4f9259796c77");
    private static final UUID APPOINTMENT_ID = UUID.fromString("df1f95fd-5558-41bc-99e9-c5a3981661b9");
    private static final UUID MANAGER_ID = UUID.fromString("3da53272-577e-4d5b-9c86-f1cf7a787e5b");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableAppointmentService appointmentService;

    @Autowired
    private MutableAppointmentPaymentService appointmentPaymentService;

    @BeforeEach
    void reset() {
        appointmentService.reset();
        appointmentPaymentService.reset();
    }

    @Test
    void patientCanReadBankTransferPaymentWithoutAccountInstructions() throws Exception {
        appointmentPaymentService.result = payment();

        mockMvc.perform(get("/api/appointments/{appointmentId}/payment", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.paymentStatusCode").value("AWAITING_DEPOSIT"))
                .andExpect(jsonPath("$.expectedAmount").value(96_000))
                .andExpect(jsonPath("$.instructionAvailable").value(false));

        assertThat(appointmentPaymentService.lastAppointmentId).isEqualTo(APPOINTMENT_ID);
    }

    @Test
    void depositorPatchPassesOperationIdAndPaymentVersion() throws Exception {
        UUID operationId = UUID.fromString("7c0e6412-34bf-47d2-a82d-ddc5f385b17f");
        appointmentPaymentService.result = payment();

        mockMvc.perform(patch("/api/appointments/{appointmentId}/payment/depositor", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "operationId": "%s",
                                  "paymentVersion": 0,
                                  "depositorName": "홍길동"
                                }
                                """.formatted(operationId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        assertThat(appointmentPaymentService.lastCommand.operationId()).isEqualTo(operationId);
        assertThat(appointmentPaymentService.lastCommand.paymentVersion()).isZero();
        assertThat(appointmentPaymentService.lastCommand.depositorName()).isEqualTo("홍길동");
    }

    @Test
    void appointmentListRequiresFirebaseAuthentication() throws Exception {
        mockMvc.perform(get("/api/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
    }

    @Test
    void authenticatedUserCanReadOwnAppointments() throws Exception {
        appointmentService.result = appointment();

        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointments[0].id").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.appointments[0].publicCode").value("BD-ABC123"))
                .andExpect(jsonPath("$.appointments[0].managerName").value("매니저 사용자"))
                .andExpect(jsonPath("$.appointments[0].version").value(2));

        assertThat(appointmentService.lastUser.id()).isEqualTo(USER_ID);
    }

    @Test
    void legacyCreatePathPassesMissingPriceToServiceForExistingRetry() throws Exception {
        appointmentService.result = appointment();
        UUID clientRequestId = UUID.fromString("1462354f-7162-42c0-9e40-d66b6d73b0f4");

        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(validCreateJson(clientRequestId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/appointments/" + APPOINTMENT_ID))
                .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()));

        assertThat(appointmentService.lastCreateCommand.clientRequestId()).isEqualTo(clientRequestId);
        assertThat(appointmentService.lastCreateCommand.draft().hospitalName()).isEqualTo("서울대학교병원");
        assertThat(appointmentService.lastCreateCommand.pricePolicyVersion()).isNull();
        assertThat(appointmentService.lastCreateCommand.expectedFinalPrice()).isNull();
    }

    @Test
    void priceConfirmedPathPassesPriceAndIdempotencyContract() throws Exception {
        appointmentService.result = appointment();
        UUID requestId = UUID.randomUUID();
        var body = (ObjectNode) new ObjectMapper().readTree(validCreateJson(requestId));
        body.put("pricePolicyVersion", "mvp-fixed-40000-v1")
                .put("expectedFinalPrice", 40_000);

        mockMvc.perform(post("/api/appointments/price-confirmed")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(body.toString()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"));

        assertThat(appointmentService.lastCreateCommand.clientRequestId()).isEqualTo(requestId);
        assertThat(appointmentService.lastCreateCommand.pricePolicyVersion()).isEqualTo("mvp-fixed-40000-v1");
        assertThat(appointmentService.lastCreateCommand.expectedFinalPrice()).isEqualTo(40_000);
    }

    @Test
    void bothCreationPathsRejectMissingPriceWithRealServiceBeforeAnyWrite() throws Exception {
        var repository = mock(AppointmentRepository.class);
        var profiles = mock(AppUserProfileRepository.class);
        appointmentService.createDelegate = new DefaultAppointmentService(
                repository, profiles, (user, appointment, patient, guardian, scope) -> false,
                Clock.systemUTC());

        for (String path : List.of("/api/appointments", "/api/appointments/price-confirmed")) {
            mockMvc.perform(post(path)
                            .header("Authorization", "Bearer valid-token")
                            .contentType("application/json")
                            .content(validCreateJson(UUID.randomUUID())))
                    .andExpect(status().isConflict())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.error").value("appointment_price_confirmation_required"));
        }
        verifyNoInteractions(profiles);
        verify(repository, never()).insert(any(), anyString(), anyString());
    }

    @Test
    void priceConfirmedCreationStillRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/appointments/price-confirmed")
                        .contentType("application/json")
                        .content(validCreateJson(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_authorization"));
        assertThat(appointmentService.lastCreateCommand).isNull();
    }

    @Test
    void confirmationAmountRejectsCoercedOrOutOfRangeValues() throws Exception {
        var mapper = new ObjectMapper();
        for (String amount : List.of("\"40000\"", "40000.5", "40000.0", "2147483648", "true", "{}", "[]")) {
            var body = (ObjectNode) mapper.readTree(validCreateJson(UUID.randomUUID()));
            body.set("expectedFinalPrice", mapper.readTree(amount));
            mockMvc.perform(post("/api/appointments/price-confirmed")
                            .header("Authorization", "Bearer valid-token")
                            .contentType("application/json")
                            .content(body.toString()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_appointment_request"));
            assertThat(appointmentService.lastCreateCommand).isNull();
        }
    }

    @Test
    void updatePassesVersionAndPathId() throws Exception {
        appointmentService.result = appointment();

        mockMvc.perform(put("/api/appointments/{appointmentId}", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content(validUpdateJson(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(appointmentService.lastAppointmentId).isEqualTo(APPOINTMENT_ID);
        assertThat(appointmentService.lastUpdateCommand.version()).isEqualTo(2);
    }

    @Test
    void invalidPathIdReturns400() throws Exception {
        mockMvc.perform(get("/api/appointments/not-a-uuid")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_appointment_request"));
    }

    @Test
    void servicePermissionFailureReturns403() throws Exception {
        appointmentService.failure = AppointmentException.permissionDenied();

        mockMvc.perform(get("/api/appointments/{appointmentId}", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("appointment_permission_denied"));
    }

    @Test
    void databaseFailureReturns503WithoutInternalMessage() throws Exception {
        appointmentService.failure = new DataAccessResourceFailureException("secret database detail");

        mockMvc.perform(get("/api/appointments")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("appointment_database_failure"))
                .andExpect(jsonPath("$.message").value("예약 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/appointments")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_appointment_request"));
    }

    @Test
    void authenticatedParticipantCanReadFollowUp() throws Exception {
        appointmentService.followUpResult = followUp();

        mockMvc.perform(get("/api/appointments/{appointmentId}/follow-up", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointmentRequestId").value(APPOINTMENT_ID.toString()))
                .andExpect(jsonPath("$.reviewRatingCode").value("good"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void followUpPatchPassesVersionAndPartialFields() throws Exception {
        appointmentService.followUpResult = followUp();

        mockMvc.perform(patch("/api/appointments/{appointmentId}/follow-up", APPOINTMENT_ID)
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("""
                                {
                                  "version": 1,
                                  "settlementFollowUpStatus": "NEEDS_HELP",
                                  "settlementFollowUpNote": "결제 내역 확인 요청"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        assertThat(appointmentService.lastAppointmentId).isEqualTo(APPOINTMENT_ID);
        assertThat(appointmentService.lastFollowUpCommand.version()).isEqualTo(1);
        assertThat(appointmentService.lastFollowUpCommand.settlementStatus())
                .isEqualTo("NEEDS_HELP");
    }

    private String validCreateJson(UUID clientRequestId) {
        return """
                {
                  "clientRequestId": "%s",
                  "linkedParticipantName": "보호자 사용자",
                  "linkedParticipantPhone": "010-9876-5432",
                  "linkedParticipantEmail": "guardian@example.com",
                  "patientConditionSummary": "휠체어 이동 지원 필요",
                  "medicationSummary": "아침 약 복용",
                  "hospitalName": "서울대학교병원",
                  "departmentName": "내과",
                  "hospitalLatitude": 37.5796,
                  "hospitalLongitude": 126.9990,
                  "appointmentAt": "2026-12-20 10:30",
                  "meetingPlace": "본관 1층",
                  "specialNotes": "진료 20분 전 도착",
                  "mobilitySupportCode": "WHEELCHAIR",
                  "tripTypeCode": "ROUND_TRIP",
                  "managerGenderPreferenceCode": "ANY",
                  "paymentMethodCode": "CARD",
                  "couponCode": "FAMILY"
                }
                """.formatted(clientRequestId);
    }

    private String validUpdateJson(long version) {
        return validCreateJson(UUID.randomUUID())
                .replaceFirst("\\{", "{\n  \"version\": " + version + ",")
                .replaceFirst("\\s*\"clientRequestId\"[^,]+,", "");
    }

    private AppointmentService.AppointmentView appointment() {
        return new AppointmentService.AppointmentView(
                APPOINTMENT_ID,
                "",
                "BD-ABC123",
                USER_ID,
                null,
                MANAGER_ID,
                "매니저 사용자",
                "010-5555-7777",
                "manager@example.com",
                "환자 사용자",
                "010-1234-5678",
                "patient@example.com",
                "보호자 사용자",
                "010-9876-5432",
                "guardian@example.com",
                "서울대학교병원",
                "내과",
                37.5796,
                126.9990,
                "2026-12-20 10:30",
                "본관 1층",
                "",
                "",
                "",
                "INDEPENDENT",
                "ONE_WAY",
                "ANY",
                "REQUESTED",
                69_000,
                0,
                0,
                69_000,
                "CARD",
                "NONE",
                "PENDING",
                "",
                "",
                "",
                2);
    }

    private AppointmentService.AppointmentFollowUpView followUp() {
        return new AppointmentService.AppointmentFollowUpView(
                APPOINTMENT_ID,
                "good",
                "2026-07-18T00:00:00Z",
                "NEEDS_HELP",
                "결제 내역 확인 요청",
                "2026-07-18T00:01:00Z",
                "",
                "",
                2);
    }

    private AppointmentPaymentService.BankTransferPaymentView payment() {
        return new AppointmentPaymentService.BankTransferPaymentView(
                APPOINTMENT_ID,
                "BANK_TRANSFER",
                "AWAITING_DEPOSIT",
                96_000,
                "",
                "",
                null,
                "",
                "",
                "",
                0,
                false);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AppointmentApiTestConfiguration {

        @Bean
        @Primary
        FirebaseTokenVerifier appointmentTestFirebaseTokenVerifier() {
            return idToken -> new FirebaseTokenVerifier.VerifiedToken("firebase-user-1");
        }

        @Bean
        @Primary
        AppCheckTokenVerifier appointmentTestAppCheckTokenVerifier() {
            return token -> new AppCheckTokenVerifier.VerifiedToken("test-app");
        }

        @Bean
        AppUserRepository appointmentTestAppUserRepository() {
            return firebaseUid -> Optional.of(new AppUserRepository.AppUser(
                    USER_ID,
                    firebaseUid,
                    AppUserRole.PATIENT));
        }

        @Bean
        MutableAppointmentService appointmentService() {
            return new MutableAppointmentService();
        }

        @Bean
        MutableAppointmentPaymentService appointmentPaymentService() {
            return new MutableAppointmentPaymentService();
        }
    }

    static final class MutableAppointmentPaymentService implements AppointmentPaymentService {
        private BankTransferPaymentView result;
        private AppUserRepository.AppUser lastUser;
        private UUID lastAppointmentId;
        private SetDepositorCommand lastCommand;

        @Override
        public BankTransferPaymentView getPayment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId) {
            lastUser = appUser;
            lastAppointmentId = appointmentId;
            return result;
        }

        @Override
        public BankTransferPaymentView setDepositor(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                SetDepositorCommand command) {
            lastUser = appUser;
            lastAppointmentId = appointmentId;
            lastCommand = command;
            return result;
        }

        void reset() {
            result = null;
            lastUser = null;
            lastAppointmentId = null;
            lastCommand = null;
        }
    }

    static final class MutableAppointmentService implements AppointmentService {
        private AppointmentView result;
        private AppointmentService createDelegate;
        private RuntimeException failure;
        private AppUserRepository.AppUser lastUser;
        private UUID lastAppointmentId;
        private CreateAppointmentCommand lastCreateCommand;
        private UpdateAppointmentCommand lastUpdateCommand;
        private AppointmentFollowUpView followUpResult;
        private UpdateAppointmentFollowUpCommand lastFollowUpCommand;

        @Override
        public List<AppointmentView> getMyAppointments(AppUserRepository.AppUser appUser) {
            recordCall(appUser, null);
            return List.of(result);
        }

        @Override
        public AppointmentView getAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId) {
            recordCall(appUser, appointmentId);
            return result;
        }

        @Override
        public AppointmentView createAppointment(
                AppUserRepository.AppUser appUser,
                CreateAppointmentCommand command) {
            recordCall(appUser, null);
            lastCreateCommand = command;
            return createDelegate == null ? result : createDelegate.createAppointment(appUser, command);
        }

        @Override
        public AppointmentView updateAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                UpdateAppointmentCommand command) {
            recordCall(appUser, appointmentId);
            lastUpdateCommand = command;
            return result;
        }

        @Override
        public AppointmentView cancelAppointment(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                long version) {
            recordCall(appUser, appointmentId);
            return result;
        }

        @Override
        public AppointmentFollowUpView getAppointmentFollowUp(
                AppUserRepository.AppUser appUser,
                UUID appointmentId) {
            recordCall(appUser, appointmentId);
            return followUpResult;
        }

        @Override
        public AppointmentFollowUpView updateAppointmentFollowUp(
                AppUserRepository.AppUser appUser,
                UUID appointmentId,
                UpdateAppointmentFollowUpCommand command) {
            recordCall(appUser, appointmentId);
            lastFollowUpCommand = command;
            return followUpResult;
        }

        void reset() {
            result = null;
            failure = null;
            createDelegate = null;
            lastUser = null;
            lastAppointmentId = null;
            lastCreateCommand = null;
            lastUpdateCommand = null;
            followUpResult = null;
            lastFollowUpCommand = null;
        }

        private void recordCall(AppUserRepository.AppUser appUser, UUID appointmentId) {
            lastUser = appUser;
            lastAppointmentId = appointmentId;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
