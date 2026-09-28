package com.example.bodeul.data.coreapi;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPricePolicy;
import com.example.bodeul.domain.model.BookingRequestDraft;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Firebase 인증 토큰으로 Core API 예약 경계를 호출한다.
 */
final class CoreApiAppointmentClient {
    private final CoreApiAuthenticatedClient authenticatedClient;
    private final String priceServerUnavailableMessage;
    private final Map<String, AppointmentReference> references = new ConcurrentHashMap<>();
    private final Map<String, UUID> pendingCreateRequestIds = new ConcurrentHashMap<>();

    CoreApiAppointmentClient(Context context) {
        authenticatedClient = new CoreApiAuthenticatedClient(context);
        priceServerUnavailableMessage = context.getString(R.string.booking_price_server_unavailable);
    }

    void getAppointments(RepositoryCallback<List<AppointmentRequest>> callback) {
        executeAuthenticated(
                (idToken, appCheckToken) -> {
                    JSONObject response = requestJson(
                            "GET",
                            "/api/appointments",
                            null,
                            idToken,
                            appCheckToken);
                    JSONArray appointments = response.optJSONArray("appointments");
                    List<AppointmentRequest> result = new ArrayList<>();
                    if (appointments == null) {
                        return result;
                    }
                    for (int index = 0; index < appointments.length(); index++) {
                        JSONObject item = appointments.optJSONObject(index);
                        if (item != null) {
                            result.add(parseAndRemember(item).request);
                        }
                    }
                    return result;
                },
                callback,
                "예약 목록을 불러오지 못했습니다."
        );
    }

