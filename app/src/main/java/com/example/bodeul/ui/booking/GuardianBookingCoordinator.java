package com.example.bodeul.ui.booking;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingRepository;
import com.example.bodeul.data.GuardianBookingRepository.Review;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;

/** 조회 성공 후 명시적 확인만 허용하고, 이전 화면의 지연 응답은 폐기한다. */
public final class GuardianBookingCoordinator {
    public enum Phase { EMPTY, LOADING, READY, SAVING, UNCERTAIN, SUBMITTED, ERROR, CLOSED }

    private final GuardianBookingRepository repository;
    private final Clock clock;
    private GuardianBookingRequest request;
    private Review review;
    private GuardianBookingApproval attemptedApproval;
    private GuardianBookingReceipt receipt;
    private Consumer<State> listener;
    private Phase phase = Phase.EMPTY;
    private int message;
    private long generation;

    public GuardianBookingCoordinator(GuardianBookingRepository repository, GuardianBookingRequest request,
            Clock clock, Consumer<State> listener) {
        this.repository = Objects.requireNonNull(repository);
        this.request = Objects.requireNonNull(request);
        this.clock = Objects.requireNonNull(clock);
        this.listener = Objects.requireNonNull(listener);
    }

    public State state() {
        boolean ready = phase == Phase.READY && review != null;
        return new State(phase, review, receipt, message,
                ready && review.patient && review.previewVerified,
                ready && review.patient && review.approval.canRevoke(),
                ready && !review.patient && review.approval.canCreateAt(clock.instant()));
    }

    public void refresh() {
        if (busy() || phase == Phase.CLOSED || phase == Phase.SUBMITTED || phase == Phase.UNCERTAIN) return;
        review = null;
        long token = begin(Phase.LOADING);
        repository.load(request, callback(token, value -> {
            if (value == null || !sameRequest(value.request)) {
                fail(R.string.guardian_booking_load_failed);
                return;
            }
            request = value.request;
            review = value;
            phase = Phase.READY;
            if (value.patient && !value.previewVerified) message = R.string.guardian_booking_preview_unavailable;
            publish();
        }, () -> fail(R.string.guardian_booking_load_failed)));
    }

    public void grant(boolean entireRequestReviewed, boolean adultPatientConfirmed) {
        if (!state().canGrant) return;
        if (!entireRequestReviewed || !adultPatientConfirmed) {
            message = R.string.guardian_booking_confirmation_required;
            publish();
            return;
        }
        Review previous = review;
        long token = begin(Phase.SAVING);
        repository.grant(request, previous.approval.getVersion(), true, callback(token, value -> {
            if (value == null || value.getGrantId() == null || value.getRevokedAt() != null
                    || value.getVersion() != previous.approval.getVersion() + 1) {
                fail(R.string.guardian_booking_mutation_failed);
                return;
            }
            review = new Review(request, value, true);
            phase = Phase.READY;
            message = R.string.guardian_booking_granted;
            publish();
        }, () -> fail(R.string.guardian_booking_mutation_failed)));
    }

    public void revoke() {
        if (!state().canRevoke) return;
        Review previous = review;
        long token = begin(Phase.SAVING);
        repository.revoke(request, previous.approval.getVersion(), callback(token, value -> {
            if (value == null || value.getRevokedAt() == null
                    || !Objects.equals(value.getGrantId(), previous.approval.getGrantId())
                    || value.getVersion() != previous.approval.getVersion() + 1) {
                fail(R.string.guardian_booking_mutation_failed);
                return;
            }
            review = new Review(request, value, true, previous.previewVerified);
            phase = Phase.READY;
            message = R.string.guardian_booking_revoked;
            publish();
        }, () -> fail(R.string.guardian_booking_mutation_failed)));
    }

    public void create() {
        if (!state().canCreate) return;
        attemptedApproval = review.approval;
        submitSameRequest();
    }

    public void retryCreate() {
        if (phase == Phase.UNCERTAIN && attemptedApproval != null) submitSameRequest();
    }

    private void submitSameRequest() {
        long token = begin(Phase.SAVING);
        repository.create(request, attemptedApproval, callback(token, value -> {
            if (value == null) {
                uncertain();
                return;
            }
            receipt = value;
            review = null;
            attemptedApproval = null;
            request = null;
            phase = Phase.SUBMITTED;
            message = R.string.guardian_booking_submitted;
            publish();
        }, this::uncertain));
    }

    private void uncertain() {
        review = null;
        phase = Phase.UNCERTAIN;
        message = R.string.guardian_booking_create_uncertain;
        publish();
    }

    private void fail(int errorMessage) {
        review = null;
        phase = Phase.ERROR;
        message = errorMessage;
        publish();
    }

    public void dispose() {
        generation++;
        review = null;
        receipt = null;
        attemptedApproval = null;
        request = null;
        listener = null;
        phase = Phase.CLOSED;
    }

    private boolean sameRequest(GuardianBookingRequest value) {
        return request.getPatientId().equals(value.getPatientId())
                && request.getGuardianId().equals(value.getGuardianId())
                && request.getRequestId().equals(value.getRequestId());
    }

    private long begin(Phase next) {
        long token = ++generation;
        phase = next;
        message = 0;
        publish();
        return token;
    }

    private boolean busy() { return phase == Phase.LOADING || phase == Phase.SAVING; }

    private void publish() {
        if (listener != null) listener.accept(state());
    }

    private <T> RepositoryCallback<T> callback(long token, Consumer<T> success, Runnable error) {
        return new RepositoryCallback<T>() {
            private boolean delivered;

            private boolean accept() {
                if (delivered || token != generation || phase == Phase.CLOSED) return false;
                delivered = true;
                return true;
            }

            @Override
            public void onSuccess(T value) { if (accept()) success.accept(value); }

            @Override
            public void onError(String ignored) { if (accept()) error.run(); }
        };
    }

    public static final class State {
        public final Phase phase;
        public final Review review;
        public final GuardianBookingReceipt receipt;
        public final int message;
        public final boolean canGrant;
        public final boolean canRevoke;
        public final boolean canCreate;

        private State(Phase phase, Review review, GuardianBookingReceipt receipt, int message,
                boolean canGrant, boolean canRevoke, boolean canCreate) {
            this.phase = phase;
            this.review = review;
            this.receipt = receipt;
            this.message = message;
            this.canGrant = canGrant;
            this.canRevoke = canRevoke;
            this.canCreate = canCreate;
        }
    }
}
