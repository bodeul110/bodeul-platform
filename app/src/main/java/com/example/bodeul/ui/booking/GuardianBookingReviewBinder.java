package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator.Phase;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator.State;
import com.google.android.material.checkbox.MaterialCheckBox;

/** 전체 원문을 생략하지 않고 표시하며 승인 체크를 조회·변경 뒤마다 초기화한다. */
public final class GuardianBookingReviewBinder {
    private final View root;
    private final Context context;
    private final GuardianBookingTextFormatter formatter;
    private final MaterialCheckBox reviewed;
    private final MaterialCheckBox adult;
    private State state;

    public GuardianBookingReviewBinder(View root) {
        this.root = root;
        context = root.getContext();
        formatter = new GuardianBookingTextFormatter(context);
        reviewed = root.findViewById(R.id.checkGuardianBookingReviewed);
        adult = root.findViewById(R.id.checkGuardianBookingAdult);
        reviewed.setOnCheckedChangeListener((button, checked) -> updateGrantButton());
        adult.setOnCheckedChangeListener((button, checked) -> updateGrantButton());
    }

    public void bindActions(GuardianBookingCoordinator coordinator) {
        root.findViewById(R.id.buttonGuardianBookingGrant).setOnClickListener(view ->
                coordinator.grant(reviewed.isChecked(), adult.isChecked()));
        root.findViewById(R.id.buttonGuardianBookingRevoke).setOnClickListener(view -> coordinator.revoke());
        root.findViewById(R.id.buttonGuardianBookingCreate).setOnClickListener(view -> coordinator.create());
        root.findViewById(R.id.buttonGuardianBookingRetry).setOnClickListener(view -> coordinator.retryCreate());
        root.findViewById(R.id.buttonGuardianBookingRefresh).setOnClickListener(view -> coordinator.refresh());
    }

    public void render(State state) {
        this.state = state;
        reviewed.setChecked(false);
        adult.setChecked(false);
        boolean busy = state.phase == Phase.LOADING || state.phase == Phase.SAVING;
        visible(R.id.progressGuardianBooking, busy);
        visible(R.id.layoutGuardianBookingDetails, state.review != null);
        boolean patient = state.review != null && state.review.patient;
        visible(R.id.checkGuardianBookingReviewed, patient);
        visible(R.id.checkGuardianBookingAdult, patient);
        reviewed.setEnabled(state.canGrant);
        adult.setEnabled(state.canGrant);
        button(R.id.buttonGuardianBookingGrant, patient, state.canGrant);
        button(R.id.buttonGuardianBookingRevoke, patient, state.canRevoke);
        button(R.id.buttonGuardianBookingCreate, state.review != null && !patient, state.canCreate);
        button(R.id.buttonGuardianBookingRetry, state.phase == Phase.UNCERTAIN, !busy);
        button(R.id.buttonGuardianBookingRefresh,
                state.phase != Phase.UNCERTAIN && state.phase != Phase.SUBMITTED, !busy);
        updateGrantButton();

        String message = state.message == 0 ? "" : context.getString(state.message);
        if (state.receipt != null) message += "\n" + context.getString(
                R.string.guardian_booking_receipt, state.receipt.getPublicCode());
        text(R.id.textGuardianBookingMessage, message);
        if (state.review == null) {
            text(R.id.textGuardianBookingParticipants, "");
            text(R.id.textGuardianBookingSummary, "");
            text(R.id.textGuardianBookingApproval, "");
            return;
        }
        GuardianBookingRequest request = state.review.request;
        text(R.id.textGuardianBookingParticipants, context.getString(R.string.guardian_booking_participants,
                request.getPatientId(), request.getGuardianId(), request.getRequestId()));
        text(R.id.textGuardianBookingSummary, patient && !state.review.previewVerified
                ? "" : formatter.summary(request.getDraft()));
        GuardianBookingApproval approval = state.review.approval;
        text(R.id.textGuardianBookingApproval, context.getString(R.string.guardian_booking_approval_info,
                approval.getVersion(), formatter.time(approval.getGrantedAt()), formatter.time(approval.getExpiresAt()),
                formatter.time(approval.getRevokedAt())));
    }

    private void updateGrantButton() {
        root.findViewById(R.id.buttonGuardianBookingGrant).setEnabled(state != null && state.canGrant
                && reviewed.isChecked() && adult.isChecked());
    }

    private void text(int id, String value) { ((TextView) root.findViewById(id)).setText(value); }
    private void visible(int id, boolean show) {
        root.findViewById(id).setVisibility(show ? View.VISIBLE : View.GONE);
    }
    private void button(int id, boolean show, boolean enabled) {
        visible(id, show);
        root.findViewById(id).setEnabled(enabled);
    }
}
