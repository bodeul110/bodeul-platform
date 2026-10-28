package com.example.bodeul.data.coreapi;

import android.content.Context;

import com.example.bodeul.data.GuardianBookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingCouponType;
import com.example.bodeul.domain.model.BookingManagerGenderPreference;
import com.example.bodeul.domain.model.BookingMobilitySupport;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPricePolicy;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.BookingTripType;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.UUID;

/** 생성 승인 전용 경계다. 기존 예약 API·Firestore·정보공유 동의로 우회하지 않는다. */
public final class CoreApiGuardianBookingRepository implements GuardianBookingRepository {
    static final String ROOT = "/api/appointments/guardian-booking";
    private final CoreApiAuthenticatedClient client;

    public CoreApiGuardianBookingRepository(Context context) {
        client = new CoreApiAuthenticatedClient(context);
    }

    @Override
    public void load(GuardianBookingRequest request, RepositoryCallback<Review> callback) {
        execute(sender -> loadRequest(request, sender), callback);
    }

    @Override
    public void grant(GuardianBookingRequest request, long expectedVersion, boolean confirmed,
            RepositoryCallback<GuardianBookingApproval> callback) {
        execute(sender -> grantRequest(request, expectedVersion, confirmed, sender), callback);
    }

    @Override
    public void revoke(GuardianBookingRequest request, long expectedVersion,
            RepositoryCallback<GuardianBookingApproval> callback) {
        execute(sender -> revokeRequest(request, expectedVersion, sender), callback);
    }

    @Override
    public void create(GuardianBookingRequest request, GuardianBookingApproval approval,
            RepositoryCallback<CreateResult> callback) {
        execute(sender -> createRequest(request, approval, sender), createCallback(callback));
    }

    static RepositoryCallback<CreateResult> createCallback(RepositoryCallback<CreateResult> callback) {
        return new RepositoryCallback<CreateResult>() {
            @Override
            public void onSuccess(CreateResult result) { callback.onSuccess(result); }

            @Override
            public void onError(String ignored) {
                // createRequest가 전송 중 오류를 모두 분류하므로 여기에는 인증 준비 실패만 온다.
                callback.onSuccess(CreateResult.failed(CreateFailure.NOT_SENT));
            }
        };
    }

    private <T> void execute(Operation<T> operation, RepositoryCallback<T> callback) {
        client.execute((idToken, appCheckToken) -> operation.run((method, path, body) ->
                        client.requestJson(method, path, body, idToken, appCheckToken)),
                callback, "보호자 예약 요청을 처리하지 못했습니다. 다시 확인해 주세요.", "보호자 예약 API");
    }

    static Review loadRequest(GuardianBookingRequest request, Sender sender) throws Exception {
        boolean patient = identify(request, sender);
        GuardianBookingApproval approval = parseApproval(sender.send("GET", statusPath(request), null));
        GuardianBookingRequest normalized = request;
        if (patient) {
            try {
                normalized = parsePreview(sender.send("POST", ROOT + "/preview", patientBody(request)), request);
            } catch (CoreApiAuthenticatedClient.ApiException error) {
                // 지난 예약·변경된 프로필은 새 승인이 불가해도 조회한 기존 승인을 철회할 수 있다.
                if (error.getStatusCode() != 400 && error.getStatusCode() != 409) throw error;
                return new Review(request, approval, true, false);
            }
        }
        return new Review(normalized, approval, patient);
    }

    static GuardianBookingApproval grantRequest(GuardianBookingRequest request, long version,
            boolean confirmed, Sender sender) throws Exception {
        requireVersion(version);
        if (!confirmed) throw new JSONException("성인 환자 본인 확인이 필요합니다.");
        requireActor(request, true, sender);
        JSONObject body = patientBody(request).put("expectedVersion", version)
                .put("adultPatientConfirmed", true);
        return parseApproval(sender.send("POST", ROOT + "/approvals", body));
    }

    static GuardianBookingApproval revokeRequest(GuardianBookingRequest request, long version,
            Sender sender) throws Exception {
        requireVersion(version);
        requireActor(request, true, sender);
        return parseApproval(sender.send("POST", ROOT + "/guardians/" + request.getGuardianId()
                + "/requests/" + request.getRequestId() + "/revoke",
                new JSONObject().put("expectedVersion", version)));
    }

