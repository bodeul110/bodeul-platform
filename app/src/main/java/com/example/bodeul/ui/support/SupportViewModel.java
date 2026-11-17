package com.example.bodeul.ui.support;

import androidx.annotation.NonNull;
import androidx.lifecycle.AbstractSavedStateViewModelFactory;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.savedstate.SavedStateRegistryOwner;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 문의 초안·조회·전송을 화면 재생성과 분리하고 이전 계정의 지연 응답을 차단한다. */
public final class SupportViewModel extends ViewModel {
    public enum Mode { LIST, COMPOSE, DETAIL }
    public enum Filter { ALL, WAITING, ANSWERED }
    public enum Gate { NONE, AUTH, PROFILE, PERMISSION, ERROR }
    private final AuthRepository auth;
    final SupportSource source;
    private final SavedStateHandle saved;
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    private List<SupportTicket> tickets = Collections.emptyList();
    private User user;
    private int generation, operation;
    private boolean active, loading, submitting, ready, recovery;
    private Gate gate = Gate.NONE;
    private int notice;
    private String contextId = "";

    public SupportViewModel(AuthRepository auth, SupportSource source, SavedStateHandle saved,
                            String appointmentId, String focusId) {
        this.auth = auth;
        this.source = source;
        this.saved = saved;
        recovery = Boolean.TRUE.equals(saved.get("pending"));
        if (!saved.contains("mode")) {
            saved.set("mode", text(focusId).isEmpty() ? Mode.LIST.name() : Mode.DETAIL.name());
            saved.set("appointment", text(appointmentId));
            saved.set("selected", text(focusId));
        }
    }

    public Mode mode() { return Mode.valueOf(value("mode", Mode.LIST.name())); }
    public Filter filter() { return Filter.valueOf(value("filter", Filter.ALL.name())); }
    public String title() { return value("title", ""); }
    public String body() { return value("body", ""); }
    public String category() { return value("category", ""); }
    public String appointmentId() { return value("appointment", ""); }
    public boolean isLoading() { return loading; }
    public boolean isSubmitting() { return submitting; }
    public boolean isReady() { return ready && active && gate == Gate.NONE; }
    public Gate gate() { return gate; }
    public boolean hasDraft() { return !title().isEmpty() || !body().isEmpty() || !category().isEmpty(); }
    public boolean canSubmit() { return isReady() && !submitting && !loading && source.hasCategory(category())
            && !title().trim().isEmpty() && !body().trim().isEmpty(); }
    public int consumeNotice() { int result = notice; notice = 0; return result; }

    public List<SupportTicket> visibleTickets() {
        List<SupportTicket> result = new ArrayList<>();
        for (SupportTicket ticket : tickets) if (filter() == Filter.ALL
                || (filter() == Filter.ANSWERED) == ticket.answered) result.add(ticket);
        return result;
    }

    public SupportTicket selectedTicket() {
        for (SupportTicket ticket : tickets) if (ticket.id.equals(value("selected", ""))) return ticket;
        return null;
    }

    public void start() { active = true; refresh(); }
    public void stop() { active = false; ready = false; generation++; }

