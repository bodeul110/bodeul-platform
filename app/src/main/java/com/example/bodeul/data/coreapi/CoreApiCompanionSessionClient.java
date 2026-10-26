package com.example.bodeul.data.coreapi;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.CompanionChatAttachmentUploadPolicy;
import com.example.bodeul.data.CompanionSessionArtifactUploadPolicy;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.CompanionSessionArtifact;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.SafeEnumParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 동행 세션 진행 상태와 리포트를 Core API에서 읽고 변경한다.
 */
final class CoreApiCompanionSessionClient {
    private final CoreApiAuthenticatedClient authenticatedClient;
    private final ContentResolver contentResolver;
    private final Map<String, SessionSnapshot> references = new ConcurrentHashMap<>();

    CoreApiCompanionSessionClient(Context context) {
        Context appContext = context.getApplicationContext();
        authenticatedClient = new CoreApiAuthenticatedClient(appContext);
        contentResolver = appContext.getContentResolver();
    }

    void getSessions(RepositoryCallback<List<SessionSnapshot>> callback) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> {
                    JSONObject response = authenticatedClient.requestJson(
                            "GET",
                            "/api/companion-sessions",
                            null,
                            idToken,
                            appCheckToken);
                    JSONArray items = response.optJSONArray("sessions");
                    List<SessionSnapshot> sessions = new ArrayList<>();
                    if (items == null) {
                        return sessions;
                    }
                    for (int index = 0; index < items.length(); index++) {
                        JSONObject item = items.optJSONObject(index);
                        if (item != null) {
                            sessions.add(parseAndRememberSession(item));
                        }
                    }
                    return sessions;
                },
                callback,
                "동행 정보를 불러오지 못했습니다.",
                "동행 세션 API"
        );
    }

    void findSession(
            @Nullable String legacySessionId,
            @Nullable String coreAppointmentId,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        getSessions(new RepositoryCallback<List<SessionSnapshot>>() {
            @Override
            public void onSuccess(List<SessionSnapshot> result) {
                callback.onSuccess(findKnown(legacySessionId, coreAppointmentId));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void getReport(SessionSnapshot session, RepositoryCallback<ReportSnapshot> callback) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parseReport(authenticatedClient.requestJson(
                        "GET",
                        "/api/companion-sessions/" + session.coreId + "/report",
                        null,
                        idToken,
                        appCheckToken)),
                callback,
                "동행 리포트를 불러오지 못했습니다.",
                "동행 리포트 API"
        );
    }

    void getRealtime(
            SessionSnapshot session,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        authenticatedClient.execute(
                (idToken, appCheckToken) -> parseRealtime(authenticatedClient.requestJson(
                        "GET",
                        "/api/companion-sessions/" + session.coreId + "/realtime",
                        null,
                        idToken,
                        appCheckToken)),
                callback,
                "실시간 동행 정보를 불러오지 못했습니다.",
                "동행 실시간 API"
        );
    }

    void enrichWithRealtime(
            SessionSnapshot session,
            CompanionSession model,
            RepositoryCallback<CompanionSession> callback
    ) {
        getRealtime(session, new RepositoryCallback<RealtimeSnapshot>() {
            @Override
            public void onSuccess(RealtimeSnapshot result) {
                result.applyTo(model);
                callback.onSuccess(model);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void sendRealtimeMessage(
            String externalSessionId,
            String bodyText,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                String clientMessageId = UUID.randomUUID().toString();
                List<CompanionChatAttachment> safeAttachments = new ArrayList<>();
                if (attachments != null) {
                    for (CompanionChatAttachment attachment : attachments) {
                        if (attachment != null && !attachment.isEmpty()) {
                            safeAttachments.add(attachment);
                        }
                    }
                }
                RepositoryCallback<JSONObject> responseCallback = new RepositoryCallback<JSONObject>() {
                    @Override
                    public void onSuccess(JSONObject ignored) {
                        getRealtime(session, callback);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                };
                if (safeAttachments.stream().anyMatch(
                        CoreApiCompanionSessionClient.this::isLocalAttachment)) {
                    if (!safeAttachments.stream().allMatch(
                            CoreApiCompanionSessionClient.this::isLocalAttachment)) {
                        callback.onError("첨부 파일 전송 방식을 확인하지 못했습니다.");
                        return;
                    }
                    List<CoreApiAuthenticatedClient.UploadPart> uploadParts = new ArrayList<>();
                    for (CompanionChatAttachment attachment : safeAttachments) {
                        uploadParts.add(new CoreApiAuthenticatedClient.UploadPart(
                                Uri.parse(attachment.getFullPath()),
                                attachment.getFileName(),
                                attachment.getContentType(),
                                attachment.getSizeBytes()));
                    }
                    authenticatedClient.execute(
                            (idToken, appCheckToken) -> authenticatedClient.requestMultipartJson(
                                    "/api/companion-sessions/" + session.coreId + "/messages",
                                    clientMessageId,
                                    valueOrEmpty(bodyText),
                                    uploadParts,
                                    idToken,
                                    appCheckToken),
                            responseCallback,
                            "채팅 첨부 파일을 보내지 못했습니다.",
                            "동행 채팅 첨부 API");
                    return;
                }
                JSONObject body = new JSONObject();
                try {
                    body.put("clientMessageId", clientMessageId);
                    body.put("body", valueOrEmpty(bodyText));
                    JSONArray attachmentItems = new JSONArray();
                    for (CompanionChatAttachment attachment : safeAttachments) {
                        JSONObject item = new JSONObject();
                        item.put("storagePath", attachment.getFullPath());
                        item.put("fileName", attachment.getFileName());
                        item.put("contentType", attachment.getContentType());
                        item.put("sizeBytes", attachment.getSizeBytes());
                        attachmentItems.put(item);
                    }
                    body.put("attachments", attachmentItems);
                } catch (JSONException exception) {
                    callback.onError("채팅 전송 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                "POST",
                                "/api/companion-sessions/" + session.coreId + "/messages",
                                body,
                                idToken,
                                appCheckToken),
                        responseCallback,
                        "채팅 메시지를 보내지 못했습니다.",
                        "동행 채팅 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void markRealtimeRead(String externalSessionId) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                getRealtime(session, new RepositoryCallback<RealtimeSnapshot>() {
                    @Override
                    public void onSuccess(RealtimeSnapshot snapshot) {
                        String lastMessageId = snapshot.lastMessageId();
                        if (lastMessageId.isEmpty()) {
                            return;
                        }
                        JSONObject body = new JSONObject();
                        try {
                            body.put("lastReadMessageId", lastMessageId);
                        } catch (JSONException ignored) {
                            return;
                        }
                        authenticatedClient.execute(
                                (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                        "PUT",
                                        "/api/companion-sessions/" + session.coreId + "/read-receipt",
                                        body,
                                        idToken,
                                        appCheckToken),
                                new RepositoryCallback<JSONObject>() {
                                    @Override
                                    public void onSuccess(JSONObject result) {
                                        // 읽음 표시는 화면 흐름을 막지 않는 보조 동작이다.
                                    }

                                    @Override
                                    public void onError(String message) {
                                        // 다음 화면 진입이나 Realtime 이벤트에서 다시 동기화한다.
                                    }
                                },
                                "채팅 읽음 상태를 저장하지 못했습니다.",
                                "동행 채팅 읽음 API"
                        );
                    }

                    @Override
                    public void onError(String message) {
                        // 읽음 표시는 화면 오류로 확장하지 않는다.
                    }
                });
            }

            @Override
            public void onError(String message) {
                // 읽음 표시는 화면 오류로 확장하지 않는다.
            }
        });
    }

    void shareRealtimeLocation(
            String externalSessionId,
            double latitude,
            double longitude,
            RepositoryCallback<RealtimeSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("clientLocationId", UUID.randomUUID().toString());
                    body.put("latitude", latitude);
                    body.put("longitude", longitude);
                    body.put("capturedAt", formatInstantMillis(System.currentTimeMillis()));
                } catch (JSONException exception) {
                    callback.onError("위치 공유 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                "POST",
                                "/api/companion-sessions/" + session.coreId + "/locations",
                                body,
                                idToken,
                                appCheckToken),
                        new RepositoryCallback<JSONObject>() {
                            @Override
                            public void onSuccess(JSONObject ignored) {
                                getRealtime(session, callback);
                            }

                            @Override
                            public void onError(String message) {
                                callback.onError(message);
                            }
                        },
                        "현재 위치를 공유하지 못했습니다.",
                        "동행 위치 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void updateText(
            String externalSessionId,
            String field,
            String value,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value == null ? "" : value, null, callback);
    }

    void updateText(
            String externalSessionId,
            String field,
            String value,
            String expectedStepCode,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value == null ? "" : value,
                expectedStepCode, callback);
    }

    void updateBoolean(
            String externalSessionId,
            String field,
            boolean value,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value, null, callback);
    }

    void updateBoolean(
            String externalSessionId,
            String field,
            boolean value,
            String expectedStepCode,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        updateSession(externalSessionId, field, value, expectedStepCode, callback);
    }

    void advance(
            String externalSessionId,
            String expectedStepCode,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                if (!matchesExpectedStep(expectedStepCode, session.currentStepCode)) {
                    callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                    return;
                }
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                } catch (JSONException exception) {
                    callback.onError("동행 단계 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> parseAndRememberSession(
                                authenticatedClient.requestJson(
                                        "POST",
                                        "/api/companion-sessions/" + session.coreId + "/advance",
                                        body,
                                        idToken,
                                        appCheckToken)),
                        callback,
                        "다음 동행 단계로 이동하지 못했습니다.",
                        "동행 단계 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void endCare(
            String externalSessionId,
            String expectedStepCode,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                if (!matchesExpectedStep(expectedStepCode, session.currentStepCode)) {
                    callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                    return;
                }
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                } catch (JSONException exception) {
                    callback.onError("동행 종료 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> parseAndRememberSession(
                                authenticatedClient.requestJson(
                                        "POST",
                                        "/api/companion-sessions/" + session.coreId + "/care-end",
                                        body,
                                        idToken,
                                        appCheckToken)),
                        callback,
                        "동행 종료를 저장하지 못했습니다.",
                        "동행 종료 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    static boolean matchesExpectedStep(
            @Nullable String expectedStepCode,
            @Nullable String actualStepCode
    ) {
        return valueOrEmpty(expectedStepCode).trim()
                .equals(valueOrEmpty(actualStepCode).trim());
    }

    void replaceArtifacts(
            String externalSessionId,
            String purpose,
            String clientRequestId,
            List<Uri> fileUris,
            RepositoryCallback<JSONObject> callback
    ) {
        String validationError = CompanionSessionArtifactUploadPolicy.validate(
                contentResolver,
                purpose,
                fileUris);
        if (!validationError.isEmpty()) {
            callback.onError(validationError);
            return;
        }
        String normalizedRequestId = clientRequestId == null
                ? ""
                : clientRequestId.trim();
        try {
            UUID.fromString(normalizedRequestId);
        } catch (IllegalArgumentException exception) {
            callback.onError("첨부 교체 요청 식별자를 확인해 주세요.");
            return;
        }
        List<CoreApiAuthenticatedClient.UploadPart> uploadParts = new ArrayList<>();
        for (Uri fileUri : fileUris) {
            uploadParts.add(new CoreApiAuthenticatedClient.UploadPart(
                    fileUri,
                    CompanionChatAttachmentUploadPolicy.resolveFileName(contentResolver, fileUri),
                    CompanionChatAttachmentUploadPolicy.resolveContentType(contentResolver, fileUri),
                    CompanionChatAttachmentUploadPolicy.resolveFileSize(contentResolver, fileUri)));
        }
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                Map<String, String> textParts = new LinkedHashMap<>();
                textParts.put("purpose", purpose);
                textParts.put("clientRequestId", normalizedRequestId);
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestMultipartJson(
                                "PUT",
                                "/api/companion-sessions/" + session.coreId + "/artifacts",
                                textParts,
                                uploadParts,
                                idToken,
                                appCheckToken),
                        callback,
                        "동행 첨부 파일을 저장하지 못했습니다.",
                        "동행 단계 첨부 API");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void clearArtifacts(
            String externalSessionId,
            String purpose,
            RepositoryCallback<JSONObject> callback
    ) {
        if (!CompanionSessionArtifactUploadPolicy.PAYMENT_EVIDENCE.equals(purpose)
                && !CompanionSessionArtifactUploadPolicy.PRESCRIPTION_IMAGE.equals(purpose)) {
            callback.onError("첨부 파일 용도를 확인하지 못했습니다.");
            return;
        }
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> authenticatedClient.requestJson(
                                "DELETE",
                                "/api/companion-sessions/" + session.coreId
                                        + "/artifacts?purpose=" + purpose,
                                null,
                                idToken,
                                appCheckToken),
                        callback,
                        "동행 첨부 파일을 삭제하지 못했습니다.",
                        "동행 단계 첨부 삭제 API");
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    void submitReport(
            String externalSessionId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            @Nullable MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<ReportSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                    body.put("summary", valueOrEmpty(summary));
                    body.put("treatmentNotes", valueOrEmpty(treatmentNotes));
                    body.put("medicationNotes", valueOrEmpty(medicationNotes));
                    body.put("medicationName", valueOrEmpty(medicationName));
                    body.put("medicationChangeSummary", valueOrEmpty(medicationChangeSummary));
                    body.put("medicationScheduleNote", valueOrEmpty(medicationScheduleNote));
                    body.put(
                            "medicationComparisonDecisionCode",
                            medicationComparisonDecision == null
                                    ? ""
                                    : medicationComparisonDecision.name());
                    body.put("medicationComparisonNote", valueOrEmpty(medicationComparisonNote));
                    body.put("nextVisitAt", valueOrEmpty(nextVisitAt));
                    body.put("managerJournal", valueOrEmpty(summary));
                } catch (JSONException exception) {
                    callback.onError("동행 리포트 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> {
                            ReportSnapshot report = parseReport(authenticatedClient.requestJson(
                                    "PUT",
                                    "/api/companion-sessions/" + session.coreId + "/report",
                                    body,
                                    idToken,
                                    appCheckToken));
                            forget(session);
                            return report;
                        },
                        callback,
                        "동행 리포트를 저장하지 못했습니다.",
                        "동행 리포트 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void updateSession(
            String externalSessionId,
            String field,
            Object value,
            String expectedStepCode,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        resolveSession(externalSessionId, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot session) {
                if (expectedStepCode != null
                        && !matchesExpectedStep(expectedStepCode, session.currentStepCode)) {
                    callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                    return;
                }
                JSONObject body = new JSONObject();
                try {
                    body.put("version", session.version);
                    body.put(field, value);
                } catch (JSONException exception) {
                    callback.onError("동행 변경 요청을 준비하지 못했습니다.");
                    return;
                }
                authenticatedClient.execute(
                        (idToken, appCheckToken) -> parseAndRememberSession(
                                authenticatedClient.requestJson(
                                        "PATCH",
                                        "/api/companion-sessions/" + session.coreId,
                                        body,
                                        idToken,
                                        appCheckToken)),
                        callback,
                        "동행 정보를 저장하지 못했습니다.",
                        "동행 세션 API"
                );
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void resolveSession(
            String externalSessionId,
            RepositoryCallback<SessionSnapshot> callback
    ) {
        SessionSnapshot known = references.get(valueOrEmpty(externalSessionId));
        if (known != null) {
            callback.onSuccess(known);
            return;
        }
        findSession(externalSessionId, null, new RepositoryCallback<SessionSnapshot>() {
            @Override
            public void onSuccess(SessionSnapshot result) {
                if (result == null) {
                    callback.onError("동행 세션 정보를 찾지 못했습니다.");
                    return;
                }
                callback.onSuccess(result);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Nullable
    SessionSnapshot findKnown(
            @Nullable String legacySessionId,
            @Nullable String coreAppointmentId
    ) {
        String normalizedLegacyId = valueOrEmpty(legacySessionId);
        if (!normalizedLegacyId.isEmpty()) {
            SessionSnapshot bySession = references.get(normalizedLegacyId);
            if (bySession != null) {
                return bySession;
            }
        }
        String normalizedAppointmentId = valueOrEmpty(coreAppointmentId);
        if (!normalizedAppointmentId.isEmpty()) {
            return references.get("appointment:" + normalizedAppointmentId);
        }
        return null;
    }

    private SessionSnapshot parseAndRememberSession(JSONObject item) throws JSONException {
        SessionSnapshot snapshot = parseSessionSnapshot(item);
        references.put(snapshot.coreId, snapshot);
        references.put("appointment:" + snapshot.appointmentRequestId, snapshot);
        if (!snapshot.legacyFirestoreId.isEmpty()) {
            references.put(snapshot.legacyFirestoreId, snapshot);
        }
        return snapshot;
    }

    static SessionSnapshot parseSessionSnapshot(JSONObject item) throws JSONException {
        String coreId = requireText(item, "id");
        String legacyFirestoreId = optText(item, "legacyFirestoreId");
        String appointmentRequestId = requireText(item, "appointmentRequestId");
        String managerUserId = requireText(item, "managerUserId");
        SessionStatus status;
        try {
            status = SessionStatus.valueOf(requireText(item, "currentStatus"));
        } catch (IllegalArgumentException exception) {
            throw new JSONException("알 수 없는 동행 세션 상태입니다.");
        }

        boolean hasGuideSnapshot = item.has("steps") && !item.isNull("steps");
        List<GuideStep> guideSteps = new ArrayList<>();
        if (hasGuideSnapshot) {
            JSONArray stepItems = item.getJSONArray("steps");
            for (int index = 0; index < stepItems.length(); index++) {
                JSONObject step = stepItems.getJSONObject(index);
                guideSteps.add(new GuideStep(
                        optText(step, "code"),
                        step.getInt("order"),
                        optText(step, "title"),
                        optText(step, "description"),
                        optText(step, "videoAssetId"),
                        optText(step, "videoAssetVersion"),
                        optText(step, "videoFallbackText")));
            }
        }

        boolean hasAdvanceDecision = item.has("canAdvance") && !item.isNull("canAdvance");
        List<CompanionSessionArtifact> artifacts = new ArrayList<>();
        JSONArray artifactItems = item.optJSONArray("artifacts");
        if (artifactItems != null) {
            for (int index = 0; index < artifactItems.length(); index++) {
                JSONObject artifact = artifactItems.optJSONObject(index);
                if (artifact == null) {
                    continue;
                }
                artifacts.add(new CompanionSessionArtifact(
                        optText(artifact, "id"),
                        optText(artifact, "purpose"),
                        optText(artifact, "fileName"),
                        optText(artifact, "contentType"),
                        artifact.optLong("sizeBytes", 0L),
                        parseInstantMillis(optText(artifact, "createdAt"))));
            }
        }
        return new SessionSnapshot(
                coreId,
                legacyFirestoreId,
                appointmentRequestId,
                managerUserId,
                item.getInt("currentStepOrder"),
                item.optInt("totalStepCount", 0),
                optText(item, "guideId"),
                optNullableLong(item, "guideRevision"),
                hasGuideSnapshot,
                guideSteps,
                optText(item, "currentStepCode"),
                hasAdvanceDecision,
                hasAdvanceDecision && item.getBoolean("canAdvance"),
                optText(item, "blockedReason"),
                status,
                optText(item, "guardianUpdate"),
                optText(item, "locationSummary"),
                optText(item, "fieldPhotoNote"),
                optText(item, "medicationNote"),
                optText(item, "pharmacySummary"),
                item.optBoolean("preConsultationConfirmed", false),
                item.optBoolean("prescriptionCollected", false),
                item.optBoolean("pharmacyCompleted", false),
                item.optBoolean("medicationGuidanceCompleted", false),
                item.optBoolean("liveLocationSharingActive", false),
                optText(item, "liveLocationSharingStartedAt"),
                optText(item, "locationAlertStage"),
                optText(item, "locationAlertSentAt"),
                item.getLong("version"),
                optText(item, "careEndedAt"),
                optText(item, "managerJournal"),
                optText(item, "reportGenerationStatus"),
                item.optInt("reportGenerationAttempts", 0),
                optText(item, "reportGenerationLastError"),
                optText(item, "reportGenerationUpdatedAt"),
                artifacts);
    }

    private void forget(SessionSnapshot session) {
        references.remove(session.coreId);
        references.remove("appointment:" + session.appointmentRequestId);
        if (!session.legacyFirestoreId.isEmpty()) {
            references.remove(session.legacyFirestoreId);
        }
    }

    private ReportSnapshot parseReport(JSONObject item) throws JSONException {
        return new ReportSnapshot(
                requireText(item, "id"),
                optText(item, "legacyFirestoreId"),
                requireText(item, "companionSessionId"),
                optText(item, "summary"),
                optText(item, "treatmentNotes"),
                optText(item, "medicationNotes"),
                optText(item, "medicationName"),
                optText(item, "medicationChangeSummary"),
                optText(item, "medicationScheduleNote"),
                MedicationComparisonDecision.fromValue(
                        optText(item, "medicationComparisonDecisionCode")),
                optText(item, "medicationComparisonNote"),
                optText(item, "nextVisitAt"));
    }

    private RealtimeSnapshot parseRealtime(JSONObject item) throws JSONException {
        String realtimeTopic = requireText(item, "realtimeTopic");
        List<CompanionChatMessage> messages = new ArrayList<>();
        JSONArray messageItems = item.optJSONArray("messages");
        if (messageItems != null) {
            for (int index = 0; index < messageItems.length(); index++) {
                JSONObject message = messageItems.optJSONObject(index);
                if (message == null) {
                    continue;
                }
                List<CompanionChatAttachment> attachments = new ArrayList<>();
                JSONArray attachmentItems = message.optJSONArray("attachments");
                if (attachmentItems != null) {
                    for (int attachmentIndex = 0;
                         attachmentIndex < attachmentItems.length();
                         attachmentIndex++) {
                        JSONObject attachment = attachmentItems.optJSONObject(attachmentIndex);
                        if (attachment == null) {
                            continue;
                        }
                        attachments.add(new CompanionChatAttachment(
                                optText(attachment, "storagePath"),
                                optText(attachment, "fileName"),
                                optText(attachment, "contentType"),
                                parseInstantMillis(optText(message, "sentAt")),
                                Math.max(attachment.optLong("sizeBytes", 0L), 0L),
                                optText(attachment, "downloadPath")));
                    }
                }
                messages.add(new CompanionChatMessage(
                        requireText(message, "id"),
                        SafeEnumParser.parseOrDefault(
                                UserRole.class,
                                optText(message, "senderRole"),
                                UserRole.MANAGER),
                        optText(message, "body"),
                        parseInstantMillis(optText(message, "sentAt")),
                        attachments));
            }
        }

        List<ReadReceiptSnapshot> receipts = new ArrayList<>();
        JSONArray receiptItems = item.optJSONArray("readReceipts");
        if (receiptItems != null) {
            for (int index = 0; index < receiptItems.length(); index++) {
                JSONObject receipt = receiptItems.optJSONObject(index);
                if (receipt == null) {
                    continue;
                }
                receipts.add(new ReadReceiptSnapshot(
                        SafeEnumParser.parseOrDefault(
                                UserRole.class,
                                optText(receipt, "userRole"),
                                UserRole.PATIENT),
                        parseInstantMillis(optText(receipt, "lastReadAt"))));
            }
        }

        List<CompanionLocationHistoryEntry> locations = new ArrayList<>();
        JSONArray locationItems = item.optJSONArray("locations");
        if (locationItems != null) {
            for (int index = locationItems.length() - 1; index >= 0; index--) {
                JSONObject location = locationItems.optJSONObject(index);
                if (location == null) {
                    continue;
                }
                locations.add(new CompanionLocationHistoryEntry(
                        location.getDouble("latitude"),
                        location.getDouble("longitude"),
                        "",
                        parseInstantMillis(optText(location, "capturedAt"))));
            }
        }
        return new RealtimeSnapshot(realtimeTopic, messages, receipts, locations);
    }

    private boolean isLocalAttachment(CompanionChatAttachment attachment) {
        String scheme = Uri.parse(attachment.getFullPath()).getScheme();
        return "content".equalsIgnoreCase(scheme)
                || "file".equalsIgnoreCase(scheme)
                || "android.resource".equalsIgnoreCase(scheme);
    }

    static String formatInstantMillis(long epochMillis) {
        SimpleDateFormat formatter =
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date(epochMillis));
    }

    static long parseInstantMillis(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return 0L;
        }
        String normalized = normalizeFraction(value);
        String pattern = normalized.contains(".")
                ? "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
                : "yyyy-MM-dd'T'HH:mm:ssXXX";
        SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        ParsePosition position = new ParsePosition(0);
        Date parsed = parser.parse(normalized, position);
        if (parsed == null || position.getIndex() != normalized.length()) {
            return 0L;
        }
        return parsed.getTime();
    }

    private static String normalizeFraction(String value) {
        int dot = value.indexOf('.');
        if (dot < 0) {
            return value;
        }
        int zone = value.indexOf('Z', dot);
        if (zone < 0) {
            zone = value.indexOf('+', dot);
        }
        if (zone < 0) {
            zone = value.indexOf('-', dot);
        }
        if (zone < 0) {
            return value;
        }
        String fraction = value.substring(dot + 1, zone);
        if (fraction.isEmpty()) {
            return value;
        }
        for (int index = 0; index < fraction.length(); index++) {
            if (!Character.isDigit(fraction.charAt(index))) {
                return value;
            }
        }
        String millis = (fraction + "000").substring(0, 3);
        return value.substring(0, dot + 1) + millis + value.substring(zone);
    }

    private static String requireText(JSONObject object, String key) throws JSONException {
        String value = optText(object, key);
        if (value.isEmpty()) {
            throw new JSONException(key + " 값이 없습니다.");
        }
        return value;
    }

    private static String optText(JSONObject object, String key) {
        if (object == null || object.isNull(key)) {
            return "";
        }
        return valueOrEmpty(object.optString(key, ""));
    }

    @Nullable
    private static Long optNullableLong(JSONObject object, String key) throws JSONException {
        if (object == null || !object.has(key) || object.isNull(key)) {
            return null;
        }
        return object.getLong(key);
    }

    private static String valueOrEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    static final class SessionSnapshot {
        private final String coreId;
        private final String legacyFirestoreId;
        private final String appointmentRequestId;
        private final String managerUserId;
        private final int currentStepOrder;
        private final int totalStepCount;
        private final String guideId;
        @Nullable
        private final Long guideRevision;
        private final boolean hasGuideSnapshot;
        private final List<GuideStep> guideSteps;
        private final String currentStepCode;
        private final boolean hasAdvanceDecision;
        private final boolean canAdvance;
        private final String blockedReason;
        private final SessionStatus status;
        private final String guardianUpdate;
        private final String locationSummary;
        private final String fieldPhotoNote;
        private final String medicationNote;
        private final String pharmacySummary;
        private final boolean preConsultationConfirmed;
        private final boolean prescriptionCollected;
        private final boolean pharmacyCompleted;
        private final boolean medicationGuidanceCompleted;
        private final boolean liveLocationSharingActive;
        private final String liveLocationSharingStartedAt;
        private final String locationAlertStage;
        private final String locationAlertSentAt;
        private final long version;
        private final String careEndedAt;
        private final String managerJournal;
        private final String reportGenerationStatus;
        private final int reportGenerationAttempts;
        private final String reportGenerationLastError;
        private final String reportGenerationUpdatedAt;
        private final List<CompanionSessionArtifact> artifacts;

        private SessionSnapshot(
                String coreId,
                String legacyFirestoreId,
                String appointmentRequestId,
                String managerUserId,
                int currentStepOrder,
                int totalStepCount,
                String guideId,
                @Nullable Long guideRevision,
                boolean hasGuideSnapshot,
                List<GuideStep> guideSteps,
                String currentStepCode,
                boolean hasAdvanceDecision,
                boolean canAdvance,
                String blockedReason,
                SessionStatus status,
                String guardianUpdate,
                String locationSummary,
                String fieldPhotoNote,
                String medicationNote,
                String pharmacySummary,
                boolean preConsultationConfirmed,
                boolean prescriptionCollected,
                boolean pharmacyCompleted,
                boolean medicationGuidanceCompleted,
                boolean liveLocationSharingActive,
                String liveLocationSharingStartedAt,
                String locationAlertStage,
                String locationAlertSentAt,
                long version,
                String careEndedAt,
                String managerJournal,
                String reportGenerationStatus,
                int reportGenerationAttempts,
                String reportGenerationLastError,
                String reportGenerationUpdatedAt,
                List<CompanionSessionArtifact> artifacts
        ) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.appointmentRequestId = appointmentRequestId;
            this.managerUserId = managerUserId;
            this.currentStepOrder = currentStepOrder;
            this.totalStepCount = totalStepCount;
            this.guideId = guideId;
            this.guideRevision = guideRevision;
            this.hasGuideSnapshot = hasGuideSnapshot;
            this.guideSteps = new ArrayList<>(guideSteps);
            this.currentStepCode = currentStepCode;
            this.hasAdvanceDecision = hasAdvanceDecision;
            this.canAdvance = canAdvance;
            this.blockedReason = blockedReason;
            this.status = status;
            this.guardianUpdate = guardianUpdate;
            this.locationSummary = locationSummary;
            this.fieldPhotoNote = fieldPhotoNote;
            this.medicationNote = medicationNote;
            this.pharmacySummary = pharmacySummary;
            this.preConsultationConfirmed = preConsultationConfirmed;
            this.prescriptionCollected = prescriptionCollected;
            this.pharmacyCompleted = pharmacyCompleted;
            this.medicationGuidanceCompleted = medicationGuidanceCompleted;
            this.liveLocationSharingActive = liveLocationSharingActive;
            this.liveLocationSharingStartedAt = liveLocationSharingStartedAt;
            this.locationAlertStage = locationAlertStage;
            this.locationAlertSentAt = locationAlertSentAt;
            this.version = version;
            this.careEndedAt = careEndedAt;
            this.managerJournal = managerJournal;
            this.reportGenerationStatus = reportGenerationStatus;
            this.reportGenerationAttempts = reportGenerationAttempts;
            this.reportGenerationLastError = reportGenerationLastError;
            this.reportGenerationUpdatedAt = reportGenerationUpdatedAt;
            this.artifacts = new ArrayList<>(artifacts);
        }

        String getExternalId() {
            return legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId;
        }

        String getCoreAppointmentId() {
            return appointmentRequestId;
        }

        String getCoreId() {
            return coreId;
        }

        SessionStatus getStatus() {
            return status;
        }

        boolean hasCareEnded() {
            return parseInstantMillis(careEndedAt) > 0L
                    || status == SessionStatus.CARE_ENDED
                    || status == SessionStatus.COMPLETED;
        }

        boolean requiresReportRetry() {
            return "FAILED".equals(reportGenerationStatus)
                    || "PENDING".equals(reportGenerationStatus);
        }

        int getTotalStepCount() {
            return totalStepCount;
        }

        boolean hasGuideSnapshot() {
            return hasGuideSnapshot;
        }

        List<GuideStep> getGuideSteps() {
            return new ArrayList<>(guideSteps);
        }

        @Nullable
        HospitalGuide toHospitalGuide(String hospitalName, String departmentName) {
            if (!hasGuideSnapshot) {
                return null;
            }
            return new HospitalGuide(
                    guideId,
                    guideRevision,
                    valueOrEmpty(hospitalName),
                    valueOrEmpty(departmentName),
                    new ArrayList<>(guideSteps));
        }

        CompanionSession merge(
                @Nullable CompanionSession legacySession,
                @Nullable String legacyAppointmentId
        ) {
            CompanionSession result = legacySession;
            if (result == null) {
                result = new CompanionSession(
                        getExternalId(),
                        valueOrEmpty(legacyAppointmentId).isEmpty()
                                ? appointmentRequestId
                                : valueOrEmpty(legacyAppointmentId),
                        managerUserId,
                        currentStepOrder,
                        status,
                        guardianUpdate,
                        locationSummary,
                        fieldPhotoNote,
                        medicationNote,
                        pharmacySummary,
                        pharmacyCompleted);
            } else {
                result.setCurrentStepOrder(currentStepOrder);
                result.setStatus(status);
                result.setGuardianUpdate(guardianUpdate);
                result.setLocationSummary(locationSummary);
                result.setFieldPhotoNote(fieldPhotoNote);
                result.setMedicationNote(medicationNote);
                result.setPharmacySummary(pharmacySummary);
                result.setPharmacyCompleted(pharmacyCompleted);
            }
            result.setPrescriptionCollected(prescriptionCollected);
            result.setPreConsultationConfirmed(preConsultationConfirmed);
            result.setMedicationGuidanceCompleted(medicationGuidanceCompleted);
            result.setRealtimeSessionId(coreId);
            result.updateLiveLocationSharing(
                    liveLocationSharingActive,
                    parseInstantMillis(liveLocationSharingStartedAt));
            result.setLocationAlertStage(CompanionLocationAlertStage.fromValue(locationAlertStage));
            result.setLocationAlertSentAtMillis(parseInstantMillis(locationAlertSentAt));
            result.applyServerGuideProgress(
                    currentStepCode,
                    hasAdvanceDecision,
                    canAdvance,
                    blockedReason);
            result.applyCompletionState(
                    parseInstantMillis(careEndedAt),
                    managerJournal,
                    reportGenerationStatus,
                    reportGenerationAttempts,
                    reportGenerationLastError,
                    parseInstantMillis(reportGenerationUpdatedAt),
                    artifacts);
            return result;
        }
    }

    static final class RealtimeSnapshot {
        private final String realtimeTopic;
        private final List<CompanionChatMessage> messages;
        private final List<ReadReceiptSnapshot> readReceipts;
        private final List<CompanionLocationHistoryEntry> locations;

        private RealtimeSnapshot(
                String realtimeTopic,
                List<CompanionChatMessage> messages,
                List<ReadReceiptSnapshot> readReceipts,
                List<CompanionLocationHistoryEntry> locations
        ) {
            this.realtimeTopic = realtimeTopic;
            this.messages = messages;
            this.readReceipts = readReceipts;
            this.locations = locations;
        }

        String getRealtimeTopic() {
            return realtimeTopic;
        }

        String lastMessageId() {
            return messages.isEmpty() ? "" : messages.get(messages.size() - 1).getId();
        }

        void applyTo(CompanionSession session) {
            session.replaceChatMessages(messages);
            List<CompanionLocationHistoryEntry> summarizedLocations = new ArrayList<>();
            for (CompanionLocationHistoryEntry location : locations) {
                summarizedLocations.add(new CompanionLocationHistoryEntry(
                        location.getLatitude(),
                        location.getLongitude(),
                        session.getLocationSummary(),
                        location.getCapturedAtMillis()));
            }
            session.replaceSharedLocationHistory(summarizedLocations);
            session.clearChatReadState();
            for (ReadReceiptSnapshot receipt : readReceipts) {
                session.markChatRead(receipt.role, receipt.readAtMillis);
            }
        }
    }

    private static final class ReadReceiptSnapshot {
        private final UserRole role;
        private final long readAtMillis;

        private ReadReceiptSnapshot(UserRole role, long readAtMillis) {
            this.role = role;
            this.readAtMillis = readAtMillis;
        }
    }

    static final class ReportSnapshot {
        private final String coreId;
        private final String legacyFirestoreId;
        private final String companionSessionId;
        private final String summary;
        private final String treatmentNotes;
        private final String medicationNotes;
        private final String medicationName;
        private final String medicationChangeSummary;
        private final String medicationScheduleNote;
        @Nullable
        private final MedicationComparisonDecision medicationComparisonDecision;
        private final String medicationComparisonNote;
        private final String nextVisitAt;

        ReportSnapshot(
                String coreId,
                String legacyFirestoreId,
                String companionSessionId,
                String summary,
                String treatmentNotes,
                String medicationNotes,
                String medicationName,
                String medicationChangeSummary,
                String medicationScheduleNote,
                @Nullable MedicationComparisonDecision medicationComparisonDecision,
                String medicationComparisonNote,
                String nextVisitAt
        ) {
            this.coreId = coreId;
            this.legacyFirestoreId = legacyFirestoreId;
            this.companionSessionId = companionSessionId;
            this.summary = summary;
            this.treatmentNotes = treatmentNotes;
            this.medicationNotes = medicationNotes;
            this.medicationName = medicationName;
            this.medicationChangeSummary = medicationChangeSummary;
            this.medicationScheduleNote = medicationScheduleNote;
            this.medicationComparisonDecision = medicationComparisonDecision;
            this.medicationComparisonNote = medicationComparisonNote;
            this.nextVisitAt = nextVisitAt;
        }

        SessionReport toModel(String externalSessionId) {
            return new SessionReport(
                    legacyFirestoreId.isEmpty() ? coreId : legacyFirestoreId,
                    valueOrEmpty(externalSessionId).isEmpty()
                            ? companionSessionId
                            : externalSessionId,
                    summary,
                    treatmentNotes,
                    medicationNotes,
                    medicationName,
                    medicationChangeSummary,
                    medicationScheduleNote,
                    medicationComparisonDecision,
                    medicationComparisonNote,
                    nextVisitAt);
        }
    }
}
