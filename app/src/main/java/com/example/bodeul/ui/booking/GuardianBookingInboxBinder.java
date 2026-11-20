package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;
import com.example.bodeul.ui.booking.GuardianBookingInboxCoordinator.Phase;
import com.example.bodeul.ui.booking.GuardianBookingInboxCoordinator.State;
import com.google.android.material.checkbox.MaterialCheckBox;

/** 상세 실패·계정 종료 시 숨기기뿐 아니라 표시 문자열과 확인 체크도 지운다. */
@androidx.annotation.RequiresApi(26)
public final class GuardianBookingInboxBinder {
    private final View root;
    private final Context context;
    private final GuardianBookingTextFormatter formatter;
    private final GuardianBookingComposeBinder compose;
    private final LinearLayout rows;
    private final MaterialCheckBox reviewed;
    private final MaterialCheckBox adult;
    private GuardianBookingInboxCoordinator coordinator;
    private State state;

    public GuardianBookingInboxBinder(View root) {
        this.root = root;
        context = root.getContext();
        formatter = new GuardianBookingTextFormatter(context);
        compose = new GuardianBookingComposeBinder(root);
        rows = root.findViewById(R.id.layoutGuardianBookingInboxRows);
        reviewed = root.findViewById(R.id.checkGuardianBookingReviewed);
        adult = root.findViewById(R.id.checkGuardianBookingAdult);
        reviewed.setOnCheckedChangeListener((button, checked) -> updateGrant());
        adult.setOnCheckedChangeListener((button, checked) -> updateGrant());
    }

    public void bindActions(GuardianBookingInboxCoordinator value) {
        coordinator = value;
        compose.bind(coordinator);
        root.findViewById(R.id.buttonGuardianBookingOwnCode).setOnClickListener(view -> {
            coordinator.accountChanged();
            if (state.phase != Phase.LIST || state.identity == null || !state.identity.patient) return;
            text(R.id.textGuardianBookingOwnCode, context.getString(R.string.guardian_booking_patient_code, state.identity.id));
            visible(R.id.textGuardianBookingOwnCode, true);
        });
        root.findViewById(R.id.buttonGuardianBookingReceipts).setOnClickListener(view -> coordinator.refreshReceipts());
        root.findViewById(R.id.buttonGuardianBookingRetry).setOnClickListener(view -> coordinator.refreshReceipts());
        root.findViewById(R.id.buttonGuardianBookingCreate).setOnClickListener(view -> coordinator.create());
        root.findViewById(R.id.buttonGuardianBookingInboxReload).setOnClickListener(view -> coordinator.refreshList());
        root.findViewById(R.id.buttonGuardianBookingRefresh).setOnClickListener(view -> coordinator.refreshDetail());
        root.findViewById(R.id.buttonGuardianBookingGrant).setOnClickListener(view ->
                coordinator.grant(reviewed.isChecked(), adult.isChecked()));
        root.findViewById(R.id.buttonGuardianBookingRevoke).setOnClickListener(view -> coordinator.revoke());
    }

