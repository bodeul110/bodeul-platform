package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 사용자 예약 이력 화면에 필요한 표시 모델을 만든다.
 */
public final class ClientBookingHistoryCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;

    public ClientBookingHistoryCoordinator(Context context, BookingPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
    }

    public ClientBookingHistoryScreenModel createScreenModel(
            User currentUser,
            List<AppointmentRequest> requests,
            boolean firebaseBacked
    ) {
        return new ClientBookingHistoryScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.client_booking_history_title),
                context.getString(
                        currentUser.getRole() == UserRole.GUARDIAN
                                ? R.string.client_booking_history_subtitle_guardian
                                : R.string.client_booking_history_subtitle_patient
                ),
                context.getString(R.string.client_booking_history_badge, requests.size()),
                context.getString(R.string.client_booking_history_hero_title, currentUser.getName()),
                buildHeroBody(requests),
                context.getString(R.string.client_booking_history_list_title),
                context.getString(R.string.client_booking_history_list_helper),
                context.getString(R.string.client_booking_history_action_manage),
                createEntryModels(currentUser, requests)
        );
    }

    private String buildHeroBody(List<AppointmentRequest> requests) {
        int activeCount = 0;
        int completedCount = 0;
        int canceledCount = 0;
        for (AppointmentRequest request : requests) {
            if (request.getStatus() == AppointmentStatus.COMPLETED) {
                completedCount++;
            } else if (request.getStatus() == AppointmentStatus.CANCELED) {
                canceledCount++;
            } else {
                activeCount++;
            }
        }
        return context.getString(
                R.string.client_booking_history_hero_body,
                activeCount,
                completedCount,
                canceledCount
        );
    }

    private List<ClientBookingHistoryEntryModel> createEntryModels(User currentUser, List<AppointmentRequest> requests) {
        List<ClientBookingHistoryEntryModel> items = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            boolean completed = request.getStatus() == AppointmentStatus.COMPLETED;
            items.add(new ClientBookingHistoryEntryModel(
                    request.getId(),
                    formatter.toStatusLabel(request.getStatus()),
                    resolveStatusBackgroundColor(request.getStatus()),
                    resolveStatusTextColor(request.getStatus()),
                    resolveStatusStrokeColor(request.getStatus()),
                    valueOrFallback(request.getHospitalName(), R.string.final_history_hospital_unknown),
                    formatAppointmentAt(request.getAppointmentAt()),
                    valueOrFallback(request.getMeetingPlace(), R.string.final_history_meeting_place_unknown),
                    context.getString(
                            R.string.final_history_patient_department,
                            resolvePatientName(currentUser, request),
                            valueOrFallback(request.getDepartmentName(), R.string.final_history_department_unknown)
                    ),
                    "",
                    "",
                    "",
                    "",
                    completed,
                    completed && currentUser.getRole() == UserRole.PATIENT
            ));
        }
        return items;
    }

    private String resolvePatientName(User currentUser, AppointmentRequest request) {
        if (!TextUtils.isEmpty(request.getPatientName())) {
            return request.getPatientName().trim();
        }
        if (currentUser.getRole() == UserRole.PATIENT && !TextUtils.isEmpty(currentUser.getName())) {
            return currentUser.getName().trim();
        }
        return context.getString(R.string.final_history_patient_unknown);
    }

    private String formatAppointmentAt(String appointmentAt) {
        BookingCompletionDateTime.DisplayValue displayValue =
                BookingCompletionDateTime.format(appointmentAt, Locale.KOREA);
        if (TextUtils.isEmpty(displayValue.getDate())) {
            return context.getString(R.string.final_history_appointment_unknown);
        }
        if (TextUtils.isEmpty(displayValue.getTime())) {
            return displayValue.getDate();
        }
        return displayValue.getDate() + " · " + displayValue.getTime();
    }

    private String valueOrFallback(String value, int fallbackStringResId) {
        return TextUtils.isEmpty(value) ? context.getString(fallbackStringResId) : value.trim();
    }

    private String buildOptionLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getTripTypeCode()) && TextUtils.isEmpty(request.getManagerGenderPreferenceCode())) {
            return "";
        }
        return context.getString(
                R.string.booking_request_option,
                formatter.toTripTypeLabel(request.getTripTypeCode()),
                formatter.toManagerGenderPreferenceLabel(request.getManagerGenderPreferenceCode())
        );
    }

    private String buildProfileLine(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getPatientConditionSummary())
                && TextUtils.isEmpty(request.getMedicationSummary())
                && TextUtils.isEmpty(request.getMobilitySupportCode())) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(request.getPatientConditionSummary())) {
            builder.append(context.getString(
                    R.string.booking_request_profile_condition,
                    request.getPatientConditionSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMedicationSummary())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_medication,
                    request.getMedicationSummary()
            ));
        }
        if (!TextUtils.isEmpty(request.getMobilitySupportCode())) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(context.getString(
                    R.string.booking_request_profile_mobility,
                    formatter.toMobilityLabel(request.getMobilitySupportCode())
            ));
        }
        return builder.toString();
    }

    private String buildPriceLine(AppointmentRequest request) {
        if (request.getFinalPrice() <= 0) {
            return "";
        }
        return context.getString(
                R.string.booking_request_price,
                formatter.formatPrice(request.getFinalPrice()),
                formatter.toPaymentMethodLabel(request.getPaymentMethodCode()),
                formatter.toCouponLabel(request.getCouponCode())
        );
    }

    private int resolveStatusBackgroundColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_surface_alt;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }

    private int resolveStatusTextColor(AppointmentStatus status) {
        return status == AppointmentStatus.CANCELED
                ? R.color.final_text_secondary
                : R.color.final_text_accent_accessible;
    }

    private int resolveStatusStrokeColor(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return R.color.bodeul_primary;
            case IN_PROGRESS:
            case COMPLETED:
                return R.color.bodeul_success;
            case CANCELED:
                return R.color.bodeul_outline;
            case REQUESTED:
            default:
                return R.color.bodeul_warning;
        }
    }
}
