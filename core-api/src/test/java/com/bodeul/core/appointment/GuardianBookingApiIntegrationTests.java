package com.bodeul.core.appointment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.bodeul.core.auth.AppCheckTokenVerifier;
import com.bodeul.core.auth.AppUserRepository;
import com.bodeul.core.auth.AppUserRepository.AppUser;
import com.bodeul.core.auth.AppUserRole;
import com.bodeul.core.auth.FirebaseTokenVerifier;
import com.bodeul.core.consent.GuardianBookingApprovalService;
import com.bodeul.core.consent.GuardianBookingApprovalService.ApprovalView;
import com.bodeul.core.consent.GuardianBookingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "guardian-booking-test"})
@Import(GuardianBookingApiIntegrationTests.Configuration.class)
class GuardianBookingApiIntegrationTests {
    private static final String BASE = "/api/appointments/guardian-booking";
    private static final AppUser PATIENT = new AppUser(UUID.randomUUID(), "patient", AppUserRole.PATIENT);
    private static final AppUser GUARDIAN = new AppUser(UUID.randomUUID(), "guardian", AppUserRole.GUARDIAN);
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID GRANT = UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private GuardianBookingService service;
    @Autowired private GuardianBookingApprovalService approvals;
    @Autowired private Environment environment;

    @BeforeEach
    void resetMocks() { reset(service, approvals); }

    @Test
    void featureFlagIsOffByDefault() {
        assertThat(environment.getProperty("bodeul.guardian-booking.enabled", Boolean.class)).isFalse();
    }

    @Test
    void authenticationIsRequiredForEveryEndpoint() throws Exception {
        for (String path : new String[]{"/preview", "/approvals", "/create",
                "/guardians/" + GUARDIAN.id() + "/requests/" + REQUEST + "/revoke"}) {
            mvc.perform(post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(body().toString()))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get(metadataPath())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, approvals);
    }

