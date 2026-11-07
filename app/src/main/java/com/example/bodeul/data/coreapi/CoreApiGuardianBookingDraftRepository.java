package com.example.bodeul.data.coreapi;

import static com.example.bodeul.data.coreapi.CoreApiGuardianBookingRepository.*;

import android.content.Context;

import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.GuardianBookingRepository.CreateFailure;
import com.example.bodeul.data.GuardianBookingRepository.CreateResult;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Participant;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** 원문은 서버에서만 복구하고 저장된 요청의 승인·접수에 클라이언트 본문을 섞지 않는다. */
public final class CoreApiGuardianBookingDraftRepository implements GuardianBookingDraftRepository {
    private static final String DRAFTS = ROOT + "/drafts";
    private static final String FAILURE = "보호자 예약 요청을 처리하지 못했습니다. 다시 확인해 주세요.";
    private final CoreApiAuthenticatedClient client;

    public CoreApiGuardianBookingDraftRepository(Context context) {
        client = new CoreApiAuthenticatedClient(context);
    }

    @Override
    public void submitDraft(GuardianBookingRequest request, RepositoryCallback<Summary> callback) {
        execute((sender, arrays) -> submitRequest(request, sender), callback);
    }

    @Override
    public void listDrafts(RepositoryCallback<List<Summary>> callback) {
        execute(CoreApiGuardianBookingDraftRepository::listRequest, callback);
    }

    @Override
    public void loadDraft(GuardianBookingDraftReference reference,
            RepositoryCallback<GuardianBookingStoredDraft> callback) {
        execute((sender, arrays) -> loadRequest(reference, sender), callback);
    }

    @Override
    public void loadApproval(GuardianBookingDraftReference reference, RepositoryCallback<ApprovalStatus> callback) {
        execute((sender, arrays) -> approvalRequest(reference, sender), callback);
    }

    @Override
    public void grantDraft(GuardianBookingDraftReference reference, long version, boolean confirmed,
            RepositoryCallback<GuardianBookingApproval> callback) {
        execute((sender, arrays) -> grantRequest(reference, version, confirmed, sender), callback);
    }

    @Override
    public void revokeDraft(GuardianBookingDraftReference reference, long version,
            RepositoryCallback<GuardianBookingApproval> callback) {
        execute((sender, arrays) -> revokeRequest(reference, version, sender), callback);
    }

    @Override
    public void createDraft(GuardianBookingDraftReference reference, GuardianBookingApproval approval,
            RepositoryCallback<CreateResult> callback) {
        AccountScope sameAccount = currentAccountGuard();
        // 인증 준비 실패만 NOT_SENT로 바꾼 뒤 계정 경계를 검사한다. 계정 전환은 미접수 증거가 아니다.
        run((sender, arrays) -> createRequest(reference, approval, sender),
                createCallback(guardedCallback(sameAccount, callback)), sameAccount);
    }

    private <T> void execute(Operation<T> operation, RepositoryCallback<T> callback) {
        AccountScope sameAccount = currentAccountGuard();
        run(operation, guardedCallback(sameAccount, callback), sameAccount);
    }

    private <T> void run(Operation<T> operation, RepositoryCallback<T> callback, AccountScope sameAccount) {
        client.executeForAccount(sameAccount.uid, (idToken, appCheckToken) -> operation.run((method, path, body) -> {
            requireCurrentAccount(sameAccount);
            return client.requestJson(method, path, body, idToken, appCheckToken);
        }, path -> {
            requireCurrentAccount(sameAccount);
            return client.requestJsonArray(path, idToken, appCheckToken);
        }), callback, FAILURE, "보호자 예약 원문 API");
    }

