package com.example.bodeul.data.coreapi;

import static com.example.bodeul.GuardianBookingTestData.*;
import static com.example.bodeul.data.coreapi.CoreApiGuardianBookingDraftRepository.*;
import static org.junit.Assert.*;

import com.example.bodeul.data.GuardianBookingDraftRepository.ApprovalStatus;
import com.example.bodeul.data.GuardianBookingRepository.CreateFailure;
import com.example.bodeul.data.GuardianBookingRepository.CreateResult;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class CoreApiGuardianBookingDraftRepositoryTest {
    private static final String ROOT = CoreApiGuardianBookingRepository.ROOT;
    private static final String PATH = ROOT + "/drafts/guardians/" + GUARDIAN + "/requests/" + REQUEST;

    @Test
    public void reference_preservesIdentityWithoutOriginalBody() {
        GuardianBookingDraftReference reference = reference();
        assertEquals(PATIENT, reference.getPatientId());
        assertEquals(GUARDIAN, reference.getGuardianId());
        assertEquals(REQUEST, reference.getRequestId());
        assertEquals(new GuardianBookingDraftReference(PATIENT, GUARDIAN, REQUEST), reference);
        assertEquals(new GuardianBookingDraftReference(PATIENT, GUARDIAN, REQUEST).hashCode(), reference.hashCode());
        assertNotEquals(new GuardianBookingDraftReference(PATIENT, GUARDIAN, GRANT), reference);
        assertThrows(IllegalArgumentException.class, () -> new GuardianBookingDraftReference(PATIENT, PATIENT, REQUEST));
        assertThrows(NullPointerException.class, () -> new GuardianBookingDraftReference(PATIENT, GUARDIAN, null));
    }

    @Test
    public void submit_sendsExistingRequestAndExplicitPatientWithoutAuthorityFields() throws Exception {
        List<String> paths = new ArrayList<>();
        Summary result = submitRequest(request(), (method, path, body) -> {
            paths.add(path);
            if (path.equals("/api/auth/me")) return me(false);
            assertEquals("POST", method);
            assertEquals(ROOT + "/drafts", path);
            assertEquals(2, body.length());
            assertEquals(PATIENT.toString(), body.getString("patientUserId"));
            assertEquals(CoreApiGuardianBookingRepository.appointmentBody(request()).toString(),
                    body.getJSONObject("appointment").toString());
            return summary();
        });
        assertEquals(List.of("/api/auth/me", ROOT + "/drafts"), paths);
        assertEquals(reference(), result.getReference());
        assertEquals(NOW, result.getCreatedAt());
        assertEquals(NOW.plusSeconds(3600), result.getExpiresAt());
    }

    @Test
    public void submit_responseLossDoesNotAutomaticallyResendOrReplaceId() throws Exception {
        List<String> bodies = new ArrayList<>();
        CoreApiGuardianBookingRepository.Sender sender = (method, path, body) -> {
            if (path.equals("/api/auth/me")) return me(false);
            assertEquals(ROOT + "/drafts", path);
            bodies.add(body.toString());
            if (bodies.size() == 1) throw new IOException("저장 후 응답 유실");
            return summary();
        };
        assertThrows(IOException.class, () -> submitRequest(request(), sender));
        assertEquals(1, bodies.size());
        assertEquals(reference(), submitRequest(request(), sender).getReference());
        assertEquals(bodies.get(0), bodies.get(1));
    }

    @Test
    public void submit_rejectsDifferentResponseReference() throws Exception {
        for (String field : List.of("patientUserId", "guardianUserId", "clientRequestId")) {
            JSONObject response = summary().put(field, GRANT.toString());
            assertThrows(JSONException.class, () -> submitRequest(request(), (method, path, body) ->
                    path.equals("/api/auth/me") ? me(false) : response));
        }
    }

    @Test
    public void submit_rejectsOtherAccountOrPatientBeforePost() throws Exception {
        for (JSONObject identity : List.of(me(true), me(false).put("userId", GRANT.toString()),
                me(false).put("role", "MANAGER"), me(false).put("role", "ADMIN"))) {
            List<String> paths = new ArrayList<>();
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> submitRequest(request(),
                    (method, path, body) -> { paths.add(path); return identity; }));
            assertEquals(List.of("/api/auth/me"), paths);
        }
    }

    @Test
    public void summary_rejectsMissingFieldsNonCanonicalIdsAndInvalidTime() throws Exception {
        for (String key : List.of("patientUserId", "guardianUserId", "clientRequestId", "createdAt", "expiresAt")) {
            JSONObject missing = summary();
            missing.remove(key);
            assertThrows(JSONException.class, () -> parseSummary(missing));
            assertThrows(JSONException.class, () -> parseSummary(summary().put(key, JSONObject.NULL)));
        }
        for (String id : List.of("firebase-uid", "1-1-1-1-1", REQUEST + "/other", "")) {
            assertThrows(JSONException.class, () -> parseSummary(summary().put("clientRequestId", id)));
        }
        for (Object time : List.of(NOW.toString(), NOW.minusSeconds(1).toString(), "tomorrow", 1)) {
            assertThrows(JSONException.class, () -> parseSummary(summary().put("expiresAt", time)));
        }
        assertThrows(JSONException.class, () -> parseSummary(summary().put("guardianUserId", PATIENT.toString())));
    }

    @Test
    public void list_readsOnlyCurrentActorMetadataAndPreservesServerOrder() throws Exception {
        for (boolean patient : new boolean[]{true, false}) {
            List<String> paths = new ArrayList<>();
            List<Summary> result = listRequest((method, path, body) -> {
                assertEquals("GET", method);
                assertNull(body);
                paths.add(path);
                return me(patient);
            }, path -> {
                paths.add(path);
                return new JSONArray().put(summary().put("clientRequestId", GRANT.toString())).put(summary());
            });
            assertEquals(List.of("/api/auth/me", ROOT + "/drafts"), paths);
            assertEquals(GRANT, result.get(0).getReference().getRequestId());
            assertEquals(REQUEST, result.get(1).getReference().getRequestId());
            assertThrows(UnsupportedOperationException.class, () -> result.clear());
        }
    }

    @Test
    public void list_rejectsUnrelatedOrMixedRoleRowsInsteadOfFilteringThem() throws Exception {
        for (boolean patient : new boolean[]{true, false}) {
            JSONArray rows = new JSONArray().put(summary()).put(summary()
                    .put(patient ? "patientUserId" : "guardianUserId", GRANT.toString()));
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () ->
                    listRequest((method, path, body) -> me(patient), path -> rows));
        }
    }

    @Test
    public void list_rejectsWrongRoleBeforeReadingRows() {
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> listRequest(
                (method, path, body) -> me(true).put("role", "ADMIN"), path -> {
                    throw new AssertionError("다른 역할은 목록 요청 전에 거부해야 합니다.");
                }));
    }

    @Test
    public void list_rejectsDuplicateMalformedAndOversizedResults() throws Exception {
        JSONArray oversized = new JSONArray();
        for (int i = 0; i < 51; i++) oversized.put(summary());
        for (JSONArray rows : List.of(new JSONArray().put(summary()).put(summary()),
                new JSONArray().put("invalid"), new JSONArray().put(JSONObject.NULL),
                new JSONArray().put(new JSONObject()), oversized)) {
            assertThrows(JSONException.class, () -> listRequest((method, path, body) -> me(true), path -> rows));
        }
    }

    @Test
    public void list_returnsEmptyOnlyForSuccessfulEmptyArray() throws Exception {
        assertTrue(listRequest((method, path, body) -> me(true), path -> new JSONArray()).isEmpty());
        assertThrows(IOException.class, () -> listRequest((method, path, body) -> me(true), path -> {
            throw new IOException("목록 응답 유실");
        }));
        for (int status : new int[]{401, 403, 404, 503}) {
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> listRequest(
                    (method, path, body) -> me(true), path -> { throw apiError(status); }));
        }
    }

    @Test
    public void detail_readsStoredBodyAndCurrentMaskedParticipantsForEitherActor() throws Exception {
        for (boolean patient : new boolean[]{true, false}) {
            List<String> paths = new ArrayList<>();
            GuardianBookingStoredDraft result = loadRequest(reference(), (method, path, body) -> {
                paths.add(path);
                assertEquals("GET", method);
                assertNull(body);
                return path.equals("/api/auth/me") ? me(patient) : detail();
            });
            assertEquals(List.of("/api/auth/me", PATH), paths);
            assertEquals(reference(), result.getSummary().getReference());
            assertEquals(CoreApiGuardianBookingRepository.appointmentBody(request()).toString(),
                    CoreApiGuardianBookingRepository.appointmentBody(result.getRequest()).toString());
            assertEquals("합성 환자 현재 이름", result.getPatient().getName());
            assertEquals("***-****-0000", result.getPatient().getMaskedPhone());
            assertEquals("합성 보호자", result.getGuardian().getName());
            assertEquals("****", result.getGuardian().getMaskedPhone());
        }
    }

    @Test
    public void detail_rejectsMismatchedSummaryOrCommandWithoutFallback() throws Exception {
        List<JSONObject> values = new ArrayList<>();
        for (String key : List.of("patientUserId", "guardianUserId", "clientRequestId")) {
            JSONObject value = detail();
            value.getJSONObject("request").put(key, GRANT.toString());
            values.add(value);
        }
        JSONObject commandMismatch = detail();
        commandMismatch.getJSONObject("appointment").put("clientRequestId", GRANT.toString());
        values.add(commandMismatch);
        for (JSONObject value : values) {
            assertThrows(JSONException.class, () -> loadRequest(reference(),
                    (method, path, body) -> path.equals("/api/auth/me") ? me(true) : value));
        }
    }

    @Test
    public void detail_rejectsPolicyPriceAndUnmaskedOrMissingParticipant() throws Exception {
        List<JSONObject> values = new ArrayList<>();
        JSONObject policy = detail();
        policy.getJSONObject("appointment").put("pricePolicyVersion", "future");
        values.add(policy);
        JSONObject price = detail();
        price.getJSONObject("appointment").put("expectedFinalPrice", "40000");
        values.add(price);
        for (String participant : List.of("patient", "guardian")) {
            for (Object phone : List.of("01012345678", "1234", JSONObject.NULL)) {
                JSONObject value = detail();
                value.getJSONObject(participant).put("maskedPhone", phone);
                values.add(value);
            }
            JSONObject missing = detail();
            missing.remove(participant);
            values.add(missing);
            JSONObject emptyName = detail();
            emptyName.getJSONObject(participant).put("name", " ");
            values.add(emptyName);
        }
        for (JSONObject value : values) {
            assertThrows(JSONException.class, () -> loadRequest(reference(),
                    (method, path, body) -> path.equals("/api/auth/me") ? me(false) : value));
        }
    }

    @Test
    public void detail_expiryAndAccessErrorsNeverCallLegacyPreviewOrReturnCachedBody() {
        for (int status : new int[]{400, 401, 403, 404, 409, 503}) {
            List<String> paths = new ArrayList<>();
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> loadRequest(reference(),
                    (method, path, body) -> {
                        paths.add(path);
                        if (path.equals("/api/auth/me")) return me(true);
                        throw apiError(status);
                    }));
            assertEquals(List.of("/api/auth/me", PATH), paths);
        }
    }

    @Test
    public void approval_canReadExistingRevocationOnlyMetadataWithoutOriginalBody() throws Exception {
        List<String> paths = new ArrayList<>();
        ApprovalStatus status = approvalRequest(reference(), (method, path, body) -> {
            paths.add(path);
            assertEquals("GET", method);
            return path.equals("/api/auth/me") ? me(true) : approval().put("revocationOnly", true);
        });
        assertTrue(status.patient);
        assertTrue(status.revocationOnly);
        assertTrue(status.approval.canRevoke());
        assertEquals(List.of("/api/auth/me", ROOT + "/patients/" + PATIENT + "/guardians/" + GUARDIAN
                + "/requests/" + REQUEST), paths);
    }

    @Test
    public void approval_normalMetadataDoesNotRequireDraftRead() throws Exception {
        for (boolean patient : new boolean[]{true, false}) {
            ApprovalStatus value = approvalRequest(reference(), (method, path, body) ->
                    path.equals("/api/auth/me") ? me(patient) : approval());
            assertEquals(patient, value.patient);
            assertFalse(value.revocationOnly);
        }
    }

    @Test
    public void approval_rejectsMalformedRevocationModeOrUnauthorizedMode() throws Exception {
        for (Object invalid : List.of("true", 1, JSONObject.NULL)) {
            assertThrows(JSONException.class, () -> approvalRequest(reference(), (method, path, body) ->
                    path.equals("/api/auth/me") ? me(true) : approval().put("revocationOnly", invalid)));
        }
        assertThrows(JSONException.class, () -> approvalRequest(reference(), (method, path, body) ->
                path.equals("/api/auth/me") ? me(false) : approval().put("revocationOnly", true)));
        assertThrows(JSONException.class, () -> approvalRequest(reference(), (method, path, body) ->
                path.equals("/api/auth/me") ? me(true) : approval().put("revocationOnly", true)
                        .put("grantId", JSONObject.NULL).put("version", 0).put("grantedAt", JSONObject.NULL)
                        .put("expiresAt", JSONObject.NULL).put("active", false)));
    }

    @Test
    public void approval_errorDoesNotBecomeVersionZeroOrRevocationPermission() {
        for (int code : new int[]{401, 403, 404, 503}) {
            assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> approvalRequest(reference(),
                    (method, path, body) -> {
                        if (path.equals("/api/auth/me")) return me(true);
                        throw apiError(code);
                    }));
        }
    }

    @Test
    public void grant_postsOnlyVersionAndConfirmationToStoredDraft() throws Exception {
        GuardianBookingApproval result = grantRequest(reference(), 0, true, (method, path, body) -> {
            if (path.equals("/api/auth/me")) return me(true);
            assertEquals("POST", method);
            assertEquals(PATH + "/approvals", path);
            assertEquals(2, body.length());
            assertEquals(0L, body.get("expectedVersion"));
            assertSame(Boolean.TRUE, body.get("adultPatientConfirmed"));
            return approval();
        });
        assertEquals(GRANT, result.getGrantId());
    }

    @Test
    public void grant_andRevokeRejectBadVersionAndConfirmationBeforeNetwork() {
        CoreApiGuardianBookingRepository.Sender unexpected = (method, path, body) -> {
            throw new AssertionError("잘못된 입력은 전송하지 않아야 합니다.");
        };
        for (long version : new long[]{-1, Long.MAX_VALUE}) {
            assertThrows(JSONException.class, () -> grantRequest(reference(), version, true, unexpected));
            assertThrows(JSONException.class, () -> revokeRequest(reference(), version, unexpected));
        }
        assertThrows(JSONException.class, () -> grantRequest(reference(), 0, false, unexpected));
    }

    @Test
    public void revoke_doesNotRequireLiveDraftOrCancelExistingAppointment() throws Exception {
        List<String> paths = new ArrayList<>();
        GuardianBookingApproval result = revokeRequest(reference(), 1, (method, path, body) -> {
            paths.add(path);
            if (path.equals("/api/auth/me")) return me(true);
            assertEquals(ROOT + "/guardians/" + GUARDIAN + "/requests/" + REQUEST + "/revoke", path);
            assertEquals("POST", method);
            assertEquals(1, body.length());
            assertEquals(1L, body.get("expectedVersion"));
            return approval().put("version", 2).put("revokedAt", NOW.toString()).put("active", false);
        });
        assertEquals(2, paths.size());
        assertFalse(result.canRevoke());
    }

    @Test
    public void writes_rejectChangedActorBeforePost() {
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> grantRequest(reference(), 0, true,
                (method, path, body) -> { assertEquals("/api/auth/me", path); return me(false); }));
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> revokeRequest(reference(), 1,
                (method, path, body) -> { assertEquals("/api/auth/me", path); return me(false); }));
        assertEquals(CreateFailure.NOT_SENT, createRequest(reference(), active(1),
                (method, path, body) -> { assertEquals("/api/auth/me", path); return me(true); }).failure);
    }

    @Test
    public void create_afterExpiryCanRecoverReceiptWithoutFetchingDraftOrCurrentApproval() throws Exception {
        List<String> paths = new ArrayList<>();
        GuardianBookingApproval expired = new GuardianBookingApproval(GRANT, 7,
                NOW.minusSeconds(7200), NOW.minusSeconds(3600), NOW.minusSeconds(3500), false);
        CreateResult result = createRequest(reference(), expired, (method, path, body) -> {
            paths.add(path);
            if (path.equals("/api/auth/me")) return me(false);
            assertEquals(PATH + "/create", path);
            assertEquals("POST", method);
            assertEquals(2, body.length());
            assertEquals(GRANT.toString(), body.getString("grantId"));
            assertEquals(7L, body.get("approvalVersion"));
            return receipt();
        });
        assertNull(result.failure);
        assertEquals("TEST-1", result.receipt.getPublicCode());
        assertEquals(List.of("/api/auth/me", PATH + "/create"), paths);
    }

    @Test
    public void create_responseLossAndRetryKeepSameRequestApprovalWithoutAutomaticRetry() throws Exception {
        List<String> bodies = new ArrayList<>();
        CoreApiGuardianBookingRepository.Sender sender = (method, path, body) -> {
            if (path.equals("/api/auth/me")) return me(false);
            assertEquals(PATH + "/create", path);
            bodies.add(body.toString());
            if (bodies.size() == 1) throw new IOException("커밋 후 응답 유실");
            return receipt();
        };
        assertEquals(CreateFailure.UNKNOWN, createRequest(reference(), active(1), sender).failure);
        assertEquals(1, bodies.size());
        assertNotNull(createRequest(reference(), active(1), sender).receipt);
        assertEquals(bodies.get(0), bodies.get(1));
    }

    @Test
    public void create_classifiesHttpFailuresWithoutLegacyFallback() {
        for (int code : new int[]{400, 401, 403, 404, 405, 409, 422, 408, 429, 500, 503}) {
            List<String> paths = new ArrayList<>();
            CreateResult result = createRequest(reference(), active(1), (method, path, body) -> {
                paths.add(path);
                if (path.equals("/api/auth/me")) return me(false);
                throw apiError(code);
            });
            assertEquals(code == 408 || code == 429 || code >= 500 ? CreateFailure.UNKNOWN : CreateFailure.REJECTED,
                    result.failure);
            assertEquals(List.of("/api/auth/me", PATH + "/create"), paths);
        }
    }

    @Test
    public void create_missingApprovalAndPrePostErrorsAreNotSent() {
        for (GuardianBookingApproval value : new GuardianBookingApproval[]{null, inactive()}) {
            assertEquals(CreateFailure.NOT_SENT, createRequest(reference(), value, (method, path, body) -> {
                throw new AssertionError("승인 없이는 전송하지 않아야 합니다.");
            }).failure);
        }
        assertEquals(CreateFailure.NOT_SENT, createRequest(reference(), active(1), (method, path, body) -> {
            assertEquals("/api/auth/me", path);
            throw new IOException("인증 준비 오류");
        }).failure);
    }

    @Test
    public void create_malformedReceiptRemainsUnknown() throws Exception {
        for (JSONObject invalid : List.of(new JSONObject(), receipt().put("publicCode", ""),
                receipt().put("appointmentId", "invalid"))) {
            assertEquals(CreateFailure.UNKNOWN, createRequest(reference(), active(1),
                    (method, path, body) -> path.equals("/api/auth/me") ? me(false) : invalid).failure);
        }
    }

    @Test
    public void accountGuard_dropsLateOriginalAndReceiptAfterAccountSwitchOrLogout() throws Exception {
        AtomicBoolean current = new AtomicBoolean(true);
        List<Object> success = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        RepositoryCallback<Object> callback = guardedCallback(current::get, callback(success, errors));
        Object draft = loadRequest(reference(), (method, path, body) ->
                path.equals("/api/auth/me") ? me(true) : detail());
        callback.onSuccess(draft);
        assertEquals(List.of(draft), success);
        current.set(false);
        callback.onSuccess(draft);
        callback.onSuccess(receipt());
        assertEquals(1, success.size());
        assertEquals(2, errors.size());
        assertThrows(CoreApiAuthenticatedClient.ApiException.class, () -> requireCurrentAccount(current::get));
    }

    @Test
    public void accountGuard_hidesServerErrorAndDoesNotReclassifyDiscardedReceiptAsNotSent() {
        List<CreateResult> success = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        AtomicBoolean current = new AtomicBoolean(true);
        RepositoryCallback<CreateResult> guarded = CoreApiGuardianBookingRepository.createCallback(
                guardedCallback(current::get, callback(success, errors)));
        guarded.onError("인증 준비 실패 원문");
        assertEquals(CreateFailure.NOT_SENT, success.get(0).failure);
        current.set(false);
        guarded.onSuccess(CreateResult.failed(CreateFailure.UNKNOWN));
        assertEquals(1, success.size());
        assertEquals(1, errors.size());
        guardedCallback(current::get, callback(success, errors)).onError("민감한 서버 원문");
        assertFalse(errors.get(1).contains("민감한"));
    }

    @Test
    public void summaryModelCannotBePairedWithAnotherOriginalRequest() throws Exception {
        Summary other = parseSummary(summary().put("clientRequestId", GRANT.toString()));
        GuardianBookingStoredDraft.Participant participant = new GuardianBookingStoredDraft.Participant("합성", "****");
        assertThrows(IllegalArgumentException.class, () -> new GuardianBookingStoredDraft(other, request(),
                participant, participant));
    }

    private static <T> RepositoryCallback<T> callback(List<T> success, List<String> errors) {
        return new RepositoryCallback<T>() {
            @Override public void onSuccess(T result) { success.add(result); }
            @Override public void onError(String message) { errors.add(message); }
        };
    }

    private static GuardianBookingDraftReference reference() { return GuardianBookingDraftReference.from(request()); }

    private static JSONObject summary() throws JSONException {
        return new JSONObject().put("patientUserId", PATIENT.toString()).put("guardianUserId", GUARDIAN.toString())
                .put("clientRequestId", REQUEST.toString()).put("createdAt", NOW.toString())
                .put("expiresAt", NOW.plusSeconds(3600).toString());
    }

    private static JSONObject detail() throws JSONException {
        return new JSONObject().put("request", summary()).put("appointment", new JSONObject()
                .put("clientRequestId", REQUEST.toString()).put("pricePolicyVersion", "mvp-fixed-40000-v1")
                .put("expectedFinalPrice", 40_000).put("draft", CoreApiAppointmentClient.buildDraftBody(request().getDraft())))
                .put("patient", new JSONObject().put("name", "합성 환자 현재 이름").put("maskedPhone", "***-****-0000"))
                .put("guardian", new JSONObject().put("name", "합성 보호자").put("maskedPhone", "****"));
    }

    private static JSONObject me(boolean patient) throws JSONException {
        return new JSONObject().put("userId", (patient ? PATIENT : GUARDIAN).toString())
                .put("role", patient ? "PATIENT" : "GUARDIAN");
    }

    private static JSONObject approval() throws JSONException {
        return new JSONObject().put("grantId", GRANT.toString()).put("version", 1)
                .put("policyVersion", GuardianBookingApproval.POLICY_VERSION).put("active", true)
                .put("grantedAt", NOW.minusSeconds(1).toString()).put("expiresAt", NOW.plusSeconds(3600).toString())
                .put("revokedAt", JSONObject.NULL);
    }

    private static JSONObject receipt() throws JSONException {
        return new JSONObject().put("appointmentId", REQUEST.toString()).put("publicCode", "TEST-1");
    }

    private static CoreApiAuthenticatedClient.ApiException apiError(int status) {
        return new CoreApiAuthenticatedClient.ApiException(status, "합성 오류");
    }
}