    public void refresh() {
        if (!active) return;
        int token = ++generation;
        loading = true;
        ready = false;
        gate = Gate.NONE;
        changed();
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (!current(token)) return;
                if (!authenticate(result)) return;
                if (submitting) { loading = false; changed(); return; }
                source.validateContext(user, appointmentId(), new RepositoryCallback<String>() {
                    @Override public void onSuccess(String id) {
                        if (!current(token)) return;
                        contextId = id;
                        source.load(user, new RepositoryCallback<List<SupportTicket>>() {
                            @Override public void onSuccess(List<SupportTicket> result) {
                                if (!current(token)) return;
                                tickets = result;
                                loading = false;
                                ready = true;
                                if (recovery) {
                                    recovery = false;
                                    saved.set("pending", false);
                                    saved.set("mode", Mode.LIST.name());
                                    notice = R.string.final_support_check_submission;
                                }
                                if (mode() == Mode.DETAIL && selectedTicket() == null) {
                                    saved.set("mode", Mode.LIST.name());
                                    notice = R.string.final_support_not_found;
                                }
                                source.markRead(user, result);
                                changed();
                            }
                            @Override public void onError(String message) { if (current(token)) fail(Gate.ERROR); }
                        });
                    }
                    @Override public void onError(String message) { if (current(token)) fail(Gate.ERROR); }
                });
            }
            @Override public void onError(String message) { if (current(token)) fail(Gate.AUTH); }
        });
    }

    private boolean authenticate(User result) {
        if (result == null || result.getId() == null || result.getRole() == null) { fail(Gate.AUTH); return false; }
        String identity = result.getId() + ":" + result.getRole().name();
        String previous = value("owner", "");
        if (!previous.isEmpty() && !previous.equals(identity)) {
            clearDraft();
            saved.set("selected", "");
            saved.set("appointment", "");
            saved.set("mode", Mode.LIST.name());
            saved.set("pending", false);
            recovery = false;
            submitting = false;
            operation++;
            tickets = Collections.emptyList();
        }
        saved.set("owner", identity);
        user = result;
        if (!source.allows(result)) { fail(Gate.PERMISSION); return false; }
        if (text(result.getName()).trim().isEmpty() || text(result.getPhone()).trim().isEmpty()) {
            fail(Gate.PROFILE); return false;
        }
        return true;
    }

    public void setDraft(String title, String body) {
        if (submitting || !isReady()) return;
        saved.set("title", text(title));
        saved.set("body", text(body));
    }
    public void selectCategory(String value) {
        if (submitting || !isReady() || !source.hasCategory(value)) return;
        saved.set("category", value); changed();
    }
    public void filter(Filter value) { saved.set("filter", value.name()); changed(); }
    public void compose() { if (isReady() && !submitting) { saved.set("mode", Mode.COMPOSE.name()); changed(); } }
    public void open(String id) {
        if (!isReady() || submitting) return;
        saved.set("selected", id);
        if (selectedTicket() == null) return;
        saved.set("mode", Mode.DETAIL.name()); changed();
    }
    public void list(boolean discard) {
        if (submitting) return;
        if (discard) clearDraft();
        saved.set("mode", Mode.LIST.name());
        changed();
    }
    public void onPush(String id) {
        // 작성 중인 문의는 알림 수신만으로 닫거나 덮어쓰지 않는다.
        if (mode() != Mode.COMPOSE && !text(id).isEmpty()) {
            saved.set("selected", id);
            saved.set("mode", Mode.DETAIL.name());
        }
        notice = R.string.client_support_push_body_fallback;
        refresh();
    }

    public boolean submit() {
        if (!canSubmit()) return false;
        final User owner = user;
        final String title = title().trim(), body = body().trim(), category = category(), appointment = contextId;
        final int op = ++operation;
        submitting = true;
        generation++;
        changed();
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User result) {
                if (op != operation) return;
                if (!active) { submitting = false; changed(); return; }
                if (result == null || !Objects.equals(owner.getId(), result.getId()) || owner.getRole() != result.getRole()) {
                    submitting = false;
                    fail(Gate.AUTH);
                    return;
                }
                if (!authenticate(result)) { submitting = false; changed(); return; }
                saved.set("pending", true);
                source.submit(result, appointment, category, title, body, new RepositoryCallback<List<SupportTicket>>() {
                    @Override public void onSuccess(List<SupportTicket> result) { complete(op, true); }
                    @Override public void onError(String message) { complete(op, false); }
                });
            }
            @Override public void onError(String message) {
                if (op != operation) return;
                submitting = false;
                fail(Gate.AUTH);
            }
        });
        return true;
    }

    private void complete(int op, boolean success) {
        if (op != operation) return;
        submitting = false;
        saved.set("mode", Mode.LIST.name());
        if (success) {
            clearDraft();
            saved.set("pending", false);
            saved.set("filter", Filter.ALL.name());
            notice = R.string.client_support_submit_done;
        } else {
            // 저장 뒤 목록 조회만 실패했을 수 있어 자동 재전송하지 않는다.
            recovery = true;
        }
        if (active) refresh();
    }

    private void clearDraft() { saved.set("title", ""); saved.set("body", ""); saved.set("category", ""); }
    private void fail(Gate value) {
        loading = false; ready = false; gate = value; tickets = Collections.emptyList(); changed();
    }
    private boolean current(int token) { return active && token == generation; }
    private String value(String key, String fallback) { String result = saved.get(key); return result == null ? fallback : result; }
    private static String text(String value) { return value == null ? "" : value; }
    private void changed() { changes.setValue(changes.getValue() + 1); }

    static final class Factory extends AbstractSavedStateViewModelFactory {
        private final AuthRepository auth;
        private final SupportSource source;
        private final String appointmentId, focusId;
        Factory(SavedStateRegistryOwner owner, AuthRepository auth, SupportSource source, String appointmentId, String focusId) {
            super(owner, null);
            this.auth = auth; this.source = source; this.appointmentId = appointmentId; this.focusId = focusId;
        }
        @NonNull @Override @SuppressWarnings("unchecked")
        protected <T extends ViewModel> T create(@NonNull String key, @NonNull Class<T> type, @NonNull SavedStateHandle state) {
            return (T) new SupportViewModel(auth, source, state, appointmentId, focusId);
        }
    }
}
