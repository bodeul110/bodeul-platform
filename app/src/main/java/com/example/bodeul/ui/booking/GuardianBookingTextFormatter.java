package com.example.bodeul.ui.booking;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.BookingRequestDraft;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 직접 본문 화면과 서버 수신함에서 같은 예약 항목을 빠짐없이 표시한다. */
final class GuardianBookingTextFormatter {
    private final Context context;

    GuardianBookingTextFormatter(Context context) { this.context = context; }

    String summary(BookingRequestDraft draft) {
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

    String time(Instant time) {
        return time == null ? context.getString(R.string.guardian_booking_empty_value)
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
                        .withZone(ZoneId.of("Asia/Seoul")).format(time);
    }
}
