package com.example.bodeul.ui.manager;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;

import java.util.List;
import java.util.UUID;

public class ManagerGuideViewModel extends ViewModel {

    private static final String REPORT_DRAFT_PRESENT = "managerGuide.reportDraft.present";
    private static final String REPORT_DRAFT_PREFIX = "managerGuide.reportDraft.";
    private static final String ARTIFACT_REQUEST_PREFIX = "managerGuide.artifactRequest.";
    private static final String VITALS_DRAFT_PREFIX = "managerGuide.vitalsDraft.";

    public enum StatePanelType {
        NONE,
        PERMISSION,
        AUTH,
        EMPTY,
        LOAD_ERROR
    }

    public static class UiState {
        @Nullable
        public final ManagerDashboard dashboard;
        @Nullable
        public final ManagerGuideScreenModel screenModel;
        public final StatePanelType statePanelType;
        @Nullable
        public final String errorMessage;
        public final boolean requireProfileCompletion;

        public UiState(@Nullable ManagerDashboard dashboard, @Nullable ManagerGuideScreenModel screenModel, StatePanelType statePanelType, @Nullable String errorMessage, boolean requireProfileCompletion) {
            this.dashboard = dashboard;
            this.screenModel = screenModel;
            this.statePanelType = statePanelType;
            this.errorMessage = errorMessage;
            this.requireProfileCompletion = requireProfileCompletion;
        }

        public static UiState loading() {
            return new UiState(null, null, StatePanelType.NONE, null, false);
        }

        public static UiState screen(ManagerDashboard dashboard, ManagerGuideScreenModel screenModel) {
            return new UiState(dashboard, screenModel, StatePanelType.NONE, null, false);
        }

        public static UiState panel(StatePanelType type, @Nullable String errorMessage) {
            return new UiState(null, null, type, errorMessage, false);
        }

        public static UiState profileCompletion() {
            return new UiState(null, null, StatePanelType.NONE, null, true);
        }
    }

    private final MutableLiveData<UiState> _uiState = new MutableLiveData<>(UiState.loading());
    public LiveData<UiState> getUiState() {
        return _uiState;
    }

    private final MutableLiveData<String> _toastMessage = new MutableLiveData<>();
    public LiveData<String> getToastMessage() {
        return _toastMessage;
    }

    private final MutableLiveData<Long> _reportSubmittedEvent = new MutableLiveData<>();
    public LiveData<Long> getReportSubmittedEvent() {
        return _reportSubmittedEvent;
    }

    private final MutableLiveData<Boolean> _mutationInFlight =
            new MutableLiveData<>(false);
    public LiveData<Boolean> getMutationInFlight() {
        return _mutationInFlight;
    }

    private final AuthRepository authRepository;
    private final ManagerRepository managerRepository;
    private final ManagerGuideCoordinator coordinator;
    private final CompanionRealtimeSubscriber realtimeSubscriber;
    private final SavedStateHandle savedStateHandle;
    private final boolean legacyManagerLocationEnabled;

    private User currentUser;
    private boolean liveLocationShareInFlight = false;
    private PendingLocationUpdate pendingLiveLocationUpdate;
    private String subscribedSessionId = "";
    private boolean mutationInFlight;
    @Nullable
    private ManagerGuideConsultationDraft consultationDraft;
    private String consultationDraftSessionId = "";

    public ManagerGuideViewModel(
            AuthRepository authRepository,
            ManagerRepository managerRepository,
            ManagerGuideCoordinator coordinator,
            CompanionRealtimeSubscriber realtimeSubscriber,
            SavedStateHandle savedStateHandle,
            boolean legacyManagerLocationEnabled
    ) {
        this.authRepository = authRepository;
        this.managerRepository = managerRepository;
        this.coordinator = coordinator;
        this.realtimeSubscriber = realtimeSubscriber;
        this.savedStateHandle = savedStateHandle;
        this.legacyManagerLocationEnabled = legacyManagerLocationEnabled;
    }

