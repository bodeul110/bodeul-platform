package com.example.bodeul.ui.booking;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.GuardianBookingDraftRepository.ApprovalStatus;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 화면의 계정·요청·조회 세대가 모두 같을 때만 원문과 승인 결과를 표시한다. */
public final class GuardianBookingInboxCoordinator {
    public enum Phase { EMPTY, LOADING_LIST, LIST, LOADING_DETAIL, DETAIL, SAVING, ERROR, CLOSED }

    private final GuardianBookingDraftRepository repository;
    private final Supplier<String> currentAccount;
    private final String account;
    private final Clock clock;
    private Consumer<State> listener;
    private Phase phase = Phase.EMPTY;
    private List<Summary> rows = List.of();
    private GuardianBookingDraftReference selected;
    private GuardianBookingStoredDraft detail;
    private ApprovalStatus approval;
    private int message;
    private long generation;

    public GuardianBookingInboxCoordinator(GuardianBookingDraftRepository repository,
            Supplier<String> currentAccount, Clock clock, Consumer<State> listener) {
        this.repository = Objects.requireNonNull(repository);
        this.currentAccount = Objects.requireNonNull(currentAccount);
        this.account = currentAccount.get();
        this.clock = Objects.requireNonNull(clock);
        this.listener = Objects.requireNonNull(listener);
    }

    public State state() {
        boolean ready = phase == Phase.DETAIL && approval != null;
        boolean validDraft = detail != null && clock.instant().isBefore(detail.getSummary().getExpiresAt());
        return new State(phase, rows, detail, approval, message, selected != null,
                ready && approval.patient && !approval.revocationOnly && validDraft,
                ready && approval.patient && approval.approval.canRevoke());
    }

    public void refreshList() {
        if (!checkAccount() || phase == Phase.SAVING) return;
        clearData();
        long token = begin(Phase.LOADING_LIST);
        repository.listDrafts(callback(token, result -> {
            if (result == null || result.size() > 50) { fail(R.string.guardian_booking_load_failed); return; }
            HashSet<GuardianBookingDraftReference> seen = new HashSet<>();
            for (Summary row : result) {
                if (row == null || !seen.add(row.getReference())) {
                    fail(R.string.guardian_booking_load_failed);
                    return;
                }
            }
            rows = List.copyOf(result);
            phase = Phase.LIST;
            message = rows.isEmpty() ? R.string.guardian_booking_inbox_empty : 0;
            publish();
        }, R.string.guardian_booking_load_failed));
    }

    public void select(GuardianBookingDraftReference reference) {
        if (!checkAccount() || phase != Phase.LIST || reference == null
                || rows.stream().noneMatch(row -> row.getReference().equals(reference))) return;
        selected = reference;
        rows = List.of();
        refreshDetail();
    }

    public void refreshDetail() {
        if (!checkAccount() || selected == null || phase == Phase.SAVING) return;
        detail = null;
        approval = null;
        long token = begin(Phase.LOADING_DETAIL);
        GuardianBookingDraftReference reference = selected;
        repository.loadApproval(reference, callback(token, result -> {
            if (result == null) { fail(R.string.guardian_booking_load_failed); return; }
            if (result.revocationOnly) {
                approval = result;
                phase = Phase.DETAIL;
                message = R.string.guardian_booking_preview_unavailable;
                publish();
                return;
            }
            // 승인 조회만 성공한 상태를 새 승인 가능한 화면으로 노출하지 않는다.
            repository.loadDraft(reference, callback(token, value -> {
                if (value == null || !reference.equals(value.getSummary().getReference())
                        || !clock.instant().isBefore(value.getSummary().getExpiresAt())) {
                    fail(R.string.guardian_booking_load_failed);
                    return;
                }
                detail = value;
                approval = result;
                phase = Phase.DETAIL;
                publish();
            }, R.string.guardian_booking_load_failed));
        }, R.string.guardian_booking_load_failed));
    }

