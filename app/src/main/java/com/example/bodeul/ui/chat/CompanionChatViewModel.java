package com.example.bodeul.ui.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.CompanionChatAttachmentUploader;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.realtime.CompanionRealtimeSubscriber;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.ui.navigation.ClientCompanionRoomEntryState;
import com.example.bodeul.ui.navigation.ClientBottomNavigationVisibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompanionChatViewModel extends ViewModel {
    private static final long GUARDIAN_POLL_INTERVAL_MILLIS = 15_000L;

    public enum StatePanelType { NONE, PERMISSION, AUTH, EMPTY, LOAD_ERROR }

    public static class UiState {
        public final boolean isLoading;
        @Nullable public final CompanionChatScreenModel screenModel;
        public final StatePanelType statePanelType;
        @Nullable public final String errorMessage;
        public final boolean requireProfileCompletion;
        public final boolean showClientNavigation;

        public UiState(boolean loading, @Nullable CompanionChatScreenModel screen, StatePanelType panel,
                @Nullable String error, boolean profile, boolean navigation) {
            isLoading = loading;
            screenModel = screen;
            statePanelType = panel;
            errorMessage = error;
            requireProfileCompletion = profile;
            showClientNavigation = navigation;
        }
        static UiState loading() { return new UiState(true, null, StatePanelType.NONE, null, false, false); }
    }

    private final MutableLiveData<UiState> uiState = new MutableLiveData<>(UiState.loading());
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<String> messageSentEvent = new MutableLiveData<>();
    private final AuthRepository authRepository;
    private final BookingRepository bookingRepository;
    private final ManagerRepository managerRepository;
    private final CompanionChatAttachmentUploader attachmentUploader;
    private final CompanionChatCoordinator coordinator;
    private final CompanionRealtimeSubscriber realtimeSubscriber;
    private final Handler pollingHandler = new Handler(Looper.getMainLooper());
    private final Runnable guardianPolling = this::refresh;
    @Nullable private User currentUser;
    @Nullable private String requestId;
    private String currentSessionId = "";
    private String subscribedSessionId = "";
    private boolean initialized;
    private boolean active;
    private boolean sending;
    private int generation;

    public CompanionChatViewModel(AuthRepository auth, BookingRepository bookings, ManagerRepository managers,
            CompanionChatAttachmentUploader uploader, CompanionChatCoordinator coordinator,
            CompanionRealtimeSubscriber subscriber) {
        authRepository = auth;
        bookingRepository = bookings;
        managerRepository = managers;
        attachmentUploader = uploader;
        this.coordinator = coordinator;
        realtimeSubscriber = subscriber;
    }

    public LiveData<UiState> getUiState() { return uiState; }
    public LiveData<String> getToastMessage() { return toastMessage; }
    public LiveData<String> getMessageSentEvent() { return messageSentEvent; }
    public void toastMessageHandled() { toastMessage.setValue(null); }
    public void messageSentHandled() { messageSentEvent.setValue(null); }

    public void start(@Nullable String requestedId) {
        if (!initialized) {
            requestId = requestedId;
            initialized = true;
        }
        active = true;
        reload();
    }

    public void stop() {
        active = false;
        generation++;
        stopUpdates();
    }

    public void reload() {
        if (!active) return;
        int expected = ++generation;
        currentUser = null;
        currentSessionId = "";
        stopUpdates();
        uiState.setValue(UiState.loading());
        authRepository.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (!accept(expected)) return;
                if (AuthFlowRouter.requiresProfileCompletion(user)) {
                    uiState.setValue(new UiState(false, null, StatePanelType.NONE, null, true, false));
                    return;
                }
                currentUser = user;
                if (user.getRole() == UserRole.MANAGER) loadManager(user);
                else if (ClientBottomNavigationVisibility.isVisibleFor(user.getRole())) {
                    if (TextUtils.isEmpty(requestId)) loadClientTarget(user); else loadBooking(user);
                } else panel(StatePanelType.PERMISSION, null);
            }
            @Override public void onError(String message) {
                if (accept(expected)) panel(StatePanelType.AUTH, null);
            }
        });
    }

    private boolean accept(int expected) { return active && expected == generation; }

    private void loadClientTarget(User user) {
        int expected = generation;
        bookingRepository.getMyAppointmentRequests(user, new RepositoryCallback<List<AppointmentRequest>>() {
            @Override public void onSuccess(List<AppointmentRequest> result) {
                if (!accept(expected)) return;
                ClientCompanionRoomEntryState target = ClientCompanionRoomEntryState.fromAuthorizedRequests(result);
                if (target.isEmpty()) { panel(StatePanelType.EMPTY, null); return; }
                requestId = target.getRequestId();
                loadBooking(user);
            }
            @Override public void onError(String message) {
                if (accept(expected)) panel(StatePanelType.LOAD_ERROR, message);
            }
        });
    }

    private void loadBooking(User user) {
        int expected = generation;
        bookingRepository.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override public void onSuccess(AppointmentRequestDetail result) {
                if (!accept(expected)) return;
                if (result == null || result.getSession() == null) { panel(StatePanelType.EMPTY, null); return; }
                currentSessionId = result.getSession().getId();
                publish(coordinator.createForBooking(user, result, bookingRepository.isFirebaseBacked()));
                ensureUpdates(result.getSession());
                bookingRepository.markCompanionChatRead(user, requestId);
            }
            @Override public void onError(String message) {
                if (!accept(expected)) return;
                panel(StatePanelType.LOAD_ERROR, message);
                scheduleGuardianPolling();
            }
        });
    }

    private void loadManager(User user) {
        int expected = generation;
        managerRepository.getManagerDashboard(user.getId(), new RepositoryCallback<ManagerDashboard>() {
            @Override public void onSuccess(ManagerDashboard result) {
                if (!accept(expected)) return;
                if (result == null || result.getSession() == null || result.getAppointmentRequest() == null) {
                    panel(StatePanelType.EMPTY, null); return;
                }
                String loadedRequest = result.getAppointmentRequest().getId();
                // 열었던 동행이 끝나고 다음 배정이 생겨도 기존 작성 내용을 다른 방에 보내지 않는다.
                if (!TextUtils.isEmpty(requestId) && !requestId.equals(loadedRequest)) {
                    panel(StatePanelType.EMPTY, null); return;
                }
                requestId = loadedRequest;
                currentSessionId = result.getSession().getId();
                publish(coordinator.createForManager(user, result, managerRepository.isFirebaseBacked()));
                ensureUpdates(result.getSession());
                managerRepository.markCompanionChatRead(user.getId());
            }
            @Override public void onError(String message) {
                if (accept(expected)) panel(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION.equals(message)
                        ? StatePanelType.EMPTY : StatePanelType.LOAD_ERROR, message);
            }
        });
    }

    public void sendMessage(String text, @Nullable List<CompanionChatPendingAttachment> pending) {
        UiState state = uiState.getValue();
        if (!active || sending || currentUser == null || state == null || state.isLoading
                || state.screenModel == null || !state.screenModel.isWritable()) return;
        String normalized = text == null ? "" : text.trim();
        List<CompanionChatPendingAttachment> files = pending == null ? Collections.emptyList() : new ArrayList<>(pending);
        if (normalized.isEmpty() && files.isEmpty()) {
            toastMessage.setValue("메시지나 첨부 파일을 준비해 주세요."); return;
        }
        sending = true;
        publish(state.screenModel);
        SendOperation operation = new SendOperation(currentUser, requestId, currentSessionId, normalized, files);
        uploadNext(operation);
    }

    // 비동기 업로드 도중 화면/계정이 바뀌어도 전송 대상은 시작 시점에 고정한다.
    private static final class SendOperation {
        final User user;
        final String requestId;
        final String sessionId;
        final String body;
        final List<CompanionChatPendingAttachment> pending;
        final List<CompanionChatAttachment> uploaded = new ArrayList<>();
        SendOperation(User user, String request, String session, String body, List<CompanionChatPendingAttachment> pending) {
            this.user = user; requestId = request; sessionId = session; this.body = body; this.pending = pending;
        }
    }

    private void uploadNext(SendOperation op) {
        if (op.uploaded.size() == op.pending.size()) { sendInternal(op); return; }
        if (TextUtils.isEmpty(op.sessionId)) { sendFailed(op, "첨부 파일 세션 정보를 확인하지 못했습니다."); return; }
        CompanionChatPendingAttachment file = op.pending.get(op.uploaded.size());
        attachmentUploader.uploadAttachment(op.sessionId, file.getFileUri(), new RepositoryCallback<CompanionChatAttachment>() {
            @Override public void onSuccess(CompanionChatAttachment attachment) {
                op.uploaded.add(attachment);
                uploadNext(op);
            }
            @Override public void onError(String message) { sendFailed(op, message); }
        });
    }

    private void sendInternal(SendOperation op) {
        if (!sameConversation(op) || !TextUtils.equals(currentSessionId, op.sessionId)) {
            sendFailed(op, "동행 정보를 다시 확인한 뒤 전송해 주세요.");
            return;
        }
        if (op.user.getRole() == UserRole.MANAGER) {
            managerRepository.sendCompanionChatMessage(op.user.getId(), op.body, op.uploaded, new RepositoryCallback<ManagerDashboard>() {
                @Override public void onSuccess(ManagerDashboard result) { sendSucceeded(op); }
                @Override public void onError(String message) { sendFailed(op, message); }
            });
        } else {
            bookingRepository.sendCompanionChatMessage(op.user, op.requestId, op.body, op.uploaded,
                    new RepositoryCallback<AppointmentRequestDetail>() {
                        @Override public void onSuccess(AppointmentRequestDetail result) { sendSucceeded(op); }
                        @Override public void onError(String message) { sendFailed(op, message); }
                    });
        }
    }

    private boolean sameConversation(SendOperation op) {
        return currentUser != null && op.user.getId().equals(currentUser.getId())
                && TextUtils.equals(op.requestId, requestId);
    }

    private void sendSucceeded(SendOperation op) {
        sending = false;
        messageSentEvent.setValue(op.user.getId() + ":" + op.requestId);
        refresh();
    }

    private void sendFailed(SendOperation op, String message) {
        sending = false;
        if (sameConversation(op)) toastMessage.setValue(message);
        UiState state = uiState.getValue();
        if (state != null && state.screenModel != null) publish(state.screenModel);
    }

    private void publish(CompanionChatScreenModel screen) {
        uiState.setValue(new UiState(sending, screen, StatePanelType.NONE, null, false, !screen.isManager()));
    }

    private void panel(StatePanelType type, @Nullable String error) {
        currentSessionId = "";
        stopUpdates();
        uiState.setValue(new UiState(false, null, type, error, false,
                currentUser != null && currentUser.getRole() != UserRole.MANAGER));
    }

    private void ensureUpdates(CompanionSession session) {
        if (!active) return;
        if (currentUser != null && currentUser.getRole() == UserRole.GUARDIAN) {
            realtimeSubscriber.stop();
            subscribedSessionId = "";
            scheduleGuardianPolling();
            return;
        }
        pollingHandler.removeCallbacks(guardianPolling);
        String id = session.getRealtimeSessionId();
        if (TextUtils.isEmpty(id)) { realtimeSubscriber.stop(); subscribedSessionId = ""; return; }
        if (id.equals(subscribedSessionId)) return;
        subscribedSessionId = id;
        realtimeSubscriber.subscribe(id, this::refresh);
    }

    private void scheduleGuardianPolling() {
        if (!active || currentUser == null || currentUser.getRole() != UserRole.GUARDIAN) return;
        pollingHandler.removeCallbacks(guardianPolling);
        pollingHandler.postDelayed(guardianPolling, GUARDIAN_POLL_INTERVAL_MILLIS);
    }

    private void refresh() {
        if (!active || currentUser == null) return;
        // 이전 조회 응답이 나중에 도착해 새 메시지/권한 상태를 덮지 못하게 한다.
        generation++;
        if (currentUser.getRole() == UserRole.MANAGER) loadManager(currentUser);
        else if (!TextUtils.isEmpty(requestId)) loadBooking(currentUser);
    }

    private void stopUpdates() {
        pollingHandler.removeCallbacks(guardianPolling);
        realtimeSubscriber.stop();
        subscribedSessionId = "";
    }

    @Override protected void onCleared() { stop(); super.onCleared(); }

    public static class Factory implements ViewModelProvider.Factory {
        private final AuthRepository auth;
        private final BookingRepository bookings;
        private final ManagerRepository managers;
        private final CompanionChatAttachmentUploader uploader;
        private final CompanionChatCoordinator coordinator;
        private final CompanionRealtimeSubscriber subscriber;

        public Factory(AuthRepository auth, BookingRepository bookings, ManagerRepository managers,
                CompanionChatAttachmentUploader uploader, CompanionChatCoordinator coordinator,
                CompanionRealtimeSubscriber subscriber) {
            this.auth = auth; this.bookings = bookings; this.managers = managers;
            this.uploader = uploader; this.coordinator = coordinator; this.subscriber = subscriber;
        }
        @NonNull @Override @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> type) {
            if (!type.isAssignableFrom(CompanionChatViewModel.class)) throw new IllegalArgumentException("지원하지 않는 화면 모델입니다.");
            return (T) new CompanionChatViewModel(auth, bookings, managers, uploader, coordinator, subscriber);
        }
    }
}