    void getAppointment(
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> parseAndRemember(requestJson(
                                "GET",
                                "/api/appointments/" + reference.coreId,
                                null,
                                idToken,
                                appCheckToken)).request,
                        callback,
                        "예약 상세 정보를 불러오지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void createAppointment(
            BookingRequestDraft draft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        String draftPayload;
        try {
            draftPayload = buildCreateDraftBody(draft).toString();
        } catch (JSONException exception) {
            postError(callback, "예약 입력값을 확인해 주세요.");
            return;
        }
        UUID clientRequestId = pendingCreateRequestIds.computeIfAbsent(
                draftPayload,
                ignored -> UUID.randomUUID());
        executeAuthenticated(
                (idToken, appCheckToken) -> {
                    JSONObject body = new JSONObject(draftPayload);
                    body.put("clientRequestId", clientRequestId.toString());
                    JSONObject response = requestCreateWithRetry(
                            body,
                            (path, request) -> requestJson("POST", path, request, idToken, appCheckToken),
                            priceServerUnavailableMessage);
                    return parseAndRemember(response).request;
                },
                new RepositoryCallback<AppointmentRequest>() {
                    @Override
                    public void onSuccess(AppointmentRequest result) {
                        pendingCreateRequestIds.remove(draftPayload, clientRequestId);
                        callback.onSuccess(result);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                },
                "동행 요청을 저장하지 못했습니다."
        );
    }

    void updateAppointment(
            String requestId,
            BookingRequestDraft draft,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> {
                            ApiAppointment current = parseAndRemember(requestJson(
                                    "GET",
                                    "/api/appointments/" + reference.coreId,
                                    null,
                                    idToken,
                                    appCheckToken));
                            JSONObject body = buildDraftBody(draft);
                            body.put("version", current.reference.version);
                            return parseAndRemember(requestJson(
                                    "PUT",
                                    "/api/appointments/" + current.reference.coreId,
                                    body,
                                    idToken,
                                    appCheckToken)).request;
                        },
                        callback,
                        "동행 요청을 수정하지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void cancelAppointment(
            String requestId,
            RepositoryCallback<AppointmentRequest> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                executeAuthenticated(
                        (idToken, appCheckToken) -> {
                            ApiAppointment current = parseAndRemember(requestJson(
                                    "GET",
                                    "/api/appointments/" + reference.coreId,
                                    null,
                                    idToken,
                                    appCheckToken));
                            JSONObject body = new JSONObject();
                            body.put("version", current.reference.version);
                            return parseAndRemember(requestJson(
                                    "POST",
                                    "/api/appointments/" + current.reference.coreId + "/cancel",
                                    body,
                                    idToken,
                                    appCheckToken)).request;
                        },
                        callback,
                        "동행 요청을 취소하지 못했습니다."
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void resolveLegacyFirestoreId(
            String requestId,
            RepositoryCallback<String> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                callback.onSuccess(reference.legacyFirestoreId);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void resolveCoreId(
            String requestId,
            RepositoryCallback<String> callback
    ) {
        resolveReference(requestId, new RepositoryCallback<AppointmentReference>() {
            @Override
            public void onSuccess(AppointmentReference reference) {
                callback.onSuccess(reference.coreId);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    String getKnownLegacyFirestoreId(String requestId) {
        AppointmentReference reference = references.get(normalizeText(requestId));
        return reference == null ? "" : reference.legacyFirestoreId;
    }

    String getKnownCoreId(String requestId) {
        AppointmentReference reference = references.get(normalizeText(requestId));
        return reference == null ? "" : reference.coreId;
    }

    private void resolveReference(
            String requestId,
            RepositoryCallback<AppointmentReference> callback
    ) {
        String normalizedRequestId = normalizeText(requestId);
        AppointmentReference known = references.get(normalizedRequestId);
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        if (isUuid(normalizedRequestId)) {
            AppointmentReference direct = new AppointmentReference(
                    normalizedRequestId,
                    "",
                    0L);
            references.put(normalizedRequestId, direct);
            callback.onSuccess(direct);
            return;
        }

        getAppointments(new RepositoryCallback<List<AppointmentRequest>>() {
            @Override
            public void onSuccess(List<AppointmentRequest> result) {
                AppointmentReference resolved = references.get(normalizedRequestId);
                if (resolved == null) {
                    callback.onError("예약 정보를 찾지 못했습니다.");
                    return;
                }
                callback.onSuccess(resolved);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    static JSONObject buildCreateDraftBody(BookingRequestDraft draft) throws JSONException {
        JSONObject body = buildDraftBody(draft);
        body.put("pricePolicyVersion", BookingPricePolicy.VERSION);
        body.put("expectedFinalPrice", draft.getPriceSummary().getFinalPrice());
        return body;
    }

    static JSONObject requestCreateWithRetry(
            JSONObject body,
            CreateRequestSender sender,
            String priceServerUnavailableMessage
    ) throws Exception {
        // 구형 서버는 새 경로를 모른다. 실패해도 기존 생성 경로로 우회하지 않는다.
        String path = "/api/appointments/price-confirmed";
        try {
            try {
                return sender.send(path, body);
            } catch (IOException firstFailure) {
                return sender.send(path, body);
            }
        } catch (CoreApiAuthenticatedClient.ApiException exception) {
            if (exception.getStatusCode() == 404 || exception.getStatusCode() == 405) {
                throw new CoreApiAuthenticatedClient.ApiException(
                        exception.getStatusCode(), priceServerUnavailableMessage);
            }
            throw exception;
        }
    }

    interface CreateRequestSender {
        JSONObject send(String path, JSONObject body) throws Exception;
    }

    static JSONObject buildDraftBody(BookingRequestDraft draft) throws JSONException {
        if (draft == null) {
            throw new JSONException("예약 입력값이 없습니다.");
        }
        BookingPaymentMethod paymentMethod = draft.getPaymentMethod();
        if (paymentMethod == null || !paymentMethod.isSelectableForRequest()) {
            throw new JSONException("결제 수단을 다시 선택해 주세요.");
        }
        JSONObject body = new JSONObject();
        body.put("linkedParticipantName", draft.getLinkedParticipantName());
        body.put("linkedParticipantPhone", draft.getLinkedParticipantPhone());
        body.put("linkedParticipantEmail", draft.getLinkedParticipantEmail());
        body.put("patientConditionSummary", draft.getPatientConditionSummary());
        body.put("medicationSummary", draft.getMedicationSummary());
        body.put("hospitalName", draft.getHospitalName());
        body.put("departmentName", draft.getDepartmentName());
        body.put("hospitalLatitude", draft.getHospitalLatitude());
        body.put("hospitalLongitude", draft.getHospitalLongitude());
        body.put("appointmentAt", draft.getAppointmentAt());
        body.put("meetingPlace", draft.getMeetingPlace());
        body.put("specialNotes", draft.getSpecialNotes());
        body.put("mobilitySupportCode", draft.getMobilitySupport().name());
        body.put("tripTypeCode", draft.getTripType().name());
        body.put("managerGenderPreferenceCode", draft.getManagerGenderPreference().name());
        body.put("paymentMethodCode", paymentMethod.name());
        body.put("couponCode", draft.getCouponType().name());
        return body;
    }

    private ApiAppointment parseAndRemember(JSONObject item) throws JSONException {
        String coreId = requireText(item, "id");
        String legacyFirestoreId = optText(item, "legacyFirestoreId");
        long version = item.getLong("version");
        String externalId = legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId;
        AppointmentStatus status;
        try {
            status = AppointmentStatus.valueOf(requireText(item, "status"));
        } catch (IllegalArgumentException exception) {
            throw new JSONException("알 수 없는 예약 상태입니다.");
        }

        AppointmentRequest request = new AppointmentRequest(
                externalId,
                optText(item, "patientUserId"),
                optText(item, "guardianUserId"),
                optText(item, "hospitalName"),
                optText(item, "departmentName"),
                item.optDouble("hospitalLatitude", 0d),
                item.optDouble("hospitalLongitude", 0d),
                optText(item, "appointmentAt"),
                optText(item, "meetingPlace"),
                optText(item, "specialNotes"),
                status,
                optText(item, "managerUserId"),
                optText(item, "patientName"),
                optText(item, "patientPhone"),
                optText(item, "patientEmail"),
                optText(item, "guardianName"),
                optText(item, "guardianPhone"),
                optText(item, "guardianEmail"),
                optText(item, "patientConditionSummary"),
                optText(item, "medicationSummary"),
                optText(item, "mobilitySupportCode"),
                optText(item, "tripTypeCode"),
                optText(item, "managerGenderPreferenceCode"),
                optText(item, "paymentMethodCode"),
                optText(item, "couponCode"),
                item.optInt("basePrice", 0),
                item.optInt("optionSurchargePrice", 0),
                item.optInt("couponDiscountPrice", 0),
                item.optInt("finalPrice", 0),
                optText(item, "paymentStatusCode"),
                optText(item, "paymentApprovalCode"),
                optText(item, "paymentApprovedAt"),
                optText(item, "paymentProviderLabel")
        );
        request.setManagerProfile(
                optText(item, "managerName"),
                optText(item, "managerPhone"),
                optText(item, "managerEmail"));
        request.setPublicCode(optText(item, "publicCode"));
        AppointmentReference reference = new AppointmentReference(
                coreId,
                legacyFirestoreId,
                version);
        references.put(coreId, reference);
        references.put(externalId, reference);
        if (!legacyFirestoreId.isEmpty()) {
            references.put(legacyFirestoreId, reference);
        }
        return new ApiAppointment(request, reference);
    }

    private <T> void executeAuthenticated(
            CoreApiAuthenticatedClient.Operation<T> operation,
            RepositoryCallback<T> callback,
            String fallbackMessage
    ) {
        authenticatedClient.execute(operation, callback, fallbackMessage, "예약 API");
    }

    private JSONObject requestJson(
            String method,
            String path,
            @Nullable JSONObject body,
            String idToken,
            @Nullable String appCheckToken
    ) throws Exception {
        return authenticatedClient.requestJson(
                method,
                path,
                body,
                idToken,
                appCheckToken);
    }

    private String requireText(JSONObject object, String key) throws JSONException {
        String value = optText(object, key);
        if (value.isEmpty()) {
            throw new JSONException(key + " 값이 없습니다.");
        }
        return value;
    }

    private String optText(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return normalizeText(object.optString(key, ""));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        authenticatedClient.postError(callback, message);
    }

    private static final class AppointmentReference {
        private final String coreId;
        private final String legacyFirestoreId;
        private final long version;

        private AppointmentReference(String coreId, String legacyFirestoreId, long version) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.version = version;
        }
    }

    private static final class ApiAppointment {
        private final AppointmentRequest request;
        private final AppointmentReference reference;

        private ApiAppointment(AppointmentRequest request, AppointmentReference reference) {
            this.request = request;
            this.reference = reference;
        }
    }

}
