package com.example.bodeul.data.firebase;

import androidx.annotation.Nullable;

import com.example.bodeul.data.ManagerDocumentReferencePolicy;
import com.example.bodeul.data.ManagerDocumentUploadPolicy;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpReviewRating;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentFollowUpSupportEscalationStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionChatMessage;
import com.example.bodeul.domain.model.CompanionLocationAlertStage;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.HospitalGuideFallbackFactory;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.ManagerDocumentFileMetadata;
import com.example.bodeul.domain.model.ManagerDocumentFileType;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEntry;
import com.example.bodeul.domain.model.ManagerDocumentHistoryEventType;
import com.example.bodeul.domain.model.ManagerDocumentOverview;
import com.example.bodeul.domain.model.ManagerDocumentStatus;
import com.example.bodeul.domain.model.ManagerHomeProfile;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryCategory;
import com.example.bodeul.domain.model.SupportInquiryStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.SafeEnumParser;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore에 저장된 세션, 요청, 가이드, 리포트를 조합해 매니저 화면을 구성한다.
 */
public class FirebaseManagerRepository implements ManagerRepository {
    private final FirebaseFirestore firestore;

    public FirebaseManagerRepository(FirebaseFirestore firestore) {
        this.firestore = firestore;
    }

    @Override
    public void getManagerDashboard(String managerUserId, RepositoryCallback<ManagerDashboard> callback) {
        // 세션 문서를 기준으로 관련 사용자, 요청, 가이드, 리포트를 병렬로 읽어 대시보드를 완성한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("companionSessions 데이터 형식을 확인해 주세요.");
                    return;
                }

                firestore.collection("appointmentRequests")
                        .document(session.getAppointmentRequestId())
                        .get()
                        .addOnSuccessListener(requestSnapshot -> {
                            AppointmentRequest request = toAppointmentRequest(requestSnapshot);
                            if (request == null) {
                                callback.onError("appointmentRequests 데이터를 찾지 못했습니다.");
                                return;
                            }

                            Task<DocumentSnapshot> managerTask = firestore.collection("users")
                                    .document(managerUserId)
                                    .get();
                            Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                                    .whereEqualTo("hospitalName", request.getHospitalName())
                                    .get();
                            Task<QuerySnapshot> reportTask = firestore.collection("sessionReports")
                                    .whereEqualTo("sessionId", session.getId())
                                    .limit(1)
                                    .get();

                            List<Task<?>> tasks = Arrays.asList(
                                    managerTask,
                                    guideTask,
                                    reportTask
                            );

                            Tasks.whenAllSuccess(tasks)
                                    .addOnSuccessListener(results -> {
                                        User manager = toUser((DocumentSnapshot) results.get(0));
                                        User patient = toParticipantUser(
                                                request.getPatientUserId(),
                                                UserRole.PATIENT,
                                                request.getPatientName(),
                                                request.getPatientEmail(),
                                                request.getPatientPhone()
                                        );
                                        User guardian = toParticipantUser(
                                                request.getGuardianUserId(),
                                                UserRole.GUARDIAN,
                                                request.getGuardianName(),
                                                request.getGuardianEmail(),
                                                request.getGuardianPhone()
                                        );
                                        HospitalGuide guide = HospitalGuideFallbackFactory.fallbackIfMissing(
                                                findGuide(
                                                        (QuerySnapshot) results.get(1),
                                                        request.getDepartmentName()
                                                ),
                                                request.getHospitalName(),
                                                request.getDepartmentName()
                                        );
                                        session.setCurrentStepCode(
                                                resolveCurrentStepCode(
                                                        guide.getSteps(),
                                                        session.getCurrentStepOrder())
                                        );
                                        SessionReport report = toReport((QuerySnapshot) results.get(2));

                                        if (manager == null || patient == null || guardian == null) {
                                            callback.onError("Firebase 연결 데이터(users)를 확인해 주세요.");
                                            return;
                                        }

                                        callback.onSuccess(new ManagerDashboard(
                                                manager,
                                                patient,
                                                guardian,
                                                request,
                                                session,
                                                guide,
                                                report
                                        ));
                                    })
                                    .addOnFailureListener(exception ->
                                            callback.onError("매니저 데이터를 불러오지 못했습니다."));
                        })
                        .addOnFailureListener(exception ->
                                callback.onError("예약 데이터를 불러오지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void advanceCurrentStep(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // 단계 이동은 현재 세션 문서와 전체 단계 수를 모두 확인한 뒤 대시보드를 다시 읽는다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard dashboard) {
                        if (!ManagerRepository.matchesAdvanceExpectation(
                                dashboard.getSession(),
                                expectedSessionId,
                                expectedStepCode)) {
                            callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                            return;
                        }
                        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
                        int currentStep = dashboard.getSession().getCurrentStepOrder();
                        if (currentStep >= totalSteps) {
                            callback.onError("마지막 단계입니다. 리포트를 전송해 주세요.");
                            return;
                        }

                        int nextStep = currentStep + 1;
                        String expectedStatus = dashboard.getSession().getStatus().name();
                        String expectedAppointmentRequestId = dashboard.getSession().getAppointmentRequestId();
                        String expectedAppointmentStatus = dashboard.getAppointmentRequest().getStatus().name();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("currentStepOrder", nextStep);
                        updates.put("currentStatus", resolveStepStatus(nextStep, totalSteps).name());
                        updates.put("updatedAt", FieldValue.serverTimestamp());

                        DocumentReference appointmentReference = firestore.collection("appointmentRequests")
                                .document(dashboard.getAppointmentRequest().getId());
                        // 확인창을 연 뒤 단계가 바뀌었으면 이전 화면의 완료 요청을 적용하지 않는다.
                        firestore.runTransaction(transaction -> {
                                    DocumentSnapshot latestSession = transaction.get(sessionSnapshot.getReference());
                                    DocumentSnapshot latestAppointment = transaction.get(appointmentReference);
                                    if (!matchesFreshAdvanceState(
                                            latestSession.getLong("currentStepOrder"),
                                            latestSession.getString("currentStatus"),
                                            latestSession.getString("managerUserId"),
                                            latestSession.getString("appointmentRequestId"),
                                            currentStep,
                                            expectedStatus,
                                            managerUserId,
                                            expectedAppointmentRequestId
                                    )) {
                                        return false;
                                    }
                                    if (!normalizeAdvanceValue(latestAppointment.getString("status"))
                                            .equals(normalizeAdvanceValue(expectedAppointmentStatus))) {
                                        return false;
                                    }

                                    transaction.update(sessionSnapshot.getReference(), updates);
                                    transaction.update(
                                            appointmentReference,
                                            "status",
                                            AppointmentStatus.IN_PROGRESS.name()
                                    );
                                    return true;
                                })
                                .addOnSuccessListener(advanced -> {
                                    if (!Boolean.TRUE.equals(advanced)) {
                                        callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                                        return;
                                    }
                                    getManagerDashboard(managerUserId, callback);
                                })
                                .addOnFailureListener(exception ->
                                        callback.onError("다음 단계로 이동하지 못했습니다."));
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    static boolean matchesFreshAdvanceState(
            @Nullable Long latestStepOrder,
            @Nullable String latestStatus,
            @Nullable String latestManagerUserId,
            @Nullable String latestAppointmentRequestId,
            int expectedStepOrder,
            String expectedStatus,
            String expectedManagerUserId,
            String expectedAppointmentRequestId
    ) {
        return latestStepOrder != null
                && latestStepOrder.intValue() == expectedStepOrder
                && normalizeAdvanceValue(latestStatus).equals(normalizeAdvanceValue(expectedStatus))
                && normalizeAdvanceValue(latestManagerUserId).equals(normalizeAdvanceValue(expectedManagerUserId))
                && normalizeAdvanceValue(latestAppointmentRequestId)
                .equals(normalizeAdvanceValue(expectedAppointmentRequestId));
    }

    private static String normalizeAdvanceValue(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    static String resolveCurrentStepCode(
            @Nullable List<GuideStep> steps,
            int currentStepOrder
    ) {
        if (steps == null) {
            return "";
        }
        for (GuideStep step : steps) {
            if (step != null && step.getOrder() == currentStepOrder) {
                return normalizeAdvanceValue(step.getCode());
            }
        }
        return "";
    }

    @Override
    public void saveGuardianUpdate(String managerUserId, String guardianUpdate, RepositoryCallback<ManagerDashboard> callback) {
        // 보호자 공유 메시지는 companionSessions 문서의 단일 필드로 관리한다.
        updateSessionField(managerUserId, "guardianUpdate", guardianUpdate, callback);
    }

    @Override
    public void shareCurrentLocation(
            String managerUserId,
            double latitude,
            double longitude,
            String locationSummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = FirebaseCompanionSessionMapper.toSession(
                        sessionSnapshot,
                        UserRole.MANAGER
                );
                if (session == null) {
                    callback.onError("세션 정보를 찾지 못했습니다.");
                    return;
                }

                long capturedAtMillis = System.currentTimeMillis();
                String normalizedSummary = normalizeText(locationSummary);
                session.recordSharedLocation(latitude, longitude, normalizedSummary, capturedAtMillis);

                Map<String, Object> updates = new HashMap<>();
                updates.put("locationSummary", normalizedSummary);
                updates.put("sharedLatitude", latitude);
                updates.put("sharedLongitude", longitude);
                updates.put("sharedLocationUpdatedAt", FieldValue.serverTimestamp());
                updates.put(
                        "sharedLocationHistory",
                        FirebaseCompanionSessionMapper.toSharedLocationHistoryPayload(
                                session.getSharedLocationHistory()
                        )
                );
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("실시간 위치를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void updateLiveLocationSharingState(
            String managerUserId,
            boolean active,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("liveLocationSharingActive", active);
                if (active) {
                    if (!Boolean.TRUE.equals(sessionSnapshot.getBoolean("liveLocationSharingActive"))) {
                        updates.put("liveLocationSharingStartedAt", FieldValue.serverTimestamp());
                    }
                } else {
                    updates.put("liveLocationSharingStartedAt", FieldValue.delete());
                }
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("실시간 위치 공유 상태를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void saveLocationSummary(String managerUserId, String locationSummary, RepositoryCallback<ManagerDashboard> callback) {
        // 위치 공유 메모를 세션 문서의 별도 필드에 저장한다.
        updateSessionField(managerUserId, "locationSummary", locationSummary, callback);
    }

    @Override
    public void saveFieldPhotoNote(String managerUserId, String fieldPhotoNote, RepositoryCallback<ManagerDashboard> callback) {
        // 현장 사진이나 서류 확인 메모를 다음 화면에서도 재사용할 수 있게 저장한다.
        updateSessionField(managerUserId, "fieldPhotoNote", fieldPhotoNote, callback);
    }

    @Override
    public void saveMedicationNote(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String medicationNote,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // 복약 메모를 같은 세션 문서에 저장해 리포트 작성 전에 누적한다.
        Map<String, Object> updates = new HashMap<>();
        updates.put("medicationNote", medicationNote);
        updateMedicationSessionFields(
                managerUserId, expectedSessionId, expectedStepCode, updates, callback);
    }

    @Override
    public void savePharmacySummary(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            String pharmacySummary,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("pharmacySummary", pharmacySummary);
        updateMedicationSessionFields(
                managerUserId, expectedSessionId, expectedStepCode, updates, callback);
    }

    @Override
    public void updatePreConsultationConfirmed(
            String managerUserId,
            boolean preConsultationConfirmed,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        callback.onError("진료 전 확인 저장에는 Core API 연결이 필요합니다.");
    }

    @Override
    public void updatePrescriptionCollected(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean prescriptionCollected,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("prescriptionCollected", prescriptionCollected);
        updateMedicationSessionFields(
                managerUserId, expectedSessionId, expectedStepCode, updates, callback);
    }

    @Override
    public void updatePharmacyCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean pharmacyCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("pharmacyCompleted", pharmacyCompleted);
        updateMedicationSessionFields(
                managerUserId, expectedSessionId, expectedStepCode, updates, callback);
    }

    @Override
    public void updateMedicationGuidanceCompleted(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            boolean medicationGuidanceCompleted,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("medicationGuidanceCompleted", medicationGuidanceCompleted);
        updateMedicationSessionFields(
                managerUserId, expectedSessionId, expectedStepCode, updates, callback);
    }

    @Override
    public void getManagerHomeProfile(String managerUserId, RepositoryCallback<ManagerHomeProfile> callback) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError("users 컬렉션에서 매니저 정보를 찾지 못했습니다.");
                        return;
                    }
                    callback.onSuccess(toManagerHomeProfile(documentSnapshot));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 서류 요약 정보를 불러오지 못했습니다."));
    }

    @Override
    public void getManagerDocumentOverview(
            String managerUserId,
            RepositoryCallback<ManagerDocumentOverview> callback
    ) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null) {
                        callback.onError("매니저 프로필 정보를 불러오지 못했습니다.");
                        return;
                    }

                    callback.onSuccess(new ManagerDocumentOverview(
                            manager,
                            toManagerHomeProfile(documentSnapshot),
                            toManagerDocumentHistory(documentSnapshot)
                    ));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 프로필 정보를 불러오지 못했습니다."));
    }

