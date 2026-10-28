package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator.Phase;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator.State;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 전체 원문을 생략하지 않고 표시하며 승인 체크를 조회·변경 뒤마다 초기화한다. */
public final class GuardianBookingReviewBinder {
    private final View root;
    private final Context context;
    private final MaterialCheckBox reviewed;
    private final MaterialCheckBox adult;
    private State state;

    public GuardianBookingReviewBinder(View root) {
        this.root = root;
        context = root.getContext();
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
                ? "" : summary(request.getDraft()));
        GuardianBookingApproval approval = state.review.approval;
        text(R.id.textGuardianBookingApproval, context.getString(R.string.guardian_booking_approval_info,
                approval.getVersion(), time(approval.getGrantedAt()), time(approval.getExpiresAt()),
                time(approval.getRevokedAt())));
    }

    private String summary(BookingRequestDraft draft) {
        return context.getString(R.string.guardian_booking_request_summary,
                value(draft.getLinkedParticipantName()), value(draft.getLinkedParticipantPhone()),
                value(draft.getLinkedParticipantEmail()), value(draft.getHospitalName()),
                value(draft.getDepartmentName()),
                Double.toString(draft.getHospitalLatitude()) + ", " + Double.toString(draft.getHospitalLongitude()),
                value(draft.getAppointmentAt()), value(draft.getMeetingPlace()),
                value(draft.getPatientConditionSummary()), value(draft.getMedicationSummary()),
                value(draft.getSpecialNotes()), context.getString(switch (draft.getMobilitySupport()) {
                    case INDEPENDENT -> R.string.guardian_booking_independent;
                    case WALKING_AID -> R.string.guardian_booking_walking_aid;
                    case WHEELCHAIR -> R.string.guardian_booking_wheelchair;
                }), context.getString(switch (draft.getTripType()) {
                    case ONE_WAY -> R.string.guardian_booking_one_way;
                    case ROUND_TRIP -> R.string.guardian_booking_round_trip;
                }), context.getString(switch (draft.getManagerGenderPreference()) {
                    case ANY -> R.string.guardian_booking_any_gender;
                    case FEMALE -> R.string.guardian_booking_female;
                    case MALE -> R.string.guardian_booking_male;
                }), context.getString(switch (draft.getPaymentMethod()) {
                    case BANK_TRANSFER -> R.string.guardian_booking_bank_transfer;
                    case CARD -> R.string.guardian_booking_card;
                    case EASY_PAY -> R.string.guardian_booking_easy_pay;
                    case ON_SITE -> R.string.guardian_booking_on_site;
                    case UNKNOWN -> R.string.guardian_booking_empty_value;
                }), String.format(Locale.KOREA, "%,d", draft.getPriceSummary().getFinalPrice()));
    }

    private String value(String value) {
        return value.isEmpty() ? context.getString(R.string.guardian_booking_empty_value) : value;
    }

    private String time(Instant time) {
        return time == null ? context.getString(R.string.guardian_booking_empty_value)
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                        .withZone(ZoneId.of("Asia/Seoul")).format(time);
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