    public void reload() {
        _uiState.setValue(UiState.loading());
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override
            public void onSuccess(User result) {
                if (AuthFlowRouter.requiresProfileCompletion(result)) {
                    _uiState.setValue(UiState.profileCompletion());
                    return;
                }
                if (result.getRole() != UserRole.MANAGER) {
                    _uiState.setValue(UiState.panel(StatePanelType.PERMISSION, null));
                    return;
                }

                currentUser = result;
                loadDashboard();
            }

            @Override
            public void onError(String message) {
                _uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            }
        });
    }

    public void loadDashboard() {
        if (currentUser == null) {
            _uiState.setValue(UiState.panel(StatePanelType.AUTH, null));
            return;
        }

        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                ensureRealtimeSubscription(result);
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                if (ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message)) {
                    _uiState.setValue(UiState.panel(StatePanelType.EMPTY, null));
                    return;
                }
                _uiState.setValue(UiState.panel(StatePanelType.LOAD_ERROR, message));
            }
        });
    }

    private void bindDashboard(@Nullable ManagerDashboard dashboard) {
        if (dashboard == null) {
            _uiState.setValue(UiState.panel(StatePanelType.EMPTY, null));
            return;
        }
        if (isRealtimeClosed(dashboard)) {
            stopRealtimeSubscription();
        }
        retainVitalsDraftFor(dashboard);
        retainConsultationDraftFor(dashboard);
        _uiState.setValue(UiState.screen(dashboard, coordinator.createScreenModel(
                dashboard,
                managerRepository.isFirebaseBacked()
        )));
    }

    private void ensureRealtimeSubscription(ManagerDashboard dashboard) {
        if (isRealtimeClosed(dashboard)) {
            stopRealtimeSubscription();
            return;
        }
        String sessionId = dashboard == null || dashboard.getSession() == null
                ? ""
                : dashboard.getSession().getRealtimeSessionId();
        if (sessionId.isEmpty() || sessionId.equals(subscribedSessionId)) {
            return;
        }
        subscribedSessionId = sessionId;
        realtimeSubscriber.subscribe(sessionId, this::loadDashboard);
    }

    private boolean isRealtimeClosed(@Nullable ManagerDashboard dashboard) {
        if (dashboard == null || dashboard.getSession() == null) {
            return false;
        }
        if (dashboard.getSession().getCareEndedAtMillis() > 0L) {
            return true;
        }
        SessionStatus status = dashboard.getSession().getStatus();
        return status == SessionStatus.CARE_ENDED
                || status == SessionStatus.COMPLETED
                || status == SessionStatus.CANCELED;
    }

    private void stopRealtimeSubscription() {
        realtimeSubscriber.stop();
        subscribedSessionId = "";
    }

    @Override
    protected void onCleared() {
        stopRealtimeSubscription();
        super.onCleared();
    }

    public void advanceStep() {
        UiState state = _uiState.getValue();
        ManagerDashboard dashboard = state == null ? null : state.dashboard;
        if (dashboard == null || dashboard.getSession() == null) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        advanceStep(
                dashboard.getSession().getId(),
                dashboard.getSession().getCurrentStepCode());
    }

    public void advanceStep(String expectedSessionId, String expectedStepCode) {
        if (currentUser == null) return;
        if (!beginMutation()) return;
        managerRepository.advanceCurrentStep(
                currentUser.getId(),
                expectedSessionId,
                expectedStepCode,
                new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                finishMutation();
                _toastMessage.setValue("다음 단계로 이동했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                finishMutation();
                _toastMessage.setValue(message);
                loadDashboard();
            }
        });
    }

    public void saveLocationSummary(String summary) {
        if (!legacyManagerLocationEnabled || currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("내용을 입력해 주세요.");
            return;
        }
        managerRepository.saveLocationSummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("위치 메모를 저장했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void saveGuardianUpdate(String message) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(message)) {
            _toastMessage.setValue("내용을 입력해 주세요.");
            return;
        }
        if (!beginMutation()) return;
        managerRepository.saveGuardianUpdate(currentUser.getId(), message, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                finishMutation();
                _toastMessage.setValue("보호자 공유 메시지를 저장했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String errorMessage) {
                finishMutation();
                _toastMessage.setValue(errorMessage);
            }
        });
    }

    public void saveConsultationGuardianUpdate(String message) {
        UiState state = _uiState.getValue();
        ManagerDashboard dashboard = state == null ? null : state.dashboard;
        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        if (currentUser == null || session == null) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        String expectedSessionId = session.getId();
        String expectedStepCode = session.getCurrentStepCode();
        if (!ManagerRepository.matchesConsultationExpectation(
                session, expectedSessionId, expectedStepCode)) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        if (!beginMutation()) return;
        String value = message == null ? "" : message.trim();
        managerRepository.saveConsultationGuardianUpdate(
                currentUser.getId(),
                expectedSessionId,
                expectedStepCode,
                value,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        finishMutation();
                        _toastMessage.setValue(TextUtils.isEmpty(value)
                                ? "보호자 공유 내용을 비웠습니다."
                                : "보호자 공유 내용을 저장했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        finishMutation();
                        _toastMessage.setValue(errorMessage);
                        if (ManagerRepository.MESSAGE_STALE_GUIDE_STEP.equals(errorMessage)) {
                            loadDashboard();
                        }
                    }
                });
    }

    public void saveFieldPhotoNote(String note) {
        if (currentUser == null) return;
        managerRepository.saveFieldPhotoNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue(TextUtils.isEmpty(note)
                        ? "현장 메모를 비웠습니다."
                        : "현장 메모를 저장했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void saveConsultationFieldNote(String note) {
        UiState state = _uiState.getValue();
        ManagerDashboard dashboard = state == null ? null : state.dashboard;
        CompanionSession session = dashboard == null ? null : dashboard.getSession();
        if (currentUser == null || session == null) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        String expectedSessionId = session.getId();
        String expectedStepCode = session.getCurrentStepCode();
        if (!ManagerRepository.matchesConsultationExpectation(
                session, expectedSessionId, expectedStepCode)) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        if (!beginMutation()) return;
        String value = note == null ? "" : note.trim();
        managerRepository.saveConsultationFieldNote(
                currentUser.getId(),
                expectedSessionId,
                expectedStepCode,
                value,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        finishMutation();
                        _toastMessage.setValue(TextUtils.isEmpty(value)
                                ? "현장 메모를 비웠습니다."
                                : "현장 메모를 저장했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        finishMutation();
                        _toastMessage.setValue(errorMessage);
                        if (ManagerRepository.MESSAGE_STALE_GUIDE_STEP.equals(errorMessage)) {
                            loadDashboard();
                        }
                    }
                });
    }

    /** 기초 측정 메모 저장 성공을 확인한 뒤 같은 세션의 다음 단계로 이동한다. */
    public void saveVitalsAndAdvance(String note) {
        UiState state = _uiState.getValue();
        ManagerDashboard dashboard = state == null ? null : state.dashboard;
        if (currentUser == null || dashboard == null || dashboard.getSession() == null) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            return;
        }
        String expectedSessionId = dashboard.getSession().getId();
        String expectedStepCode = dashboard.getSession().getCurrentStepCode();
        if (!"VITALS_CHECK".equals(expectedStepCode)) {
            _toastMessage.setValue(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
            return;
        }
        if (!beginMutation()) return;
        managerRepository.saveVitalsNote(
                currentUser.getId(),
                expectedSessionId,
                expectedStepCode,
                note,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard savedDashboard) {
                        clearVitalsDraft(expectedSessionId);
                        if (!ManagerRepository.matchesAdvanceExpectation(
                                savedDashboard.getSession(),
                                expectedSessionId,
                                expectedStepCode)) {
                            finishMutation();
                            _toastMessage.setValue(ManagerRepository.MESSAGE_STALE_GUIDE_STEP);
                            bindDashboard(savedDashboard);
                            return;
                        }
                        advanceAfterVitalsSave(
                                savedDashboard,
                                expectedSessionId,
                                expectedStepCode);
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                    }
                });
    }

    private void advanceAfterVitalsSave(
            ManagerDashboard savedDashboard,
            String expectedSessionId,
            String expectedStepCode
    ) {
        managerRepository.advanceCurrentStep(
                currentUser.getId(),
                expectedSessionId,
                expectedStepCode,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        finishMutation();
                        _toastMessage.setValue("측정 결과를 저장하고 다음 단계로 이동했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                        bindDashboard(savedDashboard);
                    }
                });
    }

    @Nullable
    ManagerGuideVitalsDraft getVitalsDraft(String sessionId) {
        return restoreVitalsDraft(savedStateHandle, sessionId);
    }

    void saveVitalsDraft(String sessionId, ManagerGuideVitalsDraft draft) {
        saveVitalsDraft(savedStateHandle, sessionId, draft);
    }

    static void saveVitalsDraft(
            SavedStateHandle state,
            String sessionId,
            ManagerGuideVitalsDraft draft
    ) {
        state.set(VITALS_DRAFT_PREFIX + "sessionId", sessionId);
        state.set(VITALS_DRAFT_PREFIX + "systolic", draft.systolic);
        state.set(VITALS_DRAFT_PREFIX + "diastolic", draft.diastolic);
        state.set(VITALS_DRAFT_PREFIX + "heartRate", draft.heartRate);
        state.set(VITALS_DRAFT_PREFIX + "weight", draft.weight);
    }

    @Nullable
    static ManagerGuideVitalsDraft restoreVitalsDraft(
            SavedStateHandle state,
            String sessionId
    ) {
        String savedSessionId = state.get(VITALS_DRAFT_PREFIX + "sessionId");
        if (sessionId == null || sessionId.isEmpty() || !sessionId.equals(savedSessionId)) {
            return null;
        }
        return ManagerGuideVitalsDraft.fromInputs(
                state.get(VITALS_DRAFT_PREFIX + "systolic"),
                state.get(VITALS_DRAFT_PREFIX + "diastolic"),
                state.get(VITALS_DRAFT_PREFIX + "heartRate"),
                state.get(VITALS_DRAFT_PREFIX + "weight"));
    }

    private void clearVitalsDraft(String sessionId) {
        clearVitalsDraft(savedStateHandle, sessionId);
    }

    static void clearVitalsDraft(SavedStateHandle state, String sessionId) {
        String savedSessionId = state.get(VITALS_DRAFT_PREFIX + "sessionId");
        if (sessionId != null && sessionId.equals(savedSessionId)) {
            state.remove(VITALS_DRAFT_PREFIX + "sessionId");
            state.remove(VITALS_DRAFT_PREFIX + "systolic");
            state.remove(VITALS_DRAFT_PREFIX + "diastolic");
            state.remove(VITALS_DRAFT_PREFIX + "heartRate");
            state.remove(VITALS_DRAFT_PREFIX + "weight");
        }
    }

    private void retainVitalsDraftFor(ManagerDashboard dashboard) {
        String savedSessionId = savedStateHandle.get(VITALS_DRAFT_PREFIX + "sessionId");
        if (savedSessionId == null || dashboard.getSession() == null) {
            return;
        }
        String activeSessionId = dashboard.getSession().getId();
        String activeStepCode = dashboard.getSession().getCurrentStepCode();
        if (!savedSessionId.equals(activeSessionId)
                || !"VITALS_CHECK".equals(activeStepCode)) {
            clearVitalsDraft(savedStateHandle, savedSessionId);
        }
    }

    @Nullable
    ManagerGuideConsultationDraft getConsultationDraft(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()
                || !sessionId.equals(consultationDraftSessionId)) {
            return null;
        }
        return consultationDraft;
    }

    void saveConsultationDraft(String sessionId, ManagerGuideConsultationDraft draft) {
        if (sessionId == null || sessionId.isEmpty() || draft == null) {
            return;
        }
        consultationDraftSessionId = sessionId;
        consultationDraft = draft;
    }

    void clearConsultationDraft(String sessionId) {
        if (sessionId != null && sessionId.equals(consultationDraftSessionId)) {
            consultationDraftSessionId = "";
            consultationDraft = null;
        }
    }

    private void retainConsultationDraftFor(ManagerDashboard dashboard) {
        if (consultationDraft == null || dashboard.getSession() == null) {
            return;
        }
        String activeSessionId = dashboard.getSession().getId();
        String activeStepCode = dashboard.getSession().getCurrentStepCode();
        if (!consultationDraftSessionId.equals(activeSessionId)
                || !"CONSULTATION_SUPPORT".equals(activeStepCode)) {
            clearConsultationDraft(consultationDraftSessionId);
        }
    }

    public void replaceSessionArtifacts(String purpose, List<Uri> fileUris) {
        if (currentUser == null) return;
        String requestFingerprint = artifactRequestFingerprint(purpose, fileUris);
        String requestId = artifactRequestId(purpose, requestFingerprint);
        if (!beginMutation()) return;
        managerRepository.replaceSessionArtifacts(
                currentUser.getId(),
                purpose,
                requestId,
                fileUris,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        clearArtifactRequest(purpose);
                        finishMutation();
                        _toastMessage.setValue("선택한 동행 첨부를 저장했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                    }
                });
    }

    public void clearSessionArtifacts(String purpose) {
        if (currentUser == null) return;
        if (!beginMutation()) return;
        managerRepository.clearSessionArtifacts(
                currentUser.getId(),
                purpose,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        clearArtifactRequest(purpose);
                        finishMutation();
                        _toastMessage.setValue("동행 첨부를 삭제했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                    }
                });
    }

    public void updatePreConsultationConfirmed(boolean confirmed) {
        if (currentUser == null) return;
        if (!beginMutation()) return;
        managerRepository.updatePreConsultationConfirmed(
                currentUser.getId(),
                confirmed,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        finishMutation();
                        _toastMessage.setValue(confirmed
                                ? "진료 전 확인을 완료했습니다."
                                : "진료 전 확인을 해제했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                        loadDashboard();
                    }
                });
    }

    public void saveMedicationNote(String note) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(note)) {
            _toastMessage.setValue("내용을 입력해 주세요.");
            return;
        }
        managerRepository.saveMedicationNote(currentUser.getId(), note, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("복약 메모를 저장했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void savePharmacySummary(String summary) {
        if (currentUser == null) return;
        if (TextUtils.isEmpty(summary)) {
            _toastMessage.setValue("내용을 입력해 주세요.");
            return;
        }
        managerRepository.savePharmacySummary(currentUser.getId(), summary, new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                _toastMessage.setValue("약국 진행 내용을 저장했습니다.");
                bindDashboard(result);
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void togglePrescriptionCollected() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isPrescriptionCollected();
                managerRepository.updatePrescriptionCollected(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue
                                        ? "처방전 수령을 완료로 표시했습니다."
                                        : "처방전 수령 전으로 되돌렸습니다.");
                                bindDashboard(updated);
                            }

                            @Override
                            public void onError(String message) {
                                _toastMessage.setValue(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void togglePharmacyCompleted() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isPharmacyCompleted();
                managerRepository.updatePharmacyCompleted(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue ? "약 수령을 완료로 표시했습니다." : "약 수령 전으로 되돌렸습니다.");
                                bindDashboard(updated);
                            }

                            @Override
                            public void onError(String message) {
                                _toastMessage.setValue(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void toggleMedicationGuidanceCompleted() {
        if (currentUser == null) return;
        managerRepository.getManagerDashboard(currentUser.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override
            public void onSuccess(ManagerDashboard result) {
                boolean nextValue = !result.getSession().isMedicationGuidanceCompleted();
                managerRepository.updateMedicationGuidanceCompleted(
                        currentUser.getId(),
                        nextValue,
                        new RepositoryCallback<ManagerDashboard>() {
                            @Override
                            public void onSuccess(ManagerDashboard updated) {
                                _toastMessage.setValue(nextValue
                                        ? "복약 안내를 완료로 표시했습니다."
                                        : "복약 안내 전으로 되돌렸습니다.");
                                bindDashboard(updated);
                            }

                            @Override
                            public void onError(String message) {
                                _toastMessage.setValue(message);
                            }
                        }
                );
            }

            @Override
            public void onError(String message) {
                _toastMessage.setValue(message);
            }
        });
    }

    public void submitReport(
            String summary,
            String treatment,
            String medication,
            String medicationName,
            String medicationChangeSummary,
            String medicationScheduleNote,
            @Nullable MedicationComparisonDecision medicationComparisonDecision,
            String medicationComparisonNote,
            String nextVisit
    ) {
        if (currentUser == null) return;
        saveReportDraft(new ReportDraft(
                currentSessionId(),
                summary,
                treatment,
                medication,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisit));
        if (summary != null && summary.trim().length() > 300) {
            _toastMessage.setValue("매니저 일지는 300자 이하로 입력해 주세요.");
            return;
        }
        boolean hasMedicationComparisonInput = !TextUtils.isEmpty(medication)
                || !TextUtils.isEmpty(medicationName)
                || !TextUtils.isEmpty(medicationChangeSummary)
                || !TextUtils.isEmpty(medicationScheduleNote);
        if (hasMedicationComparisonInput && medicationComparisonDecision == null) {
            _toastMessage.setValue("복약 대조 판단을 선택해 주세요.");
            return;
        }
        if (medicationComparisonDecision == MedicationComparisonDecision.RECHECK_REQUIRED
                && TextUtils.isEmpty(medicationComparisonNote)) {
            _toastMessage.setValue("재확인 사유를 입력해 주세요.");
            return;
        }
        if (!beginMutation()) return;
        managerRepository.submitSessionReport(
                currentUser.getId(),
                summary,
                treatment,
                medication,
                medicationName,
                medicationChangeSummary,
                medicationScheduleNote,
                medicationComparisonDecision,
                medicationComparisonNote,
                nextVisit,
                new RepositoryCallback<SessionReport>() {
                    @Override
                    public void onSuccess(SessionReport result) {
                        clearReportDraft();
                        finishMutation();
                        _reportSubmittedEvent.setValue(System.currentTimeMillis());
                    }

                    @Override
                    public void onError(String message) {
                        finishMutation();
                        _toastMessage.setValue(message);
                        loadDashboard();
                    }
                }
        );
    }

    @Nullable
    public ReportDraft getReportDraft(String sessionId) {
        ReportDraft draft = restoreReportDraft(savedStateHandle);
        String normalizedSessionId = sessionId == null ? "" : sessionId.trim();
        return draft != null && draft.sessionId.equals(normalizedSessionId) ? draft : null;
    }

    @Nullable
    static ReportDraft restoreReportDraft(SavedStateHandle state) {
        if (!Boolean.TRUE.equals(state.get(REPORT_DRAFT_PRESENT))) {
            return null;
        }
        String decisionName = valueFromState(state, "decision");
        MedicationComparisonDecision decision = null;
        if (!decisionName.isEmpty()) {
            try {
                decision = MedicationComparisonDecision.valueOf(decisionName);
            } catch (IllegalArgumentException ignored) {
                // 지원하지 않는 과거 값은 선택되지 않은 상태로 복원한다.
            }
        }
        return new ReportDraft(
                valueFromState(state, "sessionId"),
                valueFromState(state, "summary"),
                valueFromState(state, "treatment"),
                valueFromState(state, "medication"),
                valueFromState(state, "medicationName"),
                valueFromState(state, "medicationChangeSummary"),
                valueFromState(state, "medicationScheduleNote"),
                decision,
                valueFromState(state, "medicationComparisonNote"),
                valueFromState(state, "nextVisit"));
    }

    private void saveReportDraft(ReportDraft draft) {
        saveReportDraft(savedStateHandle, draft);
    }

    static void saveReportDraft(SavedStateHandle state, ReportDraft draft) {
        state.set(REPORT_DRAFT_PRESENT, true);
        state.set(REPORT_DRAFT_PREFIX + "sessionId", draft.sessionId);
        state.set(REPORT_DRAFT_PREFIX + "summary", draft.summary);
        state.set(REPORT_DRAFT_PREFIX + "treatment", draft.treatment);
        state.set(REPORT_DRAFT_PREFIX + "medication", draft.medication);
        state.set(REPORT_DRAFT_PREFIX + "medicationName", draft.medicationName);
        state.set(
                REPORT_DRAFT_PREFIX + "medicationChangeSummary",
                draft.medicationChangeSummary);
        state.set(
                REPORT_DRAFT_PREFIX + "medicationScheduleNote",
                draft.medicationScheduleNote);
        state.set(
                REPORT_DRAFT_PREFIX + "decision",
                draft.medicationComparisonDecision == null
                        ? ""
                        : draft.medicationComparisonDecision.name());
        state.set(
                REPORT_DRAFT_PREFIX + "medicationComparisonNote",
                draft.medicationComparisonNote);
        state.set(REPORT_DRAFT_PREFIX + "nextVisit", draft.nextVisit);
    }

    private void clearReportDraft() {
        savedStateHandle.set(REPORT_DRAFT_PRESENT, false);
    }

    private static String valueFromState(SavedStateHandle state, String suffix) {
        String value = state.get(REPORT_DRAFT_PREFIX + suffix);
        return value == null ? "" : value;
    }

    private String currentSessionId() {
        UiState state = _uiState.getValue();
        if (state == null || state.dashboard == null || state.dashboard.getSession() == null) {
            return "";
        }
        String sessionId = state.dashboard.getSession().getId();
        return sessionId == null ? "" : sessionId.trim();
    }

    private String artifactRequestFingerprint(String purpose, List<Uri> fileUris) {
        StringBuilder fingerprint = new StringBuilder(purpose == null ? "" : purpose.trim());
        if (fileUris != null) {
            for (Uri uri : fileUris) {
                fingerprint.append('\n').append(uri == null ? "" : uri.toString());
            }
        }
        return fingerprint.toString();
    }

    private String artifactRequestId(String purpose, String fingerprint) {
        return artifactRequestId(savedStateHandle, purpose, fingerprint);
    }

    static String artifactRequestId(
            SavedStateHandle state,
            String purpose,
            String fingerprint) {
        String key = ARTIFACT_REQUEST_PREFIX + normalizePurposeKey(purpose);
        String storedFingerprint = state.get(key + ".fingerprint");
        String storedRequestId = state.get(key + ".id");
        if (fingerprint.equals(storedFingerprint) && storedRequestId != null) {
            return storedRequestId;
        }
        String requestId = UUID.randomUUID().toString();
        state.set(key + ".fingerprint", fingerprint);
        state.set(key + ".id", requestId);
        return requestId;
    }

    private void clearArtifactRequest(String purpose) {
        String key = ARTIFACT_REQUEST_PREFIX + normalizePurposeKey(purpose);
        savedStateHandle.remove(key + ".fingerprint");
        savedStateHandle.remove(key + ".id");
    }

    private static String normalizePurposeKey(String purpose) {
        return purpose == null ? "unknown" : purpose.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private boolean beginMutation() {
        if (mutationInFlight) {
            return false;
        }
        mutationInFlight = true;
        _mutationInFlight.setValue(true);
        return true;
    }

    private void finishMutation() {
        mutationInFlight = false;
        _mutationInFlight.setValue(false);
    }

    public void shareCurrentLocation(double latitude, double longitude, String summary) {
        if (!legacyManagerLocationEnabled || currentUser == null) return;
        managerRepository.shareCurrentLocation(
                currentUser.getId(),
                latitude,
                longitude,
                summary,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        _toastMessage.setValue("현재 위치를 공유했습니다.");
                        bindDashboard(result);
                    }

                    @Override
                    public void onError(String message) {
                        _toastMessage.setValue(message);
                    }
                }
        );
    }

    public void updateLiveLocationSharingState(boolean active, Runnable onActivationComplete, Runnable onActivationFailed) {
        if (!legacyManagerLocationEnabled) {
            if (active) {
                if (onActivationFailed != null) onActivationFailed.run();
            } else if (onActivationComplete != null) {
                onActivationComplete.run();
            }
            return;
        }
        if (currentUser == null) {
            if (onActivationFailed != null) onActivationFailed.run();
            return;
        }
        managerRepository.updateLiveLocationSharingState(
                currentUser.getId(),
                active,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        if (active) {
                            _toastMessage.setValue("실시간 위치 공유를 시작했습니다.");
                        } else {
                            _toastMessage.setValue("실시간 위치 공유를 중지했습니다.");
                        }
                        bindDashboard(result);
                        if (onActivationComplete != null) onActivationComplete.run();
                    }

                    @Override
                    public void onError(String message) {
                        _toastMessage.setValue(message);
                        if (onActivationFailed != null) onActivationFailed.run();
                    }
                }
        );
    }

    public void enqueueLiveLocationShare(double latitude, double longitude, String summary, boolean isTrackerRunning) {
        if (!legacyManagerLocationEnabled || currentUser == null) return;
        PendingLocationUpdate update = new PendingLocationUpdate(latitude, longitude, summary);
        if (liveLocationShareInFlight) {
            pendingLiveLocationUpdate = update;
            return;
        }
        dispatchLiveLocationShare(update, isTrackerRunning);
    }

    private void dispatchLiveLocationShare(PendingLocationUpdate update, boolean isTrackerRunning) {
        if (currentUser == null) return;
        liveLocationShareInFlight = true;
        managerRepository.shareCurrentLocation(
                currentUser.getId(),
                update.latitude,
                update.longitude,
                update.summary,
                new RepositoryCallback<ManagerDashboard>() {
                    @Override
                    public void onSuccess(ManagerDashboard result) {
                        liveLocationShareInFlight = false;
                        bindDashboard(result);
                        flushPendingLiveLocationShare(isTrackerRunning);
                    }

                    @Override
                    public void onError(String message) {
                        liveLocationShareInFlight = false;
                        _toastMessage.setValue(message);
                        flushPendingLiveLocationShare(isTrackerRunning);
                    }
                }
        );
    }

    private void flushPendingLiveLocationShare(boolean isTrackerRunning) {
        PendingLocationUpdate nextUpdate = pendingLiveLocationUpdate;
        pendingLiveLocationUpdate = null;
        if (nextUpdate != null && isTrackerRunning) {
            dispatchLiveLocationShare(nextUpdate, true);
        }
    }

    public void resetLiveLocationInFlight() {
        liveLocationShareInFlight = false;
        pendingLiveLocationUpdate = null;
    }

    public void toastMessageHandled() {
        _toastMessage.setValue(null);
    }

    public void reportSubmittedEventHandled() {
        _reportSubmittedEvent.setValue(null);
    }

    private static class PendingLocationUpdate {
        final double latitude;
        final double longitude;
        final String summary;

        PendingLocationUpdate(double latitude, double longitude, String summary) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.summary = summary;
        }
    }

    public static final class ReportDraft {
        public final String sessionId;
        public final String summary;
        public final String treatment;
        public final String medication;
        public final String medicationName;
        public final String medicationChangeSummary;
        public final String medicationScheduleNote;
        @Nullable
        public final MedicationComparisonDecision medicationComparisonDecision;
        public final String medicationComparisonNote;
        public final String nextVisit;

        ReportDraft(
                String sessionId,
                String summary,
                String treatment,
                String medication,
                String medicationName,
                String medicationChangeSummary,
                String medicationScheduleNote,
                @Nullable MedicationComparisonDecision medicationComparisonDecision,
                String medicationComparisonNote,
                String nextVisit) {
            this.sessionId = normalizeDraftValue(sessionId);
            this.summary = normalizeDraftValue(summary);
            this.treatment = normalizeDraftValue(treatment);
            this.medication = normalizeDraftValue(medication);
            this.medicationName = normalizeDraftValue(medicationName);
            this.medicationChangeSummary = normalizeDraftValue(medicationChangeSummary);
            this.medicationScheduleNote = normalizeDraftValue(medicationScheduleNote);
            this.medicationComparisonDecision = medicationComparisonDecision;
            this.medicationComparisonNote = normalizeDraftValue(medicationComparisonNote);
            this.nextVisit = normalizeDraftValue(nextVisit);
        }

        private static String normalizeDraftValue(String value) {
            return value == null ? "" : value;
        }
    }

    public static class Factory extends androidx.lifecycle.AbstractSavedStateViewModelFactory {
        private final AuthRepository authRepository;
        private final ManagerRepository managerRepository;
        private final ManagerGuideCoordinator coordinator;
        private final CompanionRealtimeSubscriber realtimeSubscriber;
        private final boolean legacyManagerLocationEnabled;

        public Factory(
                androidx.savedstate.SavedStateRegistryOwner owner,
                AuthRepository authRepository,
                ManagerRepository managerRepository,
                ManagerGuideCoordinator coordinator,
                CompanionRealtimeSubscriber realtimeSubscriber,
                boolean legacyManagerLocationEnabled
        ) {
            super(owner, null);
            this.authRepository = authRepository;
            this.managerRepository = managerRepository;
            this.coordinator = coordinator;
            this.realtimeSubscriber = realtimeSubscriber;
            this.legacyManagerLocationEnabled = legacyManagerLocationEnabled;
        }

        @androidx.annotation.NonNull
        @Override
        @SuppressWarnings("unchecked")
        protected <T extends ViewModel> T create(
                @androidx.annotation.NonNull String key,
                @androidx.annotation.NonNull Class<T> modelClass,
                @androidx.annotation.NonNull SavedStateHandle handle) {
            if (modelClass.isAssignableFrom(ManagerGuideViewModel.class)) {
                return (T) new ManagerGuideViewModel(
                        authRepository,
                        managerRepository,
                        coordinator,
                        realtimeSubscriber,
                        handle,
                        legacyManagerLocationEnabled);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}
