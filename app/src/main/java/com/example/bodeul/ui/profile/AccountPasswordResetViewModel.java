package com.example.bodeul.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.AccountSignInInfo;

/** 회전 중에도 메일 발송을 중복 요청하지 않고 다른 계정의 완료 응답은 버린다. */
public final class AccountPasswordResetViewModel extends ViewModel {
    public enum Status { IDLE, SENDING, SENT, FAILED }
    private final MutableLiveData<Status> status = new MutableLiveData<>(Status.IDLE);
    private String userId = "";
    private int generation;
    public LiveData<Status> status() { return status; }
    public void bind(String id) {
        if (!userId.equals(id)) {
            userId = id;
            generation++;
            status.setValue(Status.IDLE);
        }
    }
    public void send(AuthRepository auth, AccountSignInInfo info) {
        if (!userId.equals(info.getUserId()) || !info.canResetPassword() || status.getValue() != Status.IDLE) return;
        int attempt = ++generation;
        status.setValue(Status.SENDING);
        auth.resetCurrentAccountPassword(info.getUserId(), info.getEmail(), new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void unused) { if (attempt == generation) status.setValue(Status.SENT); }
            @Override public void onError(String message) { if (attempt == generation) status.setValue(Status.FAILED); }
        });
    }
    public void acknowledgeNotice() {
        if (status.getValue() == Status.SENT || status.getValue() == Status.FAILED) status.setValue(Status.IDLE);
    }
    @Override protected void onCleared() { generation++; }
}