    static CreateResult createRequest(GuardianBookingRequest request,
            GuardianBookingApproval approval, Sender sender) {
        boolean submitted = false;
        try {
            if (approval == null || approval.getGrantId() == null || approval.getVersion() < 1) {
                return CreateResult.failed(CreateFailure.NOT_SENT);
            }
            requireActor(request, false, sender);
            JSONObject body = new JSONObject().put("patientUserId", request.getPatientId().toString())
                    .put("grantId", approval.getGrantId().toString())
                    .put("approvalVersion", approval.getVersion())
                    .put("appointment", appointmentBody(request));
            submitted = true;
            JSONObject receipt = sender.send("POST", ROOT + "/create", body);
            return CreateResult.accepted(new GuardianBookingReceipt(
                    uuid(receipt, "appointmentId"), text(receipt, "publicCode")));
        } catch (CoreApiAuthenticatedClient.ApiException error) {
            if (!submitted) return CreateResult.failed(CreateFailure.NOT_SENT);
            // 시간 초과·5xx는 커밋 여부를 단정할 수 없어 확정 거부 목록에 넣지 않는다.
            boolean rejected = switch (error.getStatusCode()) {
                case 400, 401, 403, 404, 405, 409, 422 -> true;
                default -> false;
            };
            return CreateResult.failed(rejected ? CreateFailure.REJECTED : CreateFailure.UNKNOWN);
        } catch (Exception error) {
            // 전송 후 단절·잘못된 성공 응답도 미생성으로 간주하지 않는다.
            return CreateResult.failed(submitted ? CreateFailure.UNKNOWN : CreateFailure.NOT_SENT);
        }
    }

    private static boolean identify(GuardianBookingRequest request, Sender sender) throws Exception {
        JSONObject me = sender.send("GET", "/api/auth/me", null);
        UUID id = uuid(me, "userId");
        String role = text(me, "role");
        if (id.equals(request.getPatientId()) && "PATIENT".equals(role)) return true;
        if (id.equals(request.getGuardianId()) && "GUARDIAN".equals(role)) return false;
        throw new CoreApiAuthenticatedClient.ApiException(403, "이 예약 요청의 참여자가 아닙니다.");
    }

    private static void requireActor(GuardianBookingRequest request, boolean patient, Sender sender)
            throws Exception {
        if (identify(request, sender) != patient) {
            throw new CoreApiAuthenticatedClient.ApiException(403, "이 작업을 수행할 수 없는 계정입니다.");
        }
    }

    static String statusPath(GuardianBookingRequest request) {
        return ROOT + "/patients/" + request.getPatientId() + "/guardians/" + request.getGuardianId()
                + "/requests/" + request.getRequestId();
    }

    static JSONObject appointmentBody(GuardianBookingRequest request) throws JSONException {
        return CoreApiAppointmentClient.buildCreateDraftBody(request.getDraft())
                .put("clientRequestId", request.getRequestId().toString());
    }

    private static JSONObject patientBody(GuardianBookingRequest request) throws JSONException {
        return new JSONObject().put("guardianUserId", request.getGuardianId().toString())
                .put("appointment", appointmentBody(request));
    }

