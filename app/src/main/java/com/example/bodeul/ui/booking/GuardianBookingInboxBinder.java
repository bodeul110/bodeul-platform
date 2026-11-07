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
public final class GuardianBookingInboxBinder {
    private final View root;
    private final Context context;
    private final GuardianBookingTextFormatter formatter;
    private final LinearLayout rows;
    private final MaterialCheckBox reviewed;
    private final MaterialCheckBox adult;
    private GuardianBookingInboxCoordinator coordinator;
    private State state;

    public GuardianBookingInboxBinder(View root) {
        this.root = root;
        context = root.getContext();
        formatter = new GuardianBookingTextFormatter(context);
        rows = root.findViewById(R.id.layoutGuardianBookingInboxRows);
        reviewed = root.findViewById(R.id.checkGuardianBookingReviewed);
        adult = root.findViewById(R.id.checkGuardianBookingAdult);
        reviewed.setOnCheckedChangeListener((button, checked) -> updateGrant());
        adult.setOnCheckedChangeListener((button, checked) -> updateGrant());
    }

    public void bindActions(GuardianBookingInboxCoordinator value) {
        coordinator = value;
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
        boolean list = state.phase == Phase.LIST || state.phase == Phase.LOADING_LIST
                || (state.phase == Phase.ERROR && !state.hasSelection);
        boolean closed = state.phase == Phase.CLOSED || state.phase == Phase.EMPTY;
        visible(R.id.scrollGuardianBookingInboxList, list);
        visible(R.id.guardianBookingReviewRoot, !list && !closed);
        visible(R.id.progressGuardianBookingInbox, state.phase == Phase.LOADING_LIST);
        visible(R.id.progressGuardianBooking, state.busy());
        text(R.id.textGuardianBookingInboxMessage, list || closed ? message() : "");
        text(R.id.textGuardianBookingMessage, !list && !closed ? message() : "");
        root.findViewById(R.id.buttonGuardianBookingInboxReload).setEnabled(!state.busy() && !closed);
        boolean patient = state.approval != null && state.approval.patient;
        visible(R.id.layoutGuardianBookingDetails, state.approval != null);
        visible(R.id.checkGuardianBookingReviewed, patient && state.detail != null);
        visible(R.id.checkGuardianBookingAdult, patient && state.detail != null);
        reviewed.setEnabled(state.canGrant);
        adult.setEnabled(state.canGrant);
        button(R.id.buttonGuardianBookingGrant, patient && state.detail != null, state.canGrant);
        button(R.id.buttonGuardianBookingRevoke, patient, state.canRevoke);
        button(R.id.buttonGuardianBookingRefresh, !list && !closed, !state.busy());
        visible(R.id.buttonGuardianBookingCreate, false);
        visible(R.id.buttonGuardianBookingRetry, false);
        updateGrant();

        text(R.id.textGuardianBookingParticipants, "");
        text(R.id.textGuardianBookingSummary, "");
        text(R.id.textGuardianBookingApproval, "");
        GuardianBookingStoredDraft detail = state.detail;
        if (detail != null) {
            text(R.id.textGuardianBookingParticipants, context.getString(R.string.guardian_booking_masked_participants,
                    detail.getPatient().getName(), detail.getPatient().getMaskedPhone(),
                    detail.getGuardian().getName(), detail.getGuardian().getMaskedPhone()));
            text(R.id.textGuardianBookingSummary, formatter.summary(detail.getRequest().getDraft()));
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