    @Test
    void grantUsesAuthenticatedPrincipalAndIgnoresClientAuthorityFields() throws Exception {
        when(service.grant(eq(PATIENT), eq(GUARDIAN.id()), any(), eq(0L), eq(true))).thenReturn(view());
        ObjectNode body = body().put("actorUserId", GUARDIAN.id().toString()).put("role", "SUPER_ADMIN")
                .put("fingerprint", "forged").put("policyVersion", "forged").put("expiresAt", "2999-01-01T00:00:00Z");
        mvc.perform(post(BASE + "/approvals").header("Authorization", "Bearer patient")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.grantId").value(GRANT.toString()))
                .andExpect(jsonPath("$.requestFingerprint").doesNotExist());
        verify(service).grant(eq(PATIENT), eq(GUARDIAN.id()), argThat(command -> command.clientRequestId().equals(REQUEST)
                && command.expectedFinalPrice() == 40000), eq(0L), eq(true));
    }

    @Test
    void creationResponseHasOnlyReceiptAndNoPatientOrAppointmentDetails() throws Exception {
        UUID appointment = UUID.randomUUID();
        when(service.create(eq(GUARDIAN), eq(PATIENT.id()), eq(GRANT), eq(1L), any()))
                .thenReturn(new GuardianBookingService.Receipt(appointment, "TEST0001"));
        String json = mvc.perform(post(BASE + "/create").header("Authorization", "Bearer guardian")
                        .contentType(MediaType.APPLICATION_JSON).content(body().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.appointmentId").value(appointment.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(json).size()).isEqualTo(2);
        assertThat(json).doesNotContain("hospital", "patient", "phone", "status", "fingerprint");
    }

    @Test
    void metadataRevokeAndPreviewAreNoStoreAndUsePrincipal() throws Exception {
        when(approvals.get(PATIENT, PATIENT.id(), GUARDIAN.id(), REQUEST)).thenReturn(view());
        when(approvals.revoke(PATIENT, GUARDIAN.id(), REQUEST, 1)).thenReturn(view());
        when(service.preview(eq(PATIENT), eq(GUARDIAN.id()), any())).thenReturn(
                GuardianBookingTestFixtures.command(REQUEST, Instant.parse("2026-10-01T00:00:00Z")));
        mvc.perform(get(metadataPath()).header("Authorization", "Bearer patient"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(BASE + "/guardians/" + GUARDIAN.id() + "/requests/" + REQUEST + "/revoke")
                        .header("Authorization", "Bearer patient").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(BASE + "/preview").header("Authorization", "Bearer patient")
                        .contentType(MediaType.APPLICATION_JSON).content(body().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(approvals).revoke(PATIENT, GUARDIAN.id(), REQUEST, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"1\"", "1.5", "true", "9223372036854775808"})
    void versionMustBeIntegralAndWithinLongRange(String version) throws Exception {
        ObjectNode body = body();
        body.set("expectedVersion", mapper.readTree(version));
        mvc.perform(post(BASE + "/approvals").header("Authorization", "Bearer patient")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"true\"", "1", "{}"})
    void adultConfirmationCannotBeCoerced(String confirmation) throws Exception {
        ObjectNode body = body();
        body.set("adultPatientConfirmed", mapper.readTree(confirmation));
        mvc.perform(post(BASE + "/approvals").header("Authorization", "Bearer patient")
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void malformedUuidPriceAndNullBodyDoNotCallService() throws Exception {
        ObjectNode invalidId = body().put("guardianUserId", "not-a-uuid");
        ObjectNode invalidPrice = body();
        ((ObjectNode) invalidPrice.get("appointment")).put("expectedFinalPrice", 40000.5);
        for (String json : new String[]{invalidId.toString(), invalidPrice.toString(), "null"}) {
            mvc.perform(post(BASE + "/approvals").header("Authorization", "Bearer patient")
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void disabledConflictAndDatabaseFailureHaveSanitizedErrors() throws Exception {
        when(service.grant(any(), any(), any(), anyLong(), anyBoolean()))
                .thenThrow(GuardianBookingException.disabled())
                .thenThrow(GuardianBookingException.conflict())
                .thenThrow(new DataAccessResourceFailureException("secret SQL endpoint"));
        for (int code : new int[]{403, 409, 503}) {
            String response = mvc.perform(post(BASE + "/approvals").header("Authorization", "Bearer patient")
                            .contentType(MediaType.APPLICATION_JSON).content(body().toString()))
                    .andExpect(status().is(code)).andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("secret", "SQL", "endpoint");
        }
    }

    private ObjectNode body() {
        ObjectNode appointment = mapper.valueToTree(GuardianBookingTestFixtures.command(
                REQUEST, Instant.parse("2026-10-01T00:00:00Z")).draft());
        appointment.put("clientRequestId", REQUEST.toString()).put("pricePolicyVersion", AppointmentPricePolicy.VERSION)
                .put("expectedFinalPrice", 40000);
        ObjectNode body = mapper.createObjectNode().put("guardianUserId", GUARDIAN.id().toString())
                .put("patientUserId", PATIENT.id().toString()).put("grantId", GRANT.toString())
                .put("expectedVersion", 0).put("approvalVersion", 1).put("adultPatientConfirmed", true);
        body.set("appointment", appointment);
        return body;
    }

    private String metadataPath() {
        return BASE + "/patients/" + PATIENT.id() + "/guardians/" + GUARDIAN.id() + "/requests/" + REQUEST;
    }
    private ApprovalView view() { return new ApprovalView(GRANT, 1, "adult-patient-guardian-booking-v1", null, null, null, true); }

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean @Primary FirebaseTokenVerifier bookingTokenVerifier() { return token -> new FirebaseTokenVerifier.VerifiedToken(token); }
        @Bean @Primary AppCheckTokenVerifier bookingAppCheckVerifier() { return token -> new AppCheckTokenVerifier.VerifiedToken("test-app"); }
        @Bean AppUserRepository bookingUsers() {
            return uid -> uid.equals("patient") ? Optional.of(PATIENT) : uid.equals("guardian") ? Optional.of(GUARDIAN) : Optional.empty();
        }
        @Bean GuardianBookingService bookingService() { return mock(GuardianBookingService.class); }
        @Bean GuardianBookingApprovalService bookingApprovals() { return mock(GuardianBookingApprovalService.class); }
    }
}