    static GuardianBookingRequest parsePreview(JSONObject response, GuardianBookingRequest request)
            throws JSONException {
        if (!uuid(response, "clientRequestId").equals(request.getRequestId())
                || !BookingPricePolicy.VERSION.equals(text(response, "pricePolicyVersion"))
                || number(response, "expectedFinalPrice") != BookingPricePolicy.BASE_PRICE) {
            throw new JSONException("예약 요청과 가격 계약이 일치하지 않습니다.");
        }
        JSONObject draft = response.getJSONObject("draft");
        try {
            BookingPaymentMethod method = BookingPaymentMethod.valueOf(text(draft, "paymentMethodCode"));
            if (!method.isSelectableForRequest()) throw new IllegalArgumentException();
            BookingRequestDraft normalized = BookingRequestDraft.builder()
                    .linkedParticipantName(text(draft, "linkedParticipantName"))
                    .linkedParticipantPhone(text(draft, "linkedParticipantPhone"))
                    .linkedParticipantEmail(text(draft, "linkedParticipantEmail"))
                    .patientConditionSummary(text(draft, "patientConditionSummary"))
                    .medicationSummary(text(draft, "medicationSummary"))
                    .hospitalName(text(draft, "hospitalName"))
                    .departmentName(text(draft, "departmentName"))
                    .hospitalLatitude(coordinate(draft, "hospitalLatitude", 90))
                    .hospitalLongitude(coordinate(draft, "hospitalLongitude", 180))
                    .appointmentAt(text(draft, "appointmentAt"))
                    .meetingPlace(text(draft, "meetingPlace"))
                    .specialNotes(text(draft, "specialNotes"))
                    .mobilitySupport(BookingMobilitySupport.valueOf(text(draft, "mobilitySupportCode")))
                    .tripType(BookingTripType.valueOf(text(draft, "tripTypeCode")))
                    .managerGenderPreference(BookingManagerGenderPreference.valueOf(
                            text(draft, "managerGenderPreferenceCode")))
                    .paymentMethod(method)
                    .couponType(BookingCouponType.valueOf(text(draft, "couponCode")))
                    .priceSummary(new BookingPriceSummary(BookingPricePolicy.BASE_PRICE, 0, 0,
                            BookingPricePolicy.BASE_PRICE)).build();
            return request.withNormalizedDraft(normalized);
        } catch (IllegalArgumentException error) {
            throw new JSONException("예약 미리보기 응답을 확인하지 못했습니다.");
        }
    }

    static GuardianBookingApproval parseApproval(JSONObject response) throws JSONException {
        if (!GuardianBookingApproval.POLICY_VERSION.equals(text(response, "policyVersion"))) {
            throw new JSONException("예약 승인 정책을 확인해 주세요.");
        }
        Object active = response.get("active");
        if (!(active instanceof Boolean)) throw new JSONException("예약 승인 상태가 올바르지 않습니다.");
        // 누락과 명시적인 null을 구분해 일부 응답을 미승인 상태로 복원하지 않는다.
        response.get("grantId");
        try {
            return new GuardianBookingApproval(response.isNull("grantId") ? null : uuid(response, "grantId"),
                    number(response, "version"), instant(response, "grantedAt"),
                    instant(response, "expiresAt"), instant(response, "revokedAt"), (Boolean) active);
        } catch (IllegalArgumentException error) {
            throw new JSONException("예약 승인 응답을 확인하지 못했습니다.");
        }
    }

    private static Instant instant(JSONObject response, String key) throws JSONException {
        Object value = response.get(key);
        if (value == JSONObject.NULL) return null;
        try {
            return Instant.parse(text(response, key));
        } catch (java.time.format.DateTimeParseException error) {
            throw new JSONException("예약 승인 시각이 올바르지 않습니다.");
        }
    }

    private static UUID uuid(JSONObject response, String key) throws JSONException {
        String value = text(response, key);
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException error) {
            throw new JSONException("예약 식별자가 올바르지 않습니다.");
        }
    }

    private static String text(JSONObject response, String key) throws JSONException {
        Object value = response.get(key);
        if (!(value instanceof String)) throw new JSONException("예약 응답 문자열을 확인해 주세요.");
        return (String) value;
    }

    private static long number(JSONObject response, String key) throws JSONException {
        Object value = response.get(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new JSONException("예약 응답 정수를 확인해 주세요.");
        }
        return ((Number) value).longValue();
    }

    private static double coordinate(JSONObject response, String key, double limit) throws JSONException {
        Object value = response.get(key);
        if (!(value instanceof Number)) throw new JSONException("예약 좌표를 확인해 주세요.");
        double result = ((Number) value).doubleValue();
        if (!Double.isFinite(result) || Math.abs(result) > limit) {
            throw new JSONException("예약 좌표 범위를 확인해 주세요.");
        }
        return result;
    }

    private static void requireVersion(long version) throws JSONException {
        if (version < 0 || version == Long.MAX_VALUE) throw new JSONException("예약 승인 버전을 확인해 주세요.");
    }

    interface Sender {
        JSONObject send(String method, String path, JSONObject body) throws Exception;
    }

    private interface Operation<T> {
        T run(Sender sender) throws Exception;
    }
}
