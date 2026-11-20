package com.example.bodeul.ui.booking;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.GuardianBookingDraftRepository.ApprovalStatus;
import com.example.bodeul.data.GuardianBookingDraftRepository.Identity;
import com.example.bodeul.data.GuardianBookingDraftRepository.ReceiptRow;
import com.example.bodeul.data.GuardianBookingRepository.CreateFailure;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.BookingHospitalSelection;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 화면의 계정·요청·조회 세대가 모두 같을 때만 원문과 승인 결과를 표시한다. */
@androidx.annotation.RequiresApi(26)
public final class GuardianBookingInboxCoordinator {
    public enum Phase { EMPTY, LOADING_LIST, LIST, LOADING_DETAIL, DETAIL, SAVING, LOADING_RECEIPTS, RECEIPTS,
        EDITING, CONFIRMING, SENDING_DRAFT, UNCERTAIN_DRAFT, DRAFT_SENT, ERROR, CLOSED }

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
    private Identity identity;
    private List<ReceiptRow> receipts = List.of();
    private BookingHospitalSelection hospital;
    private GuardianBookingRequest pending;
    private UUID receiptRequest;
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
                ready && approval.patient && approval.approval.canRevoke(), identity, receipts,
                ready && !approval.patient && !approval.revocationOnly && validDraft
                        && approval.approval.canCreateAt(clock.instant()), hospital, pending, receiptRequest != null);
    }

    public void refreshList() {
        if (!checkAccount() || writing()) return;
        clearData();
        long token = begin(Phase.LOADING_LIST);
        repository.loadIdentity(callback(token, self -> {
            if (self == null) { fail(R.string.guardian_booking_load_failed); return; }
            identity = self;
            loadRows(token);
        }, R.string.guardian_booking_load_failed));
    }

    private void loadRows(long token) {
        repository.listDrafts(callback(token, result -> {
            if (result == null || result.size() > 50) { fail(R.string.guardian_booking_load_failed); return; }
            HashSet<GuardianBookingDraftReference> seen = new HashSet<>();
            for (Summary row : result) {
                if (row == null || !identity.id.equals(identity.patient
                        ? row.getReference().getPatientId() : row.getReference().getGuardianId())
                        || !seen.add(row.getReference())) {
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
        if (!checkAccount() || (phase != Phase.LIST && phase != Phase.DRAFT_SENT) || reference == null
                || rows.stream().noneMatch(row -> row.getReference().equals(reference))) return;
        selected = reference;
        rows = List.of();
        refreshDetail();
    }

    public void refreshDetail() {
        if (!checkAccount() || selected == null || writing()) return;
        detail = null;
        approval = null;
        long token = begin(Phase.LOADING_DETAIL);
        GuardianBookingDraftReference reference = selected;
        repository.loadApproval(reference, callback(token, result -> {
            if (result == null || identity == null || identity.patient != result.patient) {
                fail(R.string.guardian_booking_load_failed); return;
            }
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

    public void startCompose(BookingHospitalSelection selection) {
        if (!checkAccount() || phase != Phase.LIST || identity == null || identity.patient
                || selection == null || selection.getHospitalName().isEmpty() || selection.getDepartmentName().isEmpty()) return;
        clearSelection();
        hospital = selection;
        begin(Phase.EDITING);
    }

    public void review(String patientCode, BookingRequestDraft draft) {
        if (!checkAccount() || phase != Phase.EDITING) return;
        try {
            String code = patientCode == null ? "" : patientCode.trim();
            UUID patient = UUID.fromString(code);
            if (!patient.toString().equalsIgnoreCase(code) || patient.equals(identity.id)) {
                throw new IllegalArgumentException();
            }
            if (!GuardianBookingForm.isValid(draft, hospital, clock)) {
                message = R.string.guardian_booking_invalid_fields;
                publish();
                return;
            }
            pending = new GuardianBookingRequest(patient, identity.id, UUID.randomUUID(), draft);
            begin(Phase.CONFIRMING);
        } catch (IllegalArgumentException invalid) {
            message = R.string.guardian_booking_invalid_patient_code;
            publish();
        }
    }

    public void edit() {
        if (!checkAccount() || phase != Phase.CONFIRMING) return;
        pending = null;
        begin(Phase.EDITING);
    }

    public void sendDraft() {
        if (!checkAccount() || (phase != Phase.CONFIRMING && phase != Phase.UNCERTAIN_DRAFT)) return;
        var request = pending;
        long token = begin(Phase.SENDING_DRAFT);
        repository.submitDraft(request, new RepositoryCallback<Summary>() {
            private void complete(Summary value) {
                if (token != generation || phase != Phase.SENDING_DRAFT || !checkAccount()) return;
                if (value == null || !GuardianBookingDraftReference.from(request).equals(value.getReference())) {
                    phase = Phase.UNCERTAIN_DRAFT;
                    message = R.string.guardian_booking_draft_uncertain;
                } else {
                    clearSelection();
                    rows = List.of(value);
                    phase = Phase.DRAFT_SENT;
                    message = R.string.guardian_booking_draft_sent;
                }
                publish();
            }
            @Override public void onSuccess(Summary result) { complete(result); }
            @Override public void onError(String ignored) { complete(null); }
        });
    }

    private boolean writing() { return phase == Phase.SAVING || phase == Phase.SENDING_DRAFT; }

    public void create() {
        if (!checkAccount() || !state().canCreate) return;
        var reference = selected;
        var current = approval.approval;
        long token = begin(Phase.SAVING);
        repository.createDraft(reference, current, callback(token, value -> {
            if (value == null || value.receipt == null) {
                fail(value == null || value.failure == CreateFailure.UNKNOWN ? R.string.guardian_booking_create_uncertain
                        : R.string.guardian_booking_create_rejected);
                return;
            }
            clearSelection();
            receiptRequest = reference.getRequestId();
            receipts = List.of(new ReceiptRow(reference.getRequestId(), value.receipt));
            phase = Phase.RECEIPTS;
            message = R.string.guardian_booking_submitted;
            publish();
        }, R.string.guardian_booking_create_uncertain));
    }

    public void refreshReceipts() {
        if (!checkAccount() || identity == null || identity.patient || writing()) return;
        UUID filter = selected == null ? receiptRequest : selected.getRequestId();
        clearSelection();
        receiptRequest = filter;
        long token = begin(Phase.LOADING_RECEIPTS);
        repository.listReceipts(filter, callback(token, result -> {
            if (result == null || result.size() > (filter == null ? 50 : 1)) { fail(R.string.guardian_booking_receipts_failed); return; }
            HashSet<UUID> seen = new HashSet<>();
            for (ReceiptRow row : result) {
                if (row == null || filter != null && !filter.equals(row.requestId) || !seen.add(row.requestId)) {
                    fail(R.string.guardian_booking_receipts_failed);
                    return;
                }
            }
            receipts = List.copyOf(result);
            phase = Phase.RECEIPTS;
            message = receipts.isEmpty() ? R.string.guardian_booking_receipts_empty
                    : filter == null ? R.string.guardian_booking_receipts_title : R.string.guardian_booking_receipt_match;
            publish();
        }, R.string.guardian_booking_receipts_failed));
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
        identity = null;
        clearSelection();
    }

    private void clearSelection() {
        receiptRequest = null;
        receipts = List.of();
        hospital = null;
        pending = null;
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
        public final Identity identity;
        public final List<ReceiptRow> receipts;
        public final boolean canCreate;
        public final BookingHospitalSelection hospital;
        public final GuardianBookingRequest pending;
        public final boolean singleReceipt;

        private State(Phase phase, List<Summary> rows, GuardianBookingStoredDraft detail,
                ApprovalStatus approval, int message, boolean hasSelection, boolean canGrant, boolean canRevoke,
                Identity identity, List<ReceiptRow> receipts, boolean canCreate,
                BookingHospitalSelection hospital, GuardianBookingRequest pending, boolean singleReceipt) {
            this.phase = phase;
            this.rows = rows;
            this.detail = detail;
            this.approval = approval;
            this.message = message;
            this.hasSelection = hasSelection;
            this.canGrant = canGrant;
            this.canRevoke = canRevoke;
            this.identity = identity;
            this.receipts = receipts;
            this.canCreate = canCreate;
            this.hospital = hospital;
            this.pending = pending;
            this.singleReceipt = singleReceipt;
        }

        public boolean busy() {
            return phase == Phase.LOADING_LIST || phase == Phase.LOADING_RECEIPTS
                    || phase == Phase.LOADING_DETAIL || phase == Phase.SAVING || phase == Phase.SENDING_DRAFT;
        }
    }
}
