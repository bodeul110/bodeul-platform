package com.example.bodeul.ui.booking;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.User;

import java.util.Objects;

/** 인증 재확인부터 저장 결과 수신까지 Activity를 참조하지 않고 추적한다. */
public final class BookingSubmissionViewModel extends ViewModel {
    static final String PENDING = "booking_submission.pending";
    private static final String OWNER = "booking_submission.owner";
    private static final String ROLE = "booking_submission.role";
    private final AuthRepository auth;
    private final BookingRepository booking;
    private final SavedStateHandle savedState;
    private final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private boolean running;
    private boolean recoveryRequired;
    @Nullable private Result result;

    public static final class Result {
        @Nullable public final AppointmentRequest request;
        public final boolean updated;
        public final boolean authFailed;
        @Nullable public final String error;

        private Result(@Nullable AppointmentRequest request, boolean updated, boolean authFailed, @Nullable String error) {
            this.request = request;
            this.updated = updated;
            this.authFailed = authFailed;
            this.error = error;
        }
    }

    public BookingSubmissionViewModel(AuthRepository auth, BookingRepository booking, SavedStateHandle savedState) {
        this.auth = auth;
        this.booking = booking;
        this.savedState = savedState;
        recoveryRequired = Boolean.TRUE.equals(savedState.get(PENDING));
    }

    AuthRepository authRepository() { return auth; }
    BookingRepository bookingRepository() { return booking; }
    LiveData<Integer> changes() { return changes; }
    public boolean isBusy() { return running || recoveryRequired || result != null; }
    public boolean requiresRecovery() { return recoveryRequired; }

    public boolean submit(User owner, @Nullable String editId, BookingRequestDraft draft) {
        BookingMutationPolicy.Operation operation = editId == null
                ? BookingMutationPolicy.Operation.CREATE : BookingMutationPolicy.Operation.UPDATE;
        if (isBusy() || !BookingMutationPolicy.isAllowed(owner.getRole(), operation)) return false;
        savedState.set(OWNER, owner.getId());
        savedState.set(ROLE, owner.getRole().name());
        savedState.set(PENDING, true);
        running = true;
        changed();
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (!matchesOwner(user) || user.getName() == null || user.getName().isEmpty()
                        || user.getPhone() == null || user.getPhone().isEmpty()) {
                    complete(new Result(null, editId != null, true, null));
                    return;
                }
                RepositoryCallback<AppointmentRequest> callback = new RepositoryCallback<AppointmentRequest>() {
                    @Override public void onSuccess(AppointmentRequest request) {
                        complete(new Result(request, editId != null, false, null));
                    }
                    @Override public void onError(String message) {
                        complete(new Result(null, editId != null, false, message));
                    }
                };
                BookingMutationPolicy.runIfAllowed(user.getRole(), operation, () -> {
                    if (editId == null) booking.createAppointmentRequest(user, draft, callback);
                    else booking.updateAppointmentRequest(user, editId, draft, callback);
                });
            }
            @Override public void onError(String message) { complete(new Result(null, editId != null, true, null)); }
        });
        return true;
    }

    @Nullable public Result consumeResult(User currentUser) {
        if (result == null) return null;
        Result consumed = matchesOwner(currentUser) ? result : null;
        reset();
        return consumed;
    }

    void discardRecoveryForOtherOwner(User currentUser) {
        if (recoveryRequired && !matchesOwner(currentUser)) reset();
    }

    void acknowledgeRecovery() {
        if (recoveryRequired) reset();
    }

    private boolean matchesOwner(User user) {
        return Objects.equals(user.getId(), savedState.get(OWNER))
                && Objects.equals(user.getRole().name(), savedState.get(ROLE));
    }

    private void complete(Result value) {
        if (!running) return;
        running = false;
        result = value;
        // 화면이 결과를 소비하기 전 프로세스가 종료돼도 같은 초안을 자동 재전송하지 않는다.
        changed();
    }

    private void reset() {
        result = null;
        recoveryRequired = false;
        savedState.set(PENDING, false);
        savedState.remove(OWNER);
        savedState.remove(ROLE);
        changed();
    }

    private void changed() { changes.setValue(changes.getValue() + 1); }

    static final class Factory extends AbstractSavedStateViewModelFactory {
        private final AuthRepository auth;
        private final BookingRepository booking;

        Factory(SavedStateRegistryOwner owner, AuthRepository auth, BookingRepository booking) {
            super(owner, null);
            this.auth = auth;
            this.booking = booking;
        }

        @NonNull @Override @SuppressWarnings("unchecked")
        protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> type, @NonNull SavedStateHandle state) {
            if (type != BookingSubmissionViewModel.class) throw new IllegalArgumentException("지원하지 않는 예약 상태 모델입니다.");
            return (T) new BookingSubmissionViewModel(auth, booking, state);
        }
    }
}