    private static AccountScope currentAccountGuard() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser original = auth.getCurrentUser();
        String uid = original == null ? null : original.getUid();
        return new AccountScope(auth, uid);
    }

    private static final class AccountScope implements BooleanSupplier {
        final FirebaseAuth auth;
        final String uid;

        AccountScope(FirebaseAuth auth, String uid) { this.auth = auth; this.uid = uid; }

        @Override
        public boolean getAsBoolean() {
            FirebaseUser current = auth.getCurrentUser();
            return uid != null && current != null && uid.equals(current.getUid());
        }
    }

    static <T> RepositoryCallback<T> guardedCallback(BooleanSupplier sameAccount, RepositoryCallback<T> callback) {
        return new RepositoryCallback<T>() {
            @Override
            public void onSuccess(T result) {
                if (sameAccount.getAsBoolean()) callback.onSuccess(result);
                else callback.onError("로그인 계정이 변경되었습니다. 요청을 다시 확인해 주세요.");
            }

            @Override
            public void onError(String ignored) { callback.onError(FAILURE); }
        };
    }

    static void requireCurrentAccount(BooleanSupplier sameAccount) throws CoreApiAuthenticatedClient.ApiException {
        if (!sameAccount.getAsBoolean()) throw new CoreApiAuthenticatedClient.ApiException(401, FAILURE);
    }

    static Summary submitRequest(GuardianBookingRequest request, Sender sender) throws Exception {
        GuardianBookingDraftReference reference = GuardianBookingDraftReference.from(request);
        requireActor(reference, false, sender);
        JSONObject response = sender.send("POST", DRAFTS, new JSONObject()
                .put("patientUserId", reference.getPatientId().toString())
                .put("appointment", appointmentBody(request)));
        Summary result = parseSummary(response);
        requireSameReference(reference, result.getReference());
        return result;
    }

    static List<Summary> listRequest(Sender sender, ArrayReader arrays) throws Exception {
        Actor actor = actor(sender);
        JSONArray rows = arrays.get(DRAFTS);
        if (rows.length() > 50) throw new JSONException("예약 원문 목록 범위를 확인해 주세요.");
        List<Summary> result = new ArrayList<>();
        HashSet<GuardianBookingDraftReference> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            Summary summary = parseSummary(rows.getJSONObject(i));
            actor.isPatient(summary.getReference());
            if (!seen.add(summary.getReference())) throw new JSONException("중복된 예약 요청입니다.");
            result.add(summary);
        }
        return List.copyOf(result);
    }

    static GuardianBookingStoredDraft loadRequest(GuardianBookingDraftReference reference, Sender sender)
            throws Exception {
        actor(sender).isPatient(reference);
        JSONObject response = sender.send("GET", draftPath(reference), null);
        Summary summary = parseSummary(response.getJSONObject("request"));
        requireSameReference(reference, summary.getReference());
        GuardianBookingRequest request = parsePreview(response.getJSONObject("appointment"),
                reference.getPatientId(), reference.getGuardianId(), reference.getRequestId());
        try {
            return new GuardianBookingStoredDraft(summary, request,
                    participant(response.getJSONObject("patient")), participant(response.getJSONObject("guardian")));
        } catch (IllegalArgumentException error) {
            throw new JSONException("예약 참여자 표시 정보를 확인하지 못했습니다.");
        }
    }

    static ApprovalStatus approvalRequest(GuardianBookingDraftReference reference, Sender sender) throws Exception {
        boolean patient = actor(sender).isPatient(reference);
        JSONObject response = sender.send("GET", ROOT + "/patients/" + reference.getPatientId()
                + "/guardians/" + reference.getGuardianId() + "/requests/" + reference.getRequestId(), null);
        GuardianBookingApproval approval = parseApproval(response);
        Object mode = response.has("revocationOnly") ? response.get("revocationOnly") : Boolean.FALSE;
        if (!(mode instanceof Boolean)) throw new JSONException("예약 승인 조회 모드가 올바르지 않습니다.");
        try {
            return new ApprovalStatus(approval, patient, (Boolean) mode);
        } catch (IllegalArgumentException error) {
            throw new JSONException("기존 환자 승인만 철회 모드로 조회할 수 있습니다.");
        }
    }

    static GuardianBookingApproval grantRequest(GuardianBookingDraftReference reference, long version,
            boolean confirmed, Sender sender) throws Exception {
        requireVersion(version);
        if (!confirmed) throw new JSONException("성인 환자 본인 확인이 필요합니다.");
        requireActor(reference, true, sender);
        return parseApproval(sender.send("POST", draftPath(reference) + "/approvals",
                new JSONObject().put("expectedVersion", version).put("adultPatientConfirmed", true)));
    }

    static GuardianBookingApproval revokeRequest(GuardianBookingDraftReference reference, long version,
            Sender sender) throws Exception {
        requireVersion(version);
        requireActor(reference, true, sender);
        return parseApproval(sender.send("POST", ROOT + "/guardians/" + reference.getGuardianId()
                + "/requests/" + reference.getRequestId() + "/revoke", new JSONObject().put("expectedVersion", version)));
    }

    static CreateResult createRequest(GuardianBookingDraftReference reference, GuardianBookingApproval approval,
            Sender sender) {
        boolean submitted = false;
        try {
            if (approval == null || approval.getGrantId() == null || approval.getVersion() < 1) {
                return CreateResult.failed(CreateFailure.NOT_SENT);
            }
            requireActor(reference, false, sender);
            JSONObject body = new JSONObject().put("grantId", approval.getGrantId().toString())
                    .put("approvalVersion", approval.getVersion());
            // 원문 만료 후 접수증 복구도 같은 경로다. 자동 재조회·새 요청 생성은 하지 않는다.
            submitted = true;
            JSONObject response = sender.send("POST", draftPath(reference) + "/create", body);
            return CreateResult.accepted(new GuardianBookingReceipt(uuid(response, "appointmentId"),
                    text(response, "publicCode")));
        } catch (CoreApiAuthenticatedClient.ApiException error) {
            if (!submitted) return CreateResult.failed(CreateFailure.NOT_SENT);
            boolean rejected = switch (error.getStatusCode()) {
                case 400, 401, 403, 404, 405, 409, 422 -> true;
                default -> false;
            };
            return CreateResult.failed(rejected ? CreateFailure.REJECTED : CreateFailure.UNKNOWN);
        } catch (Exception error) {
            return CreateResult.failed(submitted ? CreateFailure.UNKNOWN : CreateFailure.NOT_SENT);
        }
    }

    static Summary parseSummary(JSONObject response) throws JSONException {
        try {
            return new Summary(new GuardianBookingDraftReference(uuid(response, "patientUserId"),
                    uuid(response, "guardianUserId"), uuid(response, "clientRequestId")),
                    instant(response, "createdAt"), instant(response, "expiresAt"));
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new JSONException("예약 원문 식별자와 유효기간을 확인해 주세요.");
        }
    }

    private static Participant participant(JSONObject value) throws JSONException {
        return new Participant(text(value, "name"), text(value, "maskedPhone"));
    }

    private static String draftPath(GuardianBookingDraftReference reference) {
        return DRAFTS + "/guardians/" + reference.getGuardianId() + "/requests/" + reference.getRequestId();
    }

    private static void requireSameReference(GuardianBookingDraftReference expected,
            GuardianBookingDraftReference actual) throws JSONException {
        if (!expected.equals(actual)) throw new JSONException("다른 예약 원문이 반환되었습니다.");
    }

    private static Actor actor(Sender sender) throws Exception {
        JSONObject me = sender.send("GET", "/api/auth/me", null);
        String role = text(me, "role");
        if (!"PATIENT".equals(role) && !"GUARDIAN".equals(role)) {
            throw new CoreApiAuthenticatedClient.ApiException(403, "보호자 예약을 이용할 수 없는 계정입니다.");
        }
        return new Actor(uuid(me, "userId"), "PATIENT".equals(role));
    }

    private static void requireActor(GuardianBookingDraftReference reference, boolean patient, Sender sender)
            throws Exception {
        if (actor(sender).isPatient(reference) != patient) {
            throw new CoreApiAuthenticatedClient.ApiException(403, "이 작업을 수행할 수 없는 계정입니다.");
        }
    }

    private static final class Actor {
        final UUID id;
        final boolean patient;

        Actor(UUID id, boolean patient) { this.id = id; this.patient = patient; }

        boolean isPatient(GuardianBookingDraftReference reference) throws CoreApiAuthenticatedClient.ApiException {
            if (!id.equals(patient ? reference.getPatientId() : reference.getGuardianId())) {
                throw new CoreApiAuthenticatedClient.ApiException(403, "이 예약 요청의 참여자가 아닙니다.");
            }
            return patient;
        }
    }

    interface ArrayReader { JSONArray get(String path) throws Exception; }
    private interface Operation<T> { T run(Sender sender, ArrayReader arrays) throws Exception; }
}