    public void render(State value) {
        state = value;
        reviewed.setChecked(false);
        adult.setChecked(false);
        rows.removeAllViews();
        compose.render(value);
        boolean composing = state.hospital != null;
        boolean list = state.phase == Phase.LIST || state.phase == Phase.DRAFT_SENT || state.phase == Phase.LOADING_LIST
                || state.phase == Phase.RECEIPTS || state.phase == Phase.LOADING_RECEIPTS
                || (state.phase == Phase.ERROR && !state.hasSelection);
        boolean closed = state.phase == Phase.CLOSED || state.phase == Phase.EMPTY;
        visible(R.id.scrollGuardianBookingInboxList, list);
        visible(R.id.guardianBookingReviewRoot, !list && !closed && !composing);
        visible(R.id.progressGuardianBookingInbox, state.phase == Phase.LOADING_LIST || state.phase == Phase.LOADING_RECEIPTS);
        visible(R.id.progressGuardianBooking, state.busy());
        text(R.id.textGuardianBookingInboxMessage, list || closed ? message() : "");
        text(R.id.textGuardianBookingMessage, !list && !closed ? message() : "");
        ((TextView) root.findViewById(R.id.buttonGuardianBookingInboxReload)).setText(composing
                ? R.string.guardian_booking_back_inbox : R.string.guardian_booking_inbox_reload);
        root.findViewById(R.id.buttonGuardianBookingInboxReload).setEnabled(!state.busy() && !closed);
        boolean guardian = state.identity != null && !state.identity.patient;
        button(R.id.buttonGuardianBookingCompose, guardian && state.phase == Phase.LIST, !state.busy());
        button(R.id.buttonGuardianBookingOwnCode, state.identity != null && state.identity.patient && state.phase == Phase.LIST, true);
        button(R.id.buttonGuardianBookingReceipts, guardian && list && !state.busy(), true);
        ((TextView) root.findViewById(R.id.buttonGuardianBookingReceipts)).setText(state.singleReceipt
                ? R.string.guardian_booking_check_one : R.string.guardian_booking_receipts_title);
        text(R.id.textGuardianBookingOwnCode, "");
        visible(R.id.textGuardianBookingOwnCode, false);
        boolean patient = state.approval != null && state.approval.patient;
        visible(R.id.layoutGuardianBookingDetails, state.approval != null);
        visible(R.id.checkGuardianBookingReviewed, patient && state.detail != null);
        visible(R.id.checkGuardianBookingAdult, patient && state.detail != null);
        reviewed.setEnabled(state.canGrant);
        adult.setEnabled(state.canGrant);
        button(R.id.buttonGuardianBookingGrant, patient && state.detail != null, state.canGrant);
        button(R.id.buttonGuardianBookingRevoke, patient, state.canRevoke);
        button(R.id.buttonGuardianBookingRefresh, !list && !closed, !state.busy());
        button(R.id.buttonGuardianBookingCreate, state.canCreate, state.canCreate);
        button(R.id.buttonGuardianBookingRetry, guardian && !list && !closed && !composing, !state.busy());
        ((TextView) root.findViewById(R.id.buttonGuardianBookingRetry)).setText(R.string.guardian_booking_check_one);
        updateGrant();

        text(R.id.textGuardianBookingParticipants, "");
        text(R.id.textGuardianBookingSummary, "");
        text(R.id.textGuardianBookingApproval, "");
        GuardianBookingStoredDraft detail = state.detail;
        if (detail != null) {
            text(R.id.textGuardianBookingParticipants, context.getString(R.string.guardian_booking_masked_participants,
                    detail.getPatient().getName(), detail.getPatient().getMaskedPhone(),
                    detail.getGuardian().getName(), detail.getGuardian().getMaskedPhone()));
            text(R.id.textGuardianBookingSummary, context.getString(R.string.guardian_booking_receipt_request,
                    detail.getRequest().getRequestId()) + "\n" + formatter.summary(detail.getRequest().getDraft()));
        }
        if (state.approval != null) {
            GuardianBookingApproval approval = state.approval.approval;
            text(R.id.textGuardianBookingApproval, context.getString(R.string.guardian_booking_approval_info,
                    approval.getVersion(), formatter.time(approval.getGrantedAt()),
                    formatter.time(approval.getExpiresAt()), formatter.time(approval.getRevokedAt())));
        }
        int index = 0;
        for (Summary summary : state.rows) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_guardian_booking_draft, rows, false);
            ((TextView) row.findViewById(R.id.textGuardianBookingInboxRowTitle)).setText(
                    context.getString(R.string.guardian_booking_inbox_row_title, ++index));
            ((TextView) row.findViewById(R.id.textGuardianBookingInboxRowDates)).setText(
                    context.getString(R.string.guardian_booking_inbox_row_dates,
                            formatter.time(summary.getCreatedAt()), formatter.time(summary.getExpiresAt())));
            row.setOnClickListener(view -> coordinator.select(summary.getReference()));
            rows.addView(row);
        }
        for (var item : state.receipts) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_guardian_booking_draft, rows, false);
            ((TextView) row.findViewById(R.id.textGuardianBookingInboxRowTitle)).setText(context.getString(
                    R.string.guardian_booking_receipt, item.receipt.getPublicCode()));
            ((TextView) row.findViewById(R.id.textGuardianBookingInboxRowDates)).setText(context.getString(
                    R.string.guardian_booking_receipt_request, item.requestId));
            row.setClickable(false);
            row.setFocusable(false);
            row.setBackground(null);
            row.findViewById(R.id.guardianInboxRowChevron).setVisibility(View.GONE);
            rows.addView(row);
        }
    }

    private String message() { return state.message == 0 ? "" : context.getString(state.message); }
    private void updateGrant() {
        root.findViewById(R.id.buttonGuardianBookingGrant).setEnabled(state != null && state.canGrant
                && reviewed.isChecked() && adult.isChecked());
    }
    private void text(int id, String text) { ((TextView) root.findViewById(id)).setText(text); }
    private void visible(int id, boolean visible) {
        root.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    private void button(int id, boolean visible, boolean enabled) {
        visible(id, visible);
        root.findViewById(id).setEnabled(enabled);
    }
}
