package com.example.bodeul.ui.booking;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.booking.GuardianBookingInboxCoordinator.Phase;
import com.example.bodeul.ui.booking.GuardianBookingInboxCoordinator.State;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@androidx.annotation.RequiresApi(26)
final class GuardianBookingComposeBinder {
    private final View root;
    private final GuardianBookingTextFormatter formatter;
    private BookingHospitalSelection hospital;
    private LocalDateTime when;
    private Dialog picker;
    private Phase lastPhase;

    GuardianBookingComposeBinder(View root) {
        this.root = root;
        formatter = new GuardianBookingTextFormatter(root.getContext());
    }

    void bind(GuardianBookingInboxCoordinator coordinator) {
        root.findViewById(R.id.guardianFormWhen).setOnClickListener(v -> pickDate());
        root.findViewById(R.id.guardianFormReview).setOnClickListener(v -> {
            if (hospital != null) coordinator.review(input(R.id.guardianFormCode), draft());
        });
        root.findViewById(R.id.guardianFormEdit).setOnClickListener(v -> coordinator.edit());
        root.findViewById(R.id.guardianFormSend).setOnClickListener(v -> coordinator.sendDraft());
    }

    void render(State state) {
        boolean editing = state.phase == Phase.EDITING;
        boolean visible = editing || state.phase == Phase.CONFIRMING || state.phase == Phase.SENDING_DRAFT
                || state.phase == Phase.UNCERTAIN_DRAFT;
        ScrollView scroll = root.findViewById(R.id.guardianComposeRoot);
        scroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (lastPhase == Phase.EDITING && !editing) {
            var keyboard = root.getContext().getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
            root.findViewById(R.id.guardianComposeFields).clearFocus();
        }
        if (!visible && hospital != null) clear();
        if (visible) {
            hospital = state.hospital;
            text(R.id.guardianFormHospital).setText(root.getResources().getString(R.string.guardian_booking_form_hospital,
                    hospital.getHospitalName(), hospital.getDepartmentName()));
            root.findViewById(R.id.guardianComposeFields).setVisibility(editing ? View.VISIBLE : View.GONE);
            root.findViewById(R.id.guardianComposeConfirm).setVisibility(editing ? View.GONE : View.VISIBLE);
            text(R.id.guardianComposeMessage).setText(state.message == 0 ? "" : root.getResources().getString(state.message));
            text(R.id.guardianComposeSummary).setText(state.pending == null ? "" : root.getResources().getString(
                    R.string.guardian_booking_confirm_summary, state.pending.getPatientId(),
                    formatter.summary(state.pending.getDraft())));
            root.findViewById(R.id.guardianFormEdit).setVisibility(state.phase == Phase.CONFIRMING ? View.VISIBLE : View.GONE);
            text(R.id.guardianFormSend).setText(state.phase == Phase.UNCERTAIN_DRAFT
                    ? R.string.guardian_booking_retry_draft : R.string.guardian_booking_draft_send);
            root.findViewById(R.id.guardianFormSend).setEnabled(!state.busy());
            if (lastPhase != state.phase || state.message != 0) scroll.scrollTo(0, 0);
        }
        lastPhase = state.phase;
    }

    private BookingRequestDraft draft() {
        var mobility = BookingMobilitySupport.values()[index(R.id.guardianFormMobility)];
        var trip = BookingTripType.values()[index(R.id.guardianFormTrip)];
        var payment = switch (index(R.id.guardianFormPayment)) {
            case 1 -> BookingPaymentMethod.BANK_TRANSFER;
            case 2 -> BookingPaymentMethod.CARD;
            case 3 -> BookingPaymentMethod.EASY_PAY;
            case 4 -> BookingPaymentMethod.ON_SITE;
            default -> BookingPaymentMethod.UNKNOWN;
        };
        return BookingRequestDraft.builder().linkedParticipantName(input(R.id.guardianFormName))
                .linkedParticipantPhone(input(R.id.guardianFormPhone)).hospitalName(hospital.getHospitalName())
                .departmentName(hospital.getDepartmentName()).hospitalLatitude(hospital.getHospitalLatitude())
                .hospitalLongitude(hospital.getHospitalLongitude()).appointmentAt(when == null ? "" : GuardianBookingForm.TIME.format(when))
                .meetingPlace(input(R.id.guardianFormMeeting)).patientConditionSummary(input(R.id.guardianFormCondition))
                .medicationSummary(input(R.id.guardianFormMedication)).specialNotes(input(R.id.guardianFormNotes))
                .mobilitySupport(mobility).tripType(trip)
                .managerGenderPreference(BookingManagerGenderPreference.values()[index(R.id.guardianFormGender)])
                .paymentMethod(payment).priceSummary(new BookingPriceEstimator().estimate(trip, mobility, BookingCouponType.NONE)).build();
    }

    private void pickDate() {
        LocalDateTime initial = when == null ? LocalDate.now(GuardianBookingForm.SEOUL)
                .plusDays(1).atTime(9, 0) : when;
        var date = new DatePickerDialog(root.getContext(), (d, year, month, day) -> {
            if (hospital == null || lastPhase != Phase.EDITING) return;
            picker = new TimePickerDialog(root.getContext(), (t, hour, minute) -> {
                if (hospital == null || lastPhase != Phase.EDITING) return;
                when = LocalDateTime.of(LocalDate.of(year, month + 1, day), LocalTime.of(hour, minute));
                text(R.id.guardianFormWhen).setText(GuardianBookingForm.TIME.format(when));
            }, initial.getHour(), initial.getMinute(), true);
            picker.show();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        picker = date;
        date.show();
    }

    private void clear() {
        if (picker != null) picker.dismiss();
        picker = null;
        hospital = null;
        when = null;
        for (int id : new int[]{R.id.guardianFormCode, R.id.guardianFormName, R.id.guardianFormPhone,
                R.id.guardianFormMeeting, R.id.guardianFormCondition, R.id.guardianFormMedication,
                R.id.guardianFormNotes, R.id.guardianComposeSummary, R.id.guardianFormHospital, R.id.guardianComposeMessage}) {
            text(id).setText("");
        }
        for (int id : new int[]{R.id.guardianFormMobility, R.id.guardianFormTrip, R.id.guardianFormGender, R.id.guardianFormPayment}) {
            ((Spinner) root.findViewById(id)).setSelection(0);
        }
        text(R.id.guardianFormWhen).setText(R.string.guardian_booking_form_when);
    }

    private TextView text(int id) { return root.findViewById(id); }
    private String input(int id) { return ((EditText) root.findViewById(id)).getText().toString().trim(); }
    private int index(int id) { return ((Spinner) root.findViewById(id)).getSelectedItemPosition(); }
}