    public void grant(boolean reviewed, boolean adultPatientConfirmed) {
        if (!checkAccount() || !state().canGrant) return;
        if (!reviewed || !adultPatientConfirmed) {
            message = R.string.guardian_booking_confirmation_required;
            publish();
            return;
        }
        ApprovalStatus previous = approval;
        long token = begin(Phase.SAVING);
        repository.grantDraft(selected, previous.approval.getVersion(), true, callback(token, value -> {
            if (value == null || value.getGrantId() == null || value.getRevokedAt() != null
                    || !value.canCreateAt(clock.instant())
                    || value.getVersion() != previous.approval.getVersion() + 1) {
                fail(R.string.guardian_booking_mutation_failed);
                return;
            }
            approval = new ApprovalStatus(value, true, false);
            phase = Phase.DETAIL;
            message = R.string.guardian_booking_granted;
            publish();
        }, R.string.guardian_booking_mutation_failed));
    }

    public void revoke() {
        if (!checkAccount() || !state().canRevoke) return;
        ApprovalStatus previous = approval;
        long token = begin(Phase.SAVING);
        repository.revokeDraft(selected, previous.approval.getVersion(), callback(token, value -> {
            if (value == null || value.getRevokedAt() == null
                    || !Objects.equals(value.getGrantId(), previous.approval.getGrantId())
                    || value.getVersion() != previous.approval.getVersion() + 1) {
                fail(R.string.guardian_booking_mutation_failed);
                return;
            }
            approval = new ApprovalStatus(value, true, previous.revocationOnly);
            phase = Phase.DETAIL;
            message = R.string.guardian_booking_revoked;
            publish();
        }, R.string.guardian_booking_mutation_failed));
    }

    public void accountChanged() { checkAccount(); }

    public void dispose() {
        generation++;
        clearData();
        phase = Phase.CLOSED;
        message = 0;
        publish();
        listener = null;
    }

    private boolean checkAccount() {
        if (phase == Phase.CLOSED) return false;
        if (account != null && !account.isEmpty() && account.equals(currentAccount.get())) return true;
        generation++;
        clearData();
        phase = Phase.CLOSED;
        message = R.string.guardian_booking_account_changed;
        publish();
        listener = null;
        return false;
    }

    private void clearData() {
        rows = List.of();
        detail = null;
        approval = null;
        selected = null;
    }

    private long begin(Phase next) {
        long token = ++generation;
        phase = next;
        message = 0;
        publish();
        return token;
    }

    private void fail(int reason) {
        rows = List.of();
        detail = null;
        approval = null;
        phase = Phase.ERROR;
        message = reason;
        publish();
    }

    private void publish() { if (listener != null) listener.accept(state()); }

    private <T> RepositoryCallback<T> callback(long token, Consumer<T> success, int failureMessage) {
        return new RepositoryCallback<T>() {
            private boolean delivered;

            private boolean accept() {
                if (delivered || token != generation || !checkAccount()) return false;
                delivered = true;
                return true;
            }

            @Override public void onSuccess(T result) { if (accept()) success.accept(result); }
            @Override public void onError(String ignored) { if (accept()) fail(failureMessage); }
        };
    }

    public static final class State {
        public final Phase phase;
        public final List<Summary> rows;
        public final GuardianBookingStoredDraft detail;
        public final ApprovalStatus approval;
        public final int message;
        public final boolean hasSelection;
        public final boolean canGrant;
        public final boolean canRevoke;

        private State(Phase phase, List<Summary> rows, GuardianBookingStoredDraft detail,
                ApprovalStatus approval, int message, boolean hasSelection, boolean canGrant, boolean canRevoke) {
            this.phase = phase;
            this.rows = rows;
            this.detail = detail;
            this.approval = approval;
            this.message = message;
            this.hasSelection = hasSelection;
            this.canGrant = canGrant;
            this.canRevoke = canRevoke;
        }

        public boolean busy() {
            return phase == Phase.LOADING_LIST || phase == Phase.LOADING_DETAIL || phase == Phase.SAVING;
        }
    }
}
