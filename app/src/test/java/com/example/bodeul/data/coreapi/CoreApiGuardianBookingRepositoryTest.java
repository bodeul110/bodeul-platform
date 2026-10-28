package com.example.bodeul.data.coreapi;

import static com.example.bodeul.GuardianBookingTestData.*;
import static org.junit.Assert.*;

import com.example.bodeul.data.GuardianBookingRepository.Review;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CoreApiGuardianBookingRepositoryTest {
    @Test
    public void patientLoad_readsApprovalThenPreviewsCanonicalBody() throws Exception {
        List<String> calls = new ArrayList<>();
        Review result = CoreApiGuardianBookingRepository.loadRequest(request(), (method, path, body) -> {
            calls.add(method + " " + path);
            if (path.equals("/api/auth/me")) return me(true);
            if (path.endsWith("/preview")) {
                assertEquals(GUARDIAN.toString(), body.getString("guardianUserId"));
                assertEquals(REQUEST.toString(), body.getJSONObject("appointment").getString("clientRequestId"));
                assertFalse(body.has("patientUserId"));
                JSONObject preview = preview();
                preview.getJSONObject("draft").put("meetingPlace", "정규화된 만남 장소");
                return preview;
            }
            return inactiveJson();
        });
        assertTrue(result.patient);
        assertEquals("정규화된 만남 장소", result.request.getDraft().getMeetingPlace());
        assertEquals(0, result.approval.getVersion());
        assertEquals(List.of("GET /api/auth/me",
                "GET /api/appointments/guardian-booking/patients/" + PATIENT + "/guardians/" + GUARDIAN
                        + "/requests/" + REQUEST, "POST /api/appointments/guardian-booking/preview"), calls);
    }

    @Test
    public void invalidPreview_keepsReadApprovalForRevocationOnly() throws Exception {
        for (int status : new int[]{400, 409}) {
            Review result = CoreApiGuardianBookingRepository.loadRequest(request(), (method, path, body) -> {
                if (path.equals("/api/auth/me")) return me(true);
                if (path.endsWith("/preview")) throw new CoreApiAuthenticatedClient.ApiException(status, "지난 예약");
                return activeJson();
            });
            assertTrue(result.patient);
            assertFalse(result.previewVerified);
            assertTrue(result.approval.canRevoke());
        }
    }

    @Test
    public void unauthorizedOrUnavailablePreview_neverBecomesWritableState() {
        for (int status : new int[]{401, 403, 404, 503}) {
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                    CoreApiGuardianBookingRepository.loadRequest(request(), (method, path, body) -> {
                        if (path.equals("/api/auth/me")) return me(true);
                        if (path.endsWith("/preview")) throw new CoreApiAuthenticatedClient.ApiException(status, "합성 오류");
                        return activeJson();
                    }));
        }
    }

    @Test
    public void guardianLoad_doesNotCallPatientPreviewOrProfileSearch() throws Exception {
        List<String> calls = new ArrayList<>();
        Review result = CoreApiGuardianBookingRepository.loadRequest(request(), (method, path, body) -> {
            calls.add(method + " " + path);
            return path.equals("/api/auth/me") ? me(false) : activeJson();
        });
        assertFalse(result.patient);
        assertEquals(2, calls.size());
        assertTrue(calls.get(1).contains("/patients/" + PATIENT));
        assertTrue(result.approval.canCreateAt(NOW));
    }

    @Test
    public void unrelatedAccountAndWrongRole_stopBeforeReadingApproval() throws Exception {
        for (JSONObject identity : List.of(me(true).put("userId", GRANT.toString()),
                me(true).put("role", "GUARDIAN"), me(false).put("role", "ADMIN"))) {
            List<String> calls = new ArrayList<>();
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                    CoreApiGuardianBookingRepository.loadRequest(request(), (method, path, body) -> {
                        calls.add(path);
                        return identity;
                    }));
            assertEquals(List.of("/api/auth/me"), calls);
        }
    }

    @Test
    public void missingOrFirebaseIdentity_isNotAcceptedAsPostgresId() {
        for (String invalid : List.of("firebase-uid", "1-1-1-1-1", PATIENT + "/requests/other")) {
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.loadRequest(request(),
                    (method, path, body) -> me(true).put("userId", invalid)));
        }
    }

    @Test
    public void grant_usesReviewedPayloadVersionAndExplicitBooleanOnly() throws Exception {
        List<String> calls = new ArrayList<>();
        CoreApiGuardianBookingRepository.grantRequest(request(), 0, true, (method, path, body) -> {
            calls.add(path);
            if (path.equals("/api/auth/me")) return me(true);
            assertEquals("POST", method);
            assertEquals(CoreApiGuardianBookingRepository.ROOT + "/approvals", path);
            assertEquals(4, body.length());
            assertTrue(body.get("expectedVersion") instanceof Number);
            assertEquals(0L, body.getLong("expectedVersion"));
            assertSame(Boolean.TRUE, body.get("adultPatientConfirmed"));
            JSONObject appointment = body.getJSONObject("appointment");
            assertEquals(40_000, appointment.getInt("expectedFinalPrice"));
            assertEquals("mvp-fixed-40000-v1", appointment.getString("pricePolicyVersion"));
            assertEquals(REQUEST.toString(), appointment.getString("clientRequestId"));
            assertEquals("첫째 줄\n둘째 줄", appointment.getString("patientConditionSummary"));
            assertFalse(body.has("scopes"));
            assertFalse(body.has("fingerprint"));
            return activeJson();
        });
        assertEquals(2, calls.size());
    }

    @Test
    public void grant_rejectsMissingConfirmationAndInvalidVersionBeforeNetwork() {
        CoreApiGuardianBookingRepository.Sender unexpected = (method, path, body) -> {
            throw new AssertionError("잘못된 승인 입력은 전송하지 않아야 합니다.");
        };
        assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.grantRequest(
                request(), 0, false, unexpected));
        for (long version : new long[]{-1, Long.MAX_VALUE}) {
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.grantRequest(
                    request(), version, true, unexpected));
        }
    }

    @Test
    public void allMutations_recheckActorAndRejectAccountSwitchBeforePost() {
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                CoreApiGuardianBookingRepository.grantRequest(request(), 0, true,
                        (method, path, body) -> me(false)));
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                CoreApiGuardianBookingRepository.revokeRequest(request(), 1,
                        (method, path, body) -> me(false)));
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                CoreApiGuardianBookingRepository.createRequest(request(), active(1),
                        (method, path, body) -> me(true)));
    }

    @Test
    public void revoke_postsExpectedVersionWithoutCancellingAppointment() throws Exception {
        List<String> calls = new ArrayList<>();
        GuardianBookingApproval result = CoreApiGuardianBookingRepository.revokeRequest(request(), 1,
                (method, path, body) -> {
                    calls.add(path);
                    if (path.equals("/api/auth/me")) return me(true);
                    assertEquals("POST", method);
                    assertEquals(CoreApiGuardianBookingRepository.ROOT + "/guardians/" + GUARDIAN
                            + "/requests/" + REQUEST + "/revoke", path);
                    assertEquals(1, body.length());
                    assertEquals(1, body.getLong("expectedVersion"));
                    return activeJson().put("version", 2).put("active", false)
                            .put("revokedAt", NOW.toString());
                });
        assertEquals(2, calls.size());
        assertFalse(result.canCreateAt(NOW));
        assertFalse(result.canRevoke());
    }

    @Test
    public void create_retriesOnlyWhenCallerRequestsAndPreservesExactBody() throws Exception {
        List<String> bodies = new ArrayList<>();
        CoreApiGuardianBookingRepository.Sender sender = (method, path, body) -> {
            if (path.equals("/api/auth/me")) return me(false);
            assertEquals(CoreApiGuardianBookingRepository.ROOT + "/create", path);
            assertEquals("POST", method);
            bodies.add(body.toString());
            if (bodies.size() == 1) throw new IOException("접수 응답 유실");
            assertEquals(PATIENT.toString(), body.getString("patientUserId"));
            assertEquals(GRANT.toString(), body.getString("grantId"));
            assertEquals(7, body.getLong("approvalVersion"));
            return new JSONObject().put("appointmentId", REQUEST.toString()).put("publicCode", "TEST-1");
        };
        assertThrows(IOException.class, () -> CoreApiGuardianBookingRepository.createRequest(request(), active(7), sender));
        assertEquals(1, bodies.size());
        GuardianBookingReceipt receipt = CoreApiGuardianBookingRepository.createRequest(request(), active(7), sender);
        assertEquals(bodies.get(0), bodies.get(1));
        assertEquals("TEST-1", receipt.getPublicCode());
        assertEquals(REQUEST, receipt.getAppointmentId());
    }

    @Test
    public void create_httpErrorsNeverFallBackToLegacyOrReadDetail() {
        for (int status : new int[]{401, 403, 404, 405, 409, 503}) {
            List<String> paths = new ArrayList<>();
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                    CoreApiGuardianBookingRepository.createRequest(request(), active(1), (method, path, body) -> {
                        paths.add(path);
                        if (path.equals("/api/auth/me")) return me(false);
                        throw new CoreApiAuthenticatedClient.ApiException(status, "합성 오류");
                    }));
            assertEquals(List.of("/api/auth/me", CoreApiGuardianBookingRepository.ROOT + "/create"), paths);
        }
    }

    @Test
    public void load_errorIsNotReplacedWithVersionZero() {
        assertThrows(IOException.class, () -> CoreApiGuardianBookingRepository.loadRequest(request(),
                (method, path, body) -> {
                    if (path.equals("/api/auth/me")) return me(false);
                    throw new IOException();
                }));
    }

    @Test
    public void parsePreview_preservesAllNormalizedDraftFields() throws Exception {
        GuardianBookingRequest result = CoreApiGuardianBookingRepository.parsePreview(preview(), request());
        assertEquals(CoreApiGuardianBookingRepository.appointmentBody(request()).toString(),
                CoreApiGuardianBookingRepository.appointmentBody(result).toString());
        assertEquals(PATIENT, result.getPatientId());
        assertEquals(GUARDIAN, result.getGuardianId());
    }

    @Test
    public void preview_rejectsOtherRequestPolicyPriceAndUnknownEnums() throws Exception {
        for (JSONObject invalid : List.of(preview().put("clientRequestId", PATIENT.toString()),
                preview().put("pricePolicyVersion", "future"), preview().put("expectedFinalPrice", "40000"),
                preview().put("expectedFinalPrice", 40_000.0), preview().put("expectedFinalPrice", 69_000))) {
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.parsePreview(invalid, request()));
        }
        for (String field : List.of("mobilitySupportCode", "tripTypeCode", "managerGenderPreferenceCode",
                "paymentMethodCode", "couponCode")) {
            JSONObject invalid = preview();
            invalid.getJSONObject("draft").put(field, "FUTURE");
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.parsePreview(invalid, request()));
        }
    }

    @Test
    public void approval_rejectsMissingFieldsAndCoercedTypes() throws Exception {
        for (String key : List.of("grantId", "version", "policyVersion", "grantedAt", "expiresAt", "revokedAt", "active")) {
            JSONObject invalid = inactiveJson();
            invalid.remove(key);
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.parseApproval(invalid));
        }
        for (JSONObject invalid : List.of(activeJson().put("version", "1"), activeJson().put("version", 1.5),
                activeJson().put("version", -1), activeJson().put("version", Long.MAX_VALUE),
                activeJson().put("active", "true"), activeJson().put("policyVersion", "future"),
                activeJson().put("grantId", "1-1-1-1-1"), activeJson().put("expiresAt", "bad-date"),
                activeJson().put("revokedAt", NOW.toString()), activeJson().put("version", 0),
                inactiveJson().put("active", true), inactiveJson().put("version", 1))) {
            assertThrows(JSONException.class, () -> CoreApiGuardianBookingRepository.parseApproval(invalid));
        }
    }

    private static JSONObject me(boolean patient) throws JSONException {
        return new JSONObject().put("userId", (patient ? PATIENT : GUARDIAN).toString())
                .put("role", patient ? "PATIENT" : "GUARDIAN");
    }

    private static JSONObject inactiveJson() throws JSONException {
        return new JSONObject().put("grantId", JSONObject.NULL).put("version", 0)
                .put("policyVersion", GuardianBookingApproval.POLICY_VERSION)
                .put("grantedAt", JSONObject.NULL).put("expiresAt", JSONObject.NULL)
                .put("revokedAt", JSONObject.NULL).put("active", false);
    }

    private static JSONObject activeJson() throws JSONException {
        return inactiveJson().put("grantId", GRANT.toString()).put("version", 1)
                .put("grantedAt", NOW.minusSeconds(1).toString())
                .put("expiresAt", NOW.plusSeconds(3600).toString()).put("active", true);
    }

    private static JSONObject preview() throws JSONException {
        JSONObject draft = new JSONObject()
                .put("linkedParticipantName", "합성 환자").put("linkedParticipantPhone", "01000000000")
                .put("linkedParticipantEmail", "patient@example.com").put("patientConditionSummary", "첫째 줄\n둘째 줄")
                .put("medicationSummary", "합성 복약").put("hospitalName", "합성 병원").put("departmentName", "내과")
                .put("hospitalLatitude", 37.5).put("hospitalLongitude", 127)
                .put("appointmentAt", "2026-10-29 10:00").put("meetingPlace", "1층").put("specialNotes", "합성 요청")
                .put("mobilitySupportCode", "INDEPENDENT").put("tripTypeCode", "ONE_WAY")
                .put("managerGenderPreferenceCode", "ANY").put("paymentMethodCode", "BANK_TRANSFER").put("couponCode", "NONE");
        return new JSONObject().put("clientRequestId", REQUEST.toString()).put("draft", draft)
                .put("pricePolicyVersion", "mvp-fixed-40000-v1").put("expectedFinalPrice", 40_000);
    }
}