    @Override
    public void getManagerHistoryDetails(
            String managerUserId,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        Task<DocumentSnapshot> managerTask = firestore.collection("users")
                .document(managerUserId)
                .get();
        Task<QuerySnapshot> sessionTask = firestore.collection("companionSessions")
                .whereEqualTo("managerUserId", managerUserId)
                .get();

        List<Task<?>> tasks = Arrays.asList(managerTask, sessionTask);
        Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    User manager = toUser((DocumentSnapshot) results.get(0));
                    if (manager == null) {
                        callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다.");
                        return;
                    }

                    List<CompanionSession> completedSessions = new ArrayList<>();
                    QuerySnapshot querySnapshot = (QuerySnapshot) results.get(1);
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        CompanionSession session = toSession(documentSnapshot);
                        if (session != null && session.getStatus() == SessionStatus.COMPLETED) {
                            completedSessions.add(session);
                        }
                    }

                    if (completedSessions.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    loadManagerHistoryDetailsSequentially(
                            manager,
                            completedSessions,
                            0,
                            new ArrayList<>(),
                            callback
                    );
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다."));
    }

    @Override
    public void saveManagerDocumentSummary(
            String managerUserId,
            String documentSummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        String normalizedSummary = normalizeText(documentSummary);
        DocumentReference managerReference = firestore.collection("users").document(managerUserId);
        firestore.runTransaction(transaction -> {
                    DocumentSnapshot snapshot = transaction.get(managerReference);
                    if (!managerDocumentSummaryStateChanged(
                            snapshot.getString("managerDocumentSummary"),
                            snapshot.getString("managerDocumentStatus"),
                            normalizedSummary
                    )) {
                        return false;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("managerDocumentSummary", normalizedSummary);
                    putManagerDocumentSummaryState(updates, normalizedSummary);
                    transaction.update(managerReference, updates);
                    return true;
                })
                .addOnSuccessListener(unused ->
                        onManagerDocumentSummarySaved(managerUserId, callback))
                .addOnFailureListener(exception ->
                        callback.onError("서류 등록 정보를 저장하지 못했습니다."));
    }

    @Override
    public void saveManagerDocumentFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        if (documentFileMetadata == null || documentFileMetadata.isEmpty()) {
            callback.onError("업로드한 서류 파일 정보를 확인하지 못했습니다.");
            return;
        }
        String fileTypeError = ManagerDocumentUploadPolicy.validateFileType(
                documentFileMetadata.getFileType()
        );
        if (fileTypeError != null) {
            callback.onError(fileTypeError);
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("매니저 계정을 확인하지 못했습니다.");
                        return;
                    }

                    String documentSummary = normalizeText(documentSnapshot.getString("managerDocumentSummary"));
                    if (documentSummary.isEmpty()) {
                        callback.onError("서류 요약을 먼저 저장한 뒤 원본 파일을 올려 주세요.");
                        return;
                    }

                    long uploadedAtMillis = documentFileMetadata.getUploadedAtMillis() > 0L
                            ? documentFileMetadata.getUploadedAtMillis()
                            : System.currentTimeMillis();
                    Map<String, Object> updates = new HashMap<>();
                    putCanonicalQualificationReplacement(
                            updates,
                            documentFileMetadata.getFileType()
                    );
                    String fileKeyPrefix = "managerDocumentFiles."
                            + documentFileMetadata.getFileType().getStorageKey();
                    updates.put(fileKeyPrefix + ".fullPath", documentFileMetadata.getFullPath());
                    updates.put(fileKeyPrefix + ".fileName", documentFileMetadata.getFileName());
                    updates.put(fileKeyPrefix + ".contentType", documentFileMetadata.getContentType());
                    updates.put(fileKeyPrefix + ".previewUri", documentFileMetadata.getPreviewUri());
                    updates.put(fileKeyPrefix + ".uploadedAt", uploadedAtMillis);
                    updates.put(
                            "managerDocumentFilePaths." + documentFileMetadata.getFileType().getStorageKey(),
                            documentFileMetadata.getFullPath()
                    );
                    String legacyPathKey = resolveLegacyDocumentStoragePathKey(
                            documentFileMetadata.getFileType()
                    );
                    if (legacyPathKey != null) {
                        updates.put(legacyPathKey, documentFileMetadata.getFullPath());
                    }
                    putManagerDocumentSubmissionState(updates);

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("원본 서류 파일 정보를 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 서류 정보를 불러오지 못했습니다."));
    }

    @Override
    public void saveManagerDocumentDraftFileMetadata(
            String managerUserId,
            ManagerDocumentFileMetadata documentFileMetadata,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        if (documentFileMetadata == null || documentFileMetadata.isEmpty()) {
            callback.onError("업로드한 서류 파일 정보를 확인하지 못했습니다.");
            return;
        }
        String fileTypeError = ManagerDocumentUploadPolicy.validateFileType(
                documentFileMetadata.getFileType()
        );
        if (fileTypeError != null) {
            callback.onError(fileTypeError);
            return;
        }

        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onError("매니저 정보를 찾지 못했습니다.");
                        return;
                    }

                    long uploadedAtMillis = documentFileMetadata.getUploadedAtMillis() > 0L
                            ? documentFileMetadata.getUploadedAtMillis()
                            : System.currentTimeMillis();
                    Map<String, Object> updates = new HashMap<>();
                    putCanonicalQualificationReplacement(
                            updates,
                            documentFileMetadata.getFileType()
                    );
                    String fileKeyPrefix = "managerDocumentFiles."
                            + documentFileMetadata.getFileType().getStorageKey();
                    updates.put(fileKeyPrefix + ".fullPath", documentFileMetadata.getFullPath());
                    updates.put(fileKeyPrefix + ".fileName", documentFileMetadata.getFileName());
                    updates.put(fileKeyPrefix + ".contentType", documentFileMetadata.getContentType());
                    updates.put(fileKeyPrefix + ".previewUri", documentFileMetadata.getPreviewUri());
                    updates.put(fileKeyPrefix + ".uploadedAt", uploadedAtMillis);
                    updates.put(
                            "managerDocumentFilePaths." + documentFileMetadata.getFileType().getStorageKey(),
                            documentFileMetadata.getFullPath()
                    );
                    String legacyPathKey = resolveLegacyDocumentStoragePathKey(
                            documentFileMetadata.getFileType()
                    );
                    if (legacyPathKey != null) {
                        updates.put(legacyPathKey, documentFileMetadata.getFullPath());
                    }
                    ManagerDocumentStatus currentStatus = resolveManagerDocumentStatus(
                            documentSnapshot.getString("managerDocumentStatus"),
                            documentSnapshot.getString("managerDocumentSummary")
                    );
                    putManagerDocumentDraftState(
                            updates,
                            currentStatus,
                            normalizeText(documentSnapshot.getString("managerDocumentSummary"))
                    );

                    documentSnapshot.getReference()
                            .update(updates)
                            .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("원본 서류 파일 초안을 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 서류 정보를 불러오지 못했습니다."));
    }

    @Override
    public void saveManagerAvailabilitySummary(
            String managerUserId,
            String availabilitySummary,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        saveManagerHomeProfileField(
                managerUserId,
                "managerAvailabilitySummary",
                normalizeText(availabilitySummary),
                "managerAvailabilityUpdatedAt",
                "활동 가능 일정을 저장하지 못했습니다.",
                callback
        );
    }

    @Override
    public void submitSessionReport(
            String managerUserId,
            String summary,
            String treatmentNotes,
            String medicationNotes,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisitAt,
            RepositoryCallback<SessionReport> callback
    ) {
        // 리포트 저장은 sessionReports 생성과 세션 종료 처리를 함께 반영해야 한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null) {
                    callback.onError("세션 정보를 찾지 못했습니다.");
                    return;
                }

                Map<String, Object> reportDocument = new HashMap<>();
                reportDocument.put("sessionId", session.getId());
                reportDocument.put("summary", summary);
                reportDocument.put("treatmentNotes", treatmentNotes);
                reportDocument.put("medicationNotes", medicationNotes);
                reportDocument.put("medicationName", medicationName);
                reportDocument.put("medicationChangeSummary", medicationChangeSummary);
                reportDocument.put("medicationScheduleNote", medicationScheduleNote);
                reportDocument.put(
                        "medicationComparisonDecisionCode",
                        medicationComparisonDecision == null ? "" : medicationComparisonDecision.name()
                );
                reportDocument.put("medicationComparisonNote", medicationComparisonNote);
                reportDocument.put("nextVisitAt", nextVisitAt);
                reportDocument.put("createdAt", FieldValue.serverTimestamp());

                // 리포트 저장과 세션 종료 처리를 배치로 묶어 한 번에 반영한다.
                WriteBatch batch = firestore.batch();
                DocumentReference reportReference = firestore.collection("sessionReports")
                        .document("report-" + session.getId());

                batch.set(reportReference, reportDocument);
                batch.update(sessionSnapshot.getReference(), "currentStatus", SessionStatus.COMPLETED.name());
                batch.update(sessionSnapshot.getReference(), "medicationNote", medicationNotes);
                batch.update(sessionSnapshot.getReference(), "liveLocationSharingActive", false);
                batch.update(sessionSnapshot.getReference(), "liveLocationSharingStartedAt", FieldValue.delete());
                batch.update(sessionSnapshot.getReference(), "updatedAt", FieldValue.serverTimestamp());
                batch.update(
                        firestore.collection("appointmentRequests")
                                .document(session.getAppointmentRequestId()),
                        "status",
                        AppointmentStatus.COMPLETED.name()
                );

                batch.commit()
                        .addOnSuccessListener(unused -> callback.onSuccess(new SessionReport(
                                reportReference.getId(),
                                session.getId(),
                                summary,
                                treatmentNotes,
                                medicationNotes,
                                medicationName,
                                medicationChangeSummary,
                                medicationScheduleNote,
                                medicationComparisonDecision,
                                medicationComparisonNote,
                                nextVisitAt)))
                        .addOnFailureListener(exception ->
                                callback.onError("리포트를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void getSupportInquiries(
            String managerUserId,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firestore.collection("supportInquiries")
                .whereEqualTo("managerUserId", managerUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SupportInquiry> inquiries = new ArrayList<>();
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        SupportInquiry inquiry = toSupportInquiry(documentSnapshot);
                        if (inquiry != null) {
                            inquiries.add(inquiry);
                        }
                    }
                    inquiries.sort((left, right) ->
                            Long.compare(right.getCreatedAtMillis(), left.getCreatedAtMillis()));
                    callback.onSuccess(inquiries);
                })
                .addOnFailureListener(exception ->
                        callback.onError("문의 이력을 불러오지 못했습니다."));
    }

    @Override
    public void submitSupportInquiry(
            String managerUserId,
            SupportInquiryCategory category,
            String title,
            String body,
            RepositoryCallback<List<SupportInquiry>> callback
    ) {
        firestore.collection("users")
                .document(managerUserId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    User manager = toUser(documentSnapshot);
                    if (manager == null || manager.getRole() != UserRole.MANAGER) {
                        callback.onError("매니저 계정을 확인하지 못했습니다.");
                        return;
                    }

                    Map<String, Object> inquiryDocument = new HashMap<>();
                    inquiryDocument.put("managerUserId", managerUserId);
                    inquiryDocument.put("managerName", normalizeText(manager.getName()));
                    inquiryDocument.put("category", category == null
                            ? SupportInquiryCategory.MATCHING.getValue()
                            : category.getValue());
                    inquiryDocument.put("title", normalizeText(title));
                    inquiryDocument.put("body", normalizeText(body));
                    inquiryDocument.put("status", SupportInquiryStatus.RECEIVED.name());
                    inquiryDocument.put("responseText", "");
                    inquiryDocument.put("respondedByName", "");
                    inquiryDocument.put("respondedAt", 0L);
                    inquiryDocument.put("createdAt", FieldValue.serverTimestamp());

                    firestore.collection("supportInquiries")
                            .add(inquiryDocument)
                            .addOnSuccessListener(unused -> getSupportInquiries(managerUserId, callback))
                            .addOnFailureListener(exception ->
                                    callback.onError("문의 내용을 저장하지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 정보를 확인하지 못했습니다."));
    }

    @Override
    public void sendCompanionChatMessage(
            String managerUserId,
            String message,
            List<CompanionChatAttachment> attachments,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        String normalizedMessage = normalizeText(message);
        if (normalizedMessage.isEmpty() && (attachments == null || attachments.isEmpty())) {
            callback.onError("메시지나 첨부 파일 중 하나는 함께 보내야 합니다.");
            return;
        }

        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("chatMessages", FieldValue.arrayUnion(
                        buildChatMessagePayload(UserRole.MANAGER, normalizedMessage, attachments)
                ));
                updates.put(resolveChatReadField(UserRole.MANAGER), FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updates)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("메시지를 전송하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void markCompanionChatRead(String managerUserId) {
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updates = new HashMap<>();
                updates.put(resolveChatReadField(UserRole.MANAGER), FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                sessionSnapshot.getReference().update(updates);
            }

            @Override
            public void onError(String message) {
                // 읽음 처리 실패가 화면 흐름을 막지 않도록 한다.
            }
        });
    }

    @Override
    public void saveCompanionLocationAlert(String managerUserId, CompanionLocationAlertStage stage) {
        if (stage == null || stage == CompanionLocationAlertStage.NONE) {
            return;
        }
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                CompanionSession session = toSession(sessionSnapshot);
                if (session == null || !session.getLocationAlertStage().canAdvanceTo(stage)) {
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("locationAlertStage", stage.getValue());
                updates.put("locationAlertSentAt", FieldValue.serverTimestamp());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                sessionSnapshot.getReference().update(updates);
            }

            @Override
            public void onError(String message) {
                // 자동 위치 알림 실패가 위치 공유 흐름을 막지 않도록 한다.
            }
        });
    }

    @Override
    public boolean isFirebaseBacked() {
        return true;
    }

    private void onManagerDocumentSummarySaved(
            String managerUserId,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        getManagerHomeProfile(managerUserId, callback);
    }

    private void loadManagerHistoryDetailsSequentially(
            User manager,
            List<CompanionSession> sessions,
            int index,
            List<AppointmentRequestDetail> results,
            RepositoryCallback<List<AppointmentRequestDetail>> callback
    ) {
        if (index >= sessions.size()) {
            results.sort((left, right) -> right.getAppointmentRequest()
                    .getAppointmentAt()
                    .compareTo(left.getAppointmentRequest().getAppointmentAt()));
            callback.onSuccess(results);
            return;
        }

        CompanionSession session = sessions.get(index);
        firestore.collection("appointmentRequests")
                .document(session.getAppointmentRequestId())
                .get()
                .addOnSuccessListener(requestSnapshot -> {
                    AppointmentRequest request = toAppointmentRequest(requestSnapshot);
                    if (request == null) {
                        loadManagerHistoryDetailsSequentially(
                                manager,
                                sessions,
                                index + 1,
                                results,
                                callback
                        );
                        return;
                    }

                    Task<QuerySnapshot> guideTask = firestore.collection("hospitalGuides")
                            .whereEqualTo("hospitalName", request.getHospitalName())
                            .get();
                    Task<QuerySnapshot> reportTask = firestore.collection("sessionReports")
                            .whereEqualTo("sessionId", session.getId())
                            .limit(1)
                            .get();
                    Task<DocumentSnapshot> followUpTask = firestore.collection("appointmentFollowUps")
                            .document(request.getId())
                            .get();

                    Tasks.whenAllSuccess(guideTask, reportTask, followUpTask)
                            .addOnSuccessListener(historyResults -> {
                                User patient = toParticipantUser(
                                        request.getPatientUserId(),
                                        UserRole.PATIENT,
                                        request.getPatientName(),
                                        request.getPatientEmail(),
                                        request.getPatientPhone()
                                );
                                User guardian = toParticipantUser(
                                        request.getGuardianUserId(),
                                        UserRole.GUARDIAN,
                                        request.getGuardianName(),
                                        request.getGuardianEmail(),
                                        request.getGuardianPhone()
                                );
                                HospitalGuide hospitalGuide = HospitalGuideFallbackFactory.fallbackIfMissing(
                                        findGuide(
                                                (QuerySnapshot) historyResults.get(0),
                                                request.getDepartmentName()
                                        ),
                                        request.getHospitalName(),
                                        request.getDepartmentName()
                                );
                                SessionReport report = toReport((QuerySnapshot) historyResults.get(1));
                                AppointmentFollowUpRecord followUpRecord = toAppointmentFollowUpRecord(
                                        (DocumentSnapshot) historyResults.get(2),
                                        request.getId()
                                );

                                results.add(new AppointmentRequestDetail(
                                        request,
                                        patient,
                                        guardian,
                                        manager,
                                        session,
                                        report,
                                        hospitalGuide,
                                        followUpRecord
                                ));
                                loadManagerHistoryDetailsSequentially(
                                        manager,
                                        sessions,
                                        index + 1,
                                        results,
                                        callback
                                );
                            })
                            .addOnFailureListener(exception ->
                                    callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다."));
                })
                .addOnFailureListener(exception ->
                        callback.onError("매니저 과거 동행 이력을 불러오지 못했습니다."));
    }

    private void saveManagerHomeProfileField(
            String managerUserId,
            String key,
            String value,
            String updatedAtKey,
            String errorMessage,
            RepositoryCallback<ManagerHomeProfile> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(key, value);
        updates.put(updatedAtKey, FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(managerUserId)
                .update(updates)
                .addOnSuccessListener(unused -> getManagerHomeProfile(managerUserId, callback))
                .addOnFailureListener(exception -> callback.onError(errorMessage));
    }

    private void updateSessionField(
            String managerUserId,
            String key,
            String value,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(key, value);
        updateSessionFields(managerUserId, updates, callback);
    }

    private void updateMedicationSessionFields(
            String managerUserId,
            String expectedSessionId,
            String expectedStepCode,
            Map<String, Object> updates,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        getManagerDashboard(managerUserId, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard dashboard) {
                CompanionSession session = dashboard == null ? null : dashboard.getSession();
                if (!ManagerRepository.matchesMedicationExpectation(
                        session, expectedSessionId, expectedStepCode)) {
                    callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                    return;
                }

                int expectedStepOrder = session.getCurrentStepOrder();
                String expectedStatus = session.getStatus().name();
                DocumentReference sessionReference = firestore.collection("companionSessions")
                        .document(expectedSessionId);
                Map<String, Object> updatesWithTimestamp = new HashMap<>(updates);
                updatesWithTimestamp.put("updatedAt", FieldValue.serverTimestamp());

                firestore.runTransaction(transaction -> {
                            DocumentSnapshot latest = transaction.get(sessionReference);
                            Long latestStepOrder = latest.getLong("currentStepOrder");
                            boolean stillCurrent = latest.exists()
                                    && latestStepOrder != null
                                    && latestStepOrder.intValue() == expectedStepOrder
                                    && normalizeAdvanceValue(latest.getString("managerUserId"))
                                    .equals(normalizeAdvanceValue(managerUserId))
                                    && normalizeAdvanceValue(latest.getString("currentStatus"))
                                    .equals(normalizeAdvanceValue(expectedStatus));
                            if (!stillCurrent) {
                                return false;
                            }
                            transaction.update(sessionReference, updatesWithTimestamp);
                            return true;
                        })
                        .addOnSuccessListener(updated -> {
                            if (!Boolean.TRUE.equals(updated)) {
                                callback.onError(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                                return;
                            }
                            getManagerDashboard(managerUserId, callback);
                        })
                        .addOnFailureListener(exception ->
                                callback.onError("복약 확인 정보를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void updateSessionFields(
            String managerUserId,
            Map<String, Object> updates,
            RepositoryCallback<ManagerDashboard> callback
    ) {
        // 세션 메모와 단계 상태를 저장한 뒤 대시보드를 다시 읽어 화면을 갱신한다.
        loadSessionDocument(managerUserId, new RepositoryCallback<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot sessionSnapshot) {
                Map<String, Object> updatesWithTimestamp = new HashMap<>(updates);
                updatesWithTimestamp.put("updatedAt", FieldValue.serverTimestamp());

                sessionSnapshot.getReference()
                        .update(updatesWithTimestamp)
                        .addOnSuccessListener(unused -> getManagerDashboard(managerUserId, callback))
                        .addOnFailureListener(exception ->
                                callback.onError("세션 정보를 저장하지 못했습니다."));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private void loadSessionDocument(String managerUserId, RepositoryCallback<DocumentSnapshot> callback) {
        // 현재 구현은 매니저의 진행 중 동행 세션 1건을 기준으로 화면을 구성한다.
        firestore.collection("companionSessions")
                .whereEqualTo("managerUserId", managerUserId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
                        CompanionSession session = toSession(documentSnapshot);
                        if (session != null && isActiveSession(session)) {
                            callback.onSuccess(documentSnapshot);
                            return;
                        }
                    }
                    callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
                })
                .addOnFailureListener(exception ->
                        callback.onError("동행 세션 정보를 불러오지 못했습니다."));
    }

    private boolean isActiveSession(CompanionSession session) {
        return session.getStatus() != SessionStatus.COMPLETED
                && session.getStatus() != SessionStatus.CANCELED;
    }

    private ManagerHomeProfile toManagerHomeProfile(DocumentSnapshot documentSnapshot) {
        String documentSummary = documentSnapshot.getString("managerDocumentSummary");
        String availabilitySummary = documentSnapshot.getString("managerAvailabilitySummary");
        String documentStatus = documentSnapshot.getString("managerDocumentStatus");
        String documentReviewNote = documentSnapshot.getString("managerDocumentReviewNote");
        String documentReviewedByName = documentSnapshot.getString("managerDocumentReviewedByName");
        long documentUpdatedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentUpdatedAt"));
        long documentReviewedAtMillis = resolveTimestampMillis(documentSnapshot.get("managerDocumentReviewedAt"));
        return new ManagerHomeProfile(
                documentSummary == null ? "" : documentSummary,
                availabilitySummary == null ? "" : availabilitySummary,
                resolveManagerDocumentStatus(documentStatus, documentSummary),
                documentReviewNote == null ? "" : documentReviewNote,
                documentUpdatedAtMillis,
                documentReviewedAtMillis,
                documentReviewedByName == null ? "" : documentReviewedByName,
                toManagerDocumentFiles(documentSnapshot)
        );
    }

    private List<ManagerDocumentFileMetadata> toManagerDocumentFiles(DocumentSnapshot documentSnapshot) {
        Map<ManagerDocumentFileType, ManagerDocumentFileMetadata> fileByType = new HashMap<>();
        Object rawPathMap = documentSnapshot.get("managerDocumentFilePaths");
        Map<?, ?> pathMap = rawPathMap instanceof Map ? (Map<?, ?>) rawPathMap : null;

        Object rawMetadataMap = documentSnapshot.get("managerDocumentFiles");
        if (rawMetadataMap instanceof Map) {
            Map<?, ?> metadataMap = (Map<?, ?>) rawMetadataMap;
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadata(
                        fileType,
                        metadataMap.get(fileType.getStorageKey())
                );
                if (metadata != null) {
                    fileByType.put(
                            fileType,
                            withReferenceConsistency(
                                    documentSnapshot,
                                    pathMap,
                                    metadata
                            )
                    );
                }
            }
        }

        if (pathMap != null) {
            for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
                if (fileByType.containsKey(fileType)) {
                    continue;
                }
                ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                        fileType,
                        stringValue(pathMap.get(fileType.getStorageKey()))
                );
                if (metadata != null) {
                    fileByType.put(fileType, metadata);
                }
            }
        }

        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            if (fileByType.containsKey(fileType)) {
                continue;
            }
            String legacyPathKey = resolveLegacyDocumentStoragePathKey(fileType);
            if (legacyPathKey == null) {
                continue;
            }
            ManagerDocumentFileMetadata metadata = toManagerDocumentFileMetadataFromPath(
                    fileType,
                    documentSnapshot.getString(legacyPathKey)
            );
            if (metadata != null) {
                fileByType.put(fileType, metadata);
            }
        }

        List<ManagerDocumentFileMetadata> documentFiles = new ArrayList<>();
        for (ManagerDocumentFileType fileType : ManagerDocumentFileType.values()) {
            ManagerDocumentFileMetadata metadata = fileByType.get(fileType);
            if (metadata != null) {
                documentFiles.add(metadata);
            }
        }
        return documentFiles;
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadata(
            ManagerDocumentFileType fileType,
            @Nullable Object rawValue
    ) {
        if (!(rawValue instanceof Map)) {
            return null;
        }

        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = stringValue(valueMap.get("fullPath"));
        if (fullPath == null || fullPath.trim().isEmpty()) {
            return null;
        }

        String fileName = normalizeText(stringValue(valueMap.get("fileName")));
        if (fileName.isEmpty()) {
            fileName = resolveFileNameFromPath(fullPath);
        }

        return new ManagerDocumentFileMetadata(
                fileType,
                fullPath,
                fileName,
                normalizeText(stringValue(valueMap.get("contentType"))),
                resolveTimestampMillis(valueMap.get("uploadedAt")),
                normalizeText(stringValue(valueMap.get("previewUri")))
        );
    }

    @Nullable
    private ManagerDocumentFileMetadata toManagerDocumentFileMetadataFromPath(
            ManagerDocumentFileType fileType,
            @Nullable String fullPath
    ) {
        String normalizedPath = normalizeText(fullPath);
        if (normalizedPath.isEmpty()) {
            return null;
        }
        return new ManagerDocumentFileMetadata(
                fileType,
                normalizedPath,
                resolveFileNameFromPath(normalizedPath),
                "",
                0L,
                "",
                false
        );
    }

    private ManagerDocumentFileMetadata withReferenceConsistency(
            DocumentSnapshot documentSnapshot,
            @Nullable Map<?, ?> pathMap,
            ManagerDocumentFileMetadata metadata
    ) {
        ManagerDocumentFileType fileType = metadata.getFileType();
        String aliasKey = resolveReferenceConsistencyAliasKey(fileType);
        boolean aliasPresent = aliasKey != null && documentSnapshot.contains(aliasKey);
        String aliasValue = aliasPresent ? stringValue(documentSnapshot.get(aliasKey)) : null;
        String pathMapValue = pathMap == null
                ? null
                : stringValue(pathMap.get(fileType.getStorageKey()));
        boolean referenceConsistent = ManagerDocumentReferencePolicy.isConsistent(
                documentSnapshot.getId(),
                fileType,
                metadata.getFullPath(),
                pathMapValue,
                aliasPresent,
                aliasValue
        );
        return new ManagerDocumentFileMetadata(
                fileType,
                metadata.getFullPath(),
                metadata.getFileName(),
                metadata.getContentType(),
                metadata.getUploadedAtMillis(),
                metadata.getPreviewUri(),
                referenceConsistent
        );
    }

    private List<ManagerDocumentHistoryEntry> toManagerDocumentHistory(DocumentSnapshot documentSnapshot) {
        Object rawHistory = documentSnapshot.get("managerDocumentHistory");
        if (!(rawHistory instanceof List)) {
            return new ArrayList<>();
        }

        List<ManagerDocumentHistoryEntry> historyEntries = new ArrayList<>();
        for (Object rawEntry : (List<?>) rawHistory) {
            if (!(rawEntry instanceof Map)) {
                continue;
            }
            Map<?, ?> entryMap = (Map<?, ?>) rawEntry;
            historyEntries.add(new ManagerDocumentHistoryEntry(
                    resolveHistoryEventType(normalizeText(stringValue(entryMap.get("eventType")))),
                    resolveTimestampMillis(entryMap.get("happenedAt")),
                    normalizeText(stringValue(entryMap.get("actorName"))),
                    normalizeText(stringValue(entryMap.get("summary"))),
                    normalizeText(stringValue(entryMap.get("reviewNote")))
            ));
        }
        historyEntries.sort((left, right) -> Long.compare(right.getHappenedAtMillis(), left.getHappenedAtMillis()));
        return historyEntries;
    }

    private List<ManagerDocumentHistoryEntry> appendManagerDocumentHistory(
            List<ManagerDocumentHistoryEntry> historyEntries,
            ManagerDocumentHistoryEntry historyEntry
    ) {
        List<ManagerDocumentHistoryEntry> updatedEntries = new ArrayList<>(historyEntries);
        updatedEntries.add(0, historyEntry);
        return updatedEntries;
    }

    private List<Map<String, Object>> toManagerDocumentHistoryPayload(
            List<ManagerDocumentHistoryEntry> historyEntries
    ) {
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ManagerDocumentHistoryEntry historyEntry : historyEntries) {
            Map<String, Object> item = new HashMap<>();
            item.put("eventType", historyEntry.getEventType().name());
            item.put("happenedAt", historyEntry.getHappenedAtMillis());
            item.put("actorName", historyEntry.getActorName());
            item.put("summary", historyEntry.getSummary());
            item.put("reviewNote", historyEntry.getReviewNote());
            payload.add(item);
        }
        return payload;
    }

    @Nullable
    private User toUser(DocumentSnapshot documentSnapshot) {
        // users 컬렉션 문서를 도메인 User 모델로 변환한다.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String roleValue = documentSnapshot.getString("role");
        String name = documentSnapshot.getString("name");
        String email = documentSnapshot.getString("email");
        String phone = documentSnapshot.getString("phone");
        if (roleValue == null || name == null || email == null) {
            return null;
        }

        UserRole role = SafeEnumParser.parseOrNull(UserRole.class, roleValue);
        if (role == null) {
            return null;
        }

        return new User(
                documentSnapshot.getId(),
                role,
                name,
                email,
                phone == null ? "" : phone
        );
    }

    @Nullable
    private User toParticipantUser(
            @Nullable String userId,
            UserRole role,
            @Nullable String name,
            @Nullable String email,
            @Nullable String phone
    ) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        return new User(
                userId,
                role,
                normalizeText(name),
                normalizeText(email),
                normalizeText(phone)
        );
    }

    @Nullable
    private AppointmentRequest toAppointmentRequest(DocumentSnapshot documentSnapshot) {
        // appointmentRequests 문서를 일정 카드에 바로 쓸 수 있는 모델로 정리한다.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String patientUserId = documentSnapshot.getString("patientUserId");
        String guardianUserId = documentSnapshot.getString("guardianUserId");
        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        String appointmentAt = stringifyDate(documentSnapshot.get("appointmentAt"));
        String meetingPlace = documentSnapshot.getString("meetingPlace");
        String specialNotes = documentSnapshot.getString("specialNotes");
        String statusValue = documentSnapshot.getString("status");
        String managerUserId = documentSnapshot.getString("managerUserId");
        Double hospitalLatitude = doubleOrNull(documentSnapshot.get("hospitalLatitude"));
        Double hospitalLongitude = doubleOrNull(documentSnapshot.get("hospitalLongitude"));

        if (patientUserId == null
                || guardianUserId == null
                || hospitalName == null
                || departmentName == null
                || appointmentAt == null
                || statusValue == null) {
            return null;
        }

        AppointmentStatus status = SafeEnumParser.parseOrNull(AppointmentStatus.class, statusValue);
        if (status == null) {
            return null;
        }

        return new AppointmentRequest(
                documentSnapshot.getId(),
                patientUserId,
                guardianUserId,
                hospitalName,
                departmentName,
                hospitalLatitude == null ? 0.0 : hospitalLatitude,
                hospitalLongitude == null ? 0.0 : hospitalLongitude,
                appointmentAt,
                meetingPlace == null ? "" : meetingPlace,
                specialNotes == null ? "" : specialNotes,
                status,
                managerUserId,
                stringOrEmpty(documentSnapshot.getString("patientName")),
                stringOrEmpty(documentSnapshot.getString("patientPhone")),
                stringOrEmpty(documentSnapshot.getString("patientEmail")),
                stringOrEmpty(documentSnapshot.getString("guardianName")),
                stringOrEmpty(documentSnapshot.getString("guardianPhone")),
                stringOrEmpty(documentSnapshot.getString("guardianEmail")),
                stringOrEmpty(documentSnapshot.getString("patientConditionSummary")),
                stringOrEmpty(documentSnapshot.getString("medicationSummary")),
                stringOrEmpty(documentSnapshot.getString("mobilitySupportCode")),
                stringOrEmpty(documentSnapshot.getString("tripTypeCode")),
                stringOrEmpty(documentSnapshot.getString("managerGenderPreferenceCode")),
                stringOrEmpty(documentSnapshot.getString("paymentMethodCode")),
                stringOrEmpty(documentSnapshot.getString("couponCode")),
                numberOrZero(documentSnapshot.get("basePrice")),
                numberOrZero(documentSnapshot.get("optionSurchargePrice")),
                numberOrZero(documentSnapshot.get("couponDiscountPrice")),
                numberOrZero(documentSnapshot.get("finalPrice")),
                stringOrEmpty(documentSnapshot.getString("paymentStatusCode")),
                stringOrEmpty(documentSnapshot.getString("paymentApprovalCode")),
                stringOrEmpty(documentSnapshot.getString("paymentApprovedAt")),
                stringOrEmpty(documentSnapshot.getString("paymentProviderLabel"))
        );
    }

    @Nullable
    private CompanionSession toSession(DocumentSnapshot documentSnapshot) {
        // currentStepOrder와 currentStatus를 읽어 단계별 UI 상태를 복원한다.
        return FirebaseCompanionSessionMapper.toSession(documentSnapshot, UserRole.MANAGER);
    }

    private Map<String, Object> buildChatMessagePayload(
            UserRole senderRole,
            String body,
            @Nullable List<CompanionChatAttachment> attachments
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("senderRole", senderRole == null ? UserRole.MANAGER.name() : senderRole.name());
        payload.put("body", normalizeText(body));
        payload.put("sentAtMillis", System.currentTimeMillis());
        if (attachments != null && !attachments.isEmpty()) {
            payload.put("attachments", toChatAttachmentPayloads(attachments));
            payload.put("attachment", toChatAttachmentPayload(attachments.get(0)));
        }
        return payload;
    }

    private String resolveChatReadField(@Nullable UserRole role) {
        if (role == UserRole.PATIENT) {
            return "patientChatReadAt";
        }
        if (role == UserRole.GUARDIAN) {
            return "guardianChatReadAt";
        }
        return "managerChatReadAt";
    }

    @Nullable
    private CompanionChatAttachment toChatAttachment(@Nullable Object rawValue) {
        if (!(rawValue instanceof Map)) {
            return null;
        }
        Map<?, ?> valueMap = (Map<?, ?>) rawValue;
        String fullPath = normalizeText(stringValue(valueMap.get("fullPath")));
        if (fullPath.isEmpty()) {
            return null;
        }
        return new CompanionChatAttachment(
                fullPath,
                normalizeText(stringValue(valueMap.get("fileName"))),
                normalizeText(stringValue(valueMap.get("contentType"))),
                resolveTimestampMillis(valueMap.get("uploadedAtMillis"))
        );
    }

    private List<CompanionChatAttachment> toChatAttachments(
            @Nullable Object rawAttachments,
            @Nullable Object rawAttachment
    ) {
        List<CompanionChatAttachment> attachments = new ArrayList<>();
        if (rawAttachments instanceof List) {
            for (Object item : (List<?>) rawAttachments) {
                CompanionChatAttachment attachment = toChatAttachment(item);
                if (attachment != null && !attachment.isEmpty()) {
                    attachments.add(attachment);
                }
            }
        }
        if (!attachments.isEmpty()) {
            return attachments;
        }
        CompanionChatAttachment attachment = toChatAttachment(rawAttachment);
        if (attachment != null && !attachment.isEmpty()) {
            attachments.add(attachment);
        }
        return attachments;
    }

    private List<CompanionChatMessage> toChatMessages(@Nullable Object rawValue) {
        List<CompanionChatMessage> messages = new ArrayList<>();
        if (!(rawValue instanceof List)) {
            return messages;
        }
        for (Object rawMessage : (List<?>) rawValue) {
            if (!(rawMessage instanceof Map)) {
                continue;
            }
            Map<?, ?> valueMap = (Map<?, ?>) rawMessage;
            String roleValue = normalizeText(stringValue(valueMap.get("senderRole")));
            String body = normalizeText(stringValue(valueMap.get("body")));
            List<CompanionChatAttachment> attachments = toChatAttachments(
                    valueMap.get("attachments"),
                    valueMap.get("attachment")
            );
            if (body.isEmpty() && attachments.isEmpty()) {
                continue;
            }
            long sentAtMillis = resolveTimestampMillis(valueMap.get("sentAtMillis"));
            UserRole senderRole = SafeEnumParser.parseOrDefault(
                    UserRole.class,
                    roleValue,
                    UserRole.MANAGER
            );
            messages.add(new CompanionChatMessage(senderRole, body, sentAtMillis, attachments));
        }
        return messages;
    }

    private List<Map<String, Object>> toChatAttachmentPayloads(List<CompanionChatAttachment> attachments) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (CompanionChatAttachment attachment : attachments) {
            if (attachment == null || attachment.isEmpty()) {
                continue;
            }
            payloads.add(toChatAttachmentPayload(attachment));
        }
        return payloads;
    }

    private Map<String, Object> toChatAttachmentPayload(CompanionChatAttachment attachment) {
        Map<String, Object> attachmentPayload = new HashMap<>();
        attachmentPayload.put("fullPath", attachment.getFullPath());
        attachmentPayload.put("fileName", attachment.getFileName());
        attachmentPayload.put("contentType", attachment.getContentType());
        attachmentPayload.put("uploadedAtMillis", attachment.getUploadedAtMillis());
        return attachmentPayload;
    }

    @Nullable
    private HospitalGuide findGuide(QuerySnapshot querySnapshot, String departmentName) {
        // 병원명으로 먼저 좁힌 뒤 진료과가 일치하는 가이드를 한 번 더 찾는다.
        for (DocumentSnapshot documentSnapshot : querySnapshot.getDocuments()) {
            HospitalGuide guide = toGuide(documentSnapshot);
            if (guide != null && guide.getDepartmentName().equals(departmentName)) {
                return guide;
            }
        }
        return null;
    }

    @Nullable
    private HospitalGuide toGuide(DocumentSnapshot documentSnapshot) {
        // Firestore 배열 형태의 steps를 GuideStep 목록으로 변환한다.
        if (!documentSnapshot.exists()) {
            return null;
        }

        String hospitalName = documentSnapshot.getString("hospitalName");
        String departmentName = documentSnapshot.getString("departmentName");
        Object stepsValue = documentSnapshot.get("steps");
        if (hospitalName == null || departmentName == null) {
            return null;
        }

        List<GuideStep> steps = toGuideSteps(stepsValue);
        if (steps.isEmpty()) {
            return null;
        }

        return new HospitalGuide(documentSnapshot.getId(), hospitalName, departmentName, steps);
    }

    static List<GuideStep> toGuideSteps(@Nullable Object stepsValue) {
        List<GuideStep> steps = new ArrayList<>();
        if (!(stepsValue instanceof List)) {
            return steps;
        }
        List<?> rawSteps = (List<?>) stepsValue;
        for (Object rawStep : rawSteps) {
            if (!(rawStep instanceof Map)) {
                continue;
            }
            // 구형 문서는 기존 필드만 읽고 새 선택 필드는 빈 값으로 유지한다.
            Map<?, ?> stepMap = (Map<?, ?>) rawStep;
            Object orderValue = stepMap.get("order");
            Object titleValue = stepMap.get("title");
            Object descriptionValue = stepMap.get("description");
            if (!(orderValue instanceof Number) || titleValue == null || descriptionValue == null) {
                continue;
            }
            steps.add(new GuideStep(
                    optionalGuideString(stepMap, "code"),
                    ((Number) orderValue).intValue(),
                    String.valueOf(titleValue),
                    String.valueOf(descriptionValue),
                    optionalGuideString(stepMap, "videoAssetId"),
                    optionalGuideString(stepMap, "videoAssetVersion"),
                    optionalGuideString(stepMap, "videoFallbackText")
            ));
        }
        return steps;
    }

    private static String optionalGuideString(Map<?, ?> stepMap, String key) {
        Object value = stepMap.get(key);
        return value instanceof String ? ((String) value).trim() : "";
    }

    @Nullable
    private SessionReport toReport(QuerySnapshot querySnapshot) {
        // 현재 구현은 세션별 리포트 1개를 가정하고 첫 문서만 사용한다.
        if (querySnapshot.isEmpty()) {
            return null;
        }
        DocumentSnapshot documentSnapshot = querySnapshot.getDocuments().get(0);
        String sessionId = documentSnapshot.getString("sessionId");
        String summary = documentSnapshot.getString("summary");
        String treatmentNotes = documentSnapshot.getString("treatmentNotes");
        String medicationNotes = documentSnapshot.getString("medicationNotes");
        String medicationName = documentSnapshot.getString("medicationName");
        String medicationChangeSummary = documentSnapshot.getString("medicationChangeSummary");
        String medicationScheduleNote = documentSnapshot.getString("medicationScheduleNote");
        MedicationComparisonDecision medicationComparisonDecision = MedicationComparisonDecision.fromValue(
                documentSnapshot.getString("medicationComparisonDecisionCode")
        );
        String medicationComparisonNote = documentSnapshot.getString("medicationComparisonNote");
        String nextVisitAt = stringifyDate(documentSnapshot.get("nextVisitAt"));
        if (sessionId == null || summary == null) {
            return null;
        }

        return new SessionReport(
                documentSnapshot.getId(),
                sessionId,
                summary,
                treatmentNotes == null ? "" : treatmentNotes,
                medicationNotes == null ? "" : medicationNotes,
                medicationName == null ? "" : medicationName,
                medicationChangeSummary == null ? "" : medicationChangeSummary,
                medicationScheduleNote == null ? "" : medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote == null ? "" : medicationComparisonNote,
                nextVisitAt == null ? "" : nextVisitAt
        );
    }

    @Nullable
    private AppointmentFollowUpRecord toAppointmentFollowUpRecord(
            DocumentSnapshot documentSnapshot,
            String requestId
    ) {
        if (!documentSnapshot.exists()) {
            return AppointmentFollowUpRecord.empty(requestId);
        }

        return new AppointmentFollowUpRecord(
                requestId,
                AppointmentFollowUpReviewRating.fromValue(documentSnapshot.getString("reviewRatingCode")),
                resolveTimestampMillis(documentSnapshot.get("reviewSavedAt")),
                AppointmentFollowUpSettlementStatus.fromValue(
                        documentSnapshot.getString("settlementFollowUpStatus")
                ),
                normalizeText(documentSnapshot.getString("settlementFollowUpNote")),
                resolveTimestampMillis(documentSnapshot.get("settlementFollowUpSavedAt")),
                AppointmentFollowUpSupportEscalationStatus.fromValue(
                        documentSnapshot.getString("supportEscalationStatus")
                ),
                resolveTimestampMillis(documentSnapshot.get("supportEscalatedAt"))
        );
    }

    @Nullable
    private SupportInquiry toSupportInquiry(DocumentSnapshot documentSnapshot) {
        if (!documentSnapshot.exists()) {
            return null;
        }

        String managerUserId = documentSnapshot.getString("managerUserId");
        String managerName = documentSnapshot.getString("managerName");
        String title = documentSnapshot.getString("title");
        String body = documentSnapshot.getString("body");
        if (managerUserId == null || managerName == null || title == null || body == null) {
            return null;
        }

        return new SupportInquiry(
                documentSnapshot.getId(),
                managerUserId,
                normalizeText(managerName),
                SupportInquiryCategory.fromValue(documentSnapshot.getString("category")),
                normalizeText(title),
                normalizeText(body),
                resolveSupportInquiryStatus(documentSnapshot.getString("status")),
                resolveTimestampMillis(documentSnapshot.get("createdAt")),
                normalizeText(documentSnapshot.getString("responseText")),
                resolveTimestampMillis(documentSnapshot.get("respondedAt")),
                normalizeText(documentSnapshot.getString("respondedByName"))
        );
    }

    @Nullable
    private static String resolveLegacyDocumentStoragePathKey(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.ID_CARD) {
            return "managerIdCardStoragePath";
        }
        if (fileType == ManagerDocumentFileType.LICENSE) {
            return "managerLicenseStoragePath";
        }
        if (fileType == ManagerDocumentFileType.CRIMINAL_RECORD) {
            return "managerCriminalRecordStoragePath";
        }
        if (fileType == ManagerDocumentFileType.HEALTH_CERTIFICATE) {
            return "managerHealthCertificateStoragePath";
        }
        return null;
    }

    @Nullable
    private static String resolveReferenceConsistencyAliasKey(ManagerDocumentFileType fileType) {
        if (fileType == ManagerDocumentFileType.NURSING_LICENSE) {
            // nursingLicense 제출에는 과거 license 별칭이 남아 있으면 안 된다.
            return "managerLicenseStoragePath";
        }
        return resolveLegacyDocumentStoragePathKey(fileType);
    }

    static void putManagerDocumentSubmissionState(Map<String, Object> updates) {
        updates.put("managerDocumentStatus", ManagerDocumentStatus.PENDING_REVIEW.name());
        updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());
    }

    static void putCanonicalQualificationReplacement(
            Map<String, Object> updates,
            ManagerDocumentFileType selectedType
    ) {
        if (!ManagerDocumentUploadPolicy.isCanonicalQualificationType(selectedType)) {
            return;
        }

        ManagerDocumentFileType[] removableTypes = new ManagerDocumentFileType[]{
                ManagerDocumentFileType.LICENSE,
                ManagerDocumentFileType.NURSING_LICENSE,
                ManagerDocumentFileType.HEALTH_CERTIFICATE
        };
        for (ManagerDocumentFileType removableType : removableTypes) {
            if (removableType == selectedType) {
                continue;
            }
            updates.put(
                    "managerDocumentFiles." + removableType.getStorageKey(),
                    FieldValue.delete()
            );
            updates.put(
                    "managerDocumentFilePaths." + removableType.getStorageKey(),
                    FieldValue.delete()
            );
            String legacyPathKey = resolveLegacyDocumentStoragePathKey(removableType);
            if (legacyPathKey != null) {
                updates.put(legacyPathKey, FieldValue.delete());
            }
        }

        // 참조 교체는 한 번의 Firestore update로 끝낸다. 이전 Storage 객체 삭제는
        // 클라이언트 실패 시 복구가 어려우므로 서버의 고아 원본 정리 작업이 담당해야 한다.
    }

    static void putManagerDocumentSummaryState(Map<String, Object> updates, String normalizedSummary) {
        updates.put(
                "managerDocumentStatus",
                normalizedSummary.isEmpty()
                        ? ManagerDocumentStatus.NOT_SUBMITTED.name()
                        : ManagerDocumentStatus.PENDING_REVIEW.name()
        );
        updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());
    }

    static boolean managerDocumentSummaryStateChanged(
            @Nullable String currentSummary,
            @Nullable String currentStatus,
            String normalizedSummary
    ) {
        String normalizedCurrentSummary = currentSummary == null ? "" : currentSummary.trim();
        ManagerDocumentStatus resolvedCurrentStatus = SafeEnumParser.parseOrNull(
                ManagerDocumentStatus.class,
                currentStatus
        );
        if (resolvedCurrentStatus == null) {
            resolvedCurrentStatus = normalizedCurrentSummary.isEmpty()
                    ? ManagerDocumentStatus.NOT_SUBMITTED
                    : ManagerDocumentStatus.PENDING_REVIEW;
        }
        ManagerDocumentStatus targetStatus = normalizedSummary.isEmpty()
                ? ManagerDocumentStatus.NOT_SUBMITTED
                : ManagerDocumentStatus.PENDING_REVIEW;
        return !normalizedCurrentSummary.equals(normalizedSummary)
                || resolvedCurrentStatus != targetStatus;
    }

    static void putManagerDocumentDraftState(
            Map<String, Object> updates,
            ManagerDocumentStatus currentStatus,
            String normalizedSummary
    ) {
        boolean isInitialDraft = currentStatus == ManagerDocumentStatus.NOT_SUBMITTED
                && normalizedSummary.isEmpty();
        updates.put(
                "managerDocumentStatus",
                isInitialDraft
                        ? ManagerDocumentStatus.NOT_SUBMITTED.name()
                        : ManagerDocumentStatus.PENDING_REVIEW.name()
        );
        updates.put("managerDocumentUpdatedAt", FieldValue.serverTimestamp());
    }

    private String resolveFileNameFromPath(String fullPath) {
        int separatorIndex = fullPath.lastIndexOf('/');
        if (separatorIndex < 0 || separatorIndex >= fullPath.length() - 1) {
            return fullPath;
        }
        return fullPath.substring(separatorIndex + 1);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    @Nullable
    private String stringValue(@Nullable Object rawValue) {
        return rawValue instanceof String ? (String) rawValue : null;
    }

    @Nullable
    private String stringifyDate(@Nullable Object rawValue) {
        // Timestamp와 문자열 입력을 같은 텍스트 출력으로 맞춘다.
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().toString();
        }
        return String.valueOf(rawValue);
    }

    private String stringOrEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private int numberOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @Nullable
    private Double doubleOrNull(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private long resolveTimestampMillis(@Nullable Object rawValue) {
        if (rawValue instanceof Timestamp) {
            return ((Timestamp) rawValue).toDate().getTime();
        }
        if (rawValue instanceof Number) {
            return ((Number) rawValue).longValue();
        }
        return 0L;
    }

    private SupportInquiryStatus resolveSupportInquiryStatus(@Nullable String rawValue) {
        return SafeEnumParser.parseOrDefault(
                SupportInquiryStatus.class,
                rawValue,
                SupportInquiryStatus.RECEIVED
        );
    }

    private ManagerDocumentHistoryEventType resolveHistoryEventType(String rawValue) {
        return SafeEnumParser.parseOrDefault(
                ManagerDocumentHistoryEventType.class,
                rawValue,
                ManagerDocumentHistoryEventType.SUBMITTED
        );
    }

    private ManagerDocumentStatus resolveManagerDocumentStatus(
            @Nullable String rawStatus,
            @Nullable String documentSummary
    ) {
        ManagerDocumentStatus status = SafeEnumParser.parseOrNull(ManagerDocumentStatus.class, rawStatus);
        if (status != null) {
            return status;
        }
        if (normalizeText(documentSummary).isEmpty()) {
            return ManagerDocumentStatus.NOT_SUBMITTED;
        }
        return ManagerDocumentStatus.PENDING_REVIEW;
    }

    private SessionStatus resolveStepStatus(int stepOrder, int totalSteps) {
        // 단계 번호를 화면 상태 배지에 사용하는 도메인 상태로 매핑한다.
        if (stepOrder <= 1) {
            return SessionStatus.MEETING;
        }
        if (stepOrder == 2) {
            return SessionStatus.WAITING;
        }
        if (stepOrder <= 4) {
            return SessionStatus.IN_TREATMENT;
        }
        if (stepOrder < totalSteps) {
            return SessionStatus.PAYMENT;
        }
        return SessionStatus.PAYMENT;
    }
}
