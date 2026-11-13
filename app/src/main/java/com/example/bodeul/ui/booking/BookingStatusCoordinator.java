package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.BookingPaymentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;
import com.example.bodeul.util.MedicationComparisonDecisionDisplayHelper;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;
import com.example.bodeul.util.MedicationComparisonDisplayHelper;
import com.example.bodeul.util.MedicationComparisonSummary;
import com.example.bodeul.util.PharmacyProgressDisplayHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 예약 상세에 필요한 도메인 정보를 상태 화면 모델로 조합한다.
 */
public final class BookingStatusCoordinator {
    private final Context context;
    private final BookingPresentationFormatter formatter;
    private final AppointmentProgressComposer progressComposer;
    private final boolean legacyManagerLocationEnabled;

    public BookingStatusCoordinator(
            Context context,
            BookingPresentationFormatter formatter,
            AppointmentProgressComposer progressComposer
    ) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
        this.progressComposer = progressComposer;
        this.legacyManagerLocationEnabled = LegacyManagerLocationSharingPolicy.isEnabled(context);
    }

    public BookingStatusScreenModel createScreenModel(
            User currentUser,
            AppointmentRequestDetail detail,
            boolean isFirebaseBacked,
            @Nullable AppointmentFollowUpRecord followUpRecord
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        AppointmentProgressOverviewModel progressOverview = progressComposer.create(
                currentUser.getRole(),
                request,
                detail.getManager(),
                detail.getSession(),
                detail.getSessionReport(),
                detail.getHospitalGuide()
        );
        return new BookingStatusScreenModel(
                request.getStatus(),
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                buildStatusLabel(request),
                request.getHospitalName(),
                request.getDepartmentName(),
                progressOverview.getTitleText(),
                progressOverview.getBodyText(),
                progressOverview,
                createParticipantLines(detail),
                createSummaryLines(detail),
                createPaymentLines(request),
                TextUtils.isEmpty(request.getPublicCode()) ? "" : context.getString(
                        R.string.final_booking_status_reference, request.getPublicCode()),
                detail.getManager() == null ? "" : TextUtils.isEmpty(detail.getManager().getName())
                        ? context.getString(R.string.booking_status_line_manager) : context.getString(
                                R.string.final_booking_status_manager_name, detail.getManager().getName()),
                detail.getManager() == null ? "" : buildManagerDisplay(detail.getManager()),
                TextUtils.isEmpty(request.getSpecialNotes())
                        ? context.getString(R.string.final_booking_status_note_empty) : request.getSpecialNotes(),
                isAwaitingDeposit(request),
                createLiveLines(detail),
                createReportSections(detail, followUpRecord),
                buildPrimaryAction(currentUser, request.getStatus()),
                buildSecondaryAction(currentUser, request.getStatus())
        );
    }

    private String buildStatusLabel(AppointmentRequest request) {
        if (isAwaitingDeposit(request)) {
            return context.getString(R.string.final_booking_status_awaiting_deposit);
        }
        switch (request.getStatus()) {
            case MATCHED:
                return context.getString(R.string.final_booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.final_booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.final_booking_status_completed);
            case CANCELED:
                return context.getString(R.string.final_booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.final_booking_status_requested);
        }
    }

    static boolean isAwaitingDeposit(AppointmentRequest request) {
        // 완료·취소 등 예약 상태를 오래된 결제 상태로 덮어쓰지 않는다.
        return (request.getStatus() == AppointmentStatus.REQUESTED
                || request.getStatus() == AppointmentStatus.MATCHED)
                && BookingPaymentStatus.fromValue(request.getPaymentStatusCode())
                == BookingPaymentStatus.AWAITING_DEPOSIT;
    }

    private List<BookingStatusLineItem> createParticipantLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<BookingStatusLineItem> items = new ArrayList<>();
        User patient = detail.getPatient();
        String patientName = patient == null ? request.getPatientName() : patient.getName();
        if (patient == null && TextUtils.isEmpty(request.getPatientUserId()) && !TextUtils.isEmpty(patientName)) {
            patientName = context.getString(R.string.booking_status_contact_pending_format, patientName);
        }
        String patientContact = patient == null
                ? buildContactValue("", request.getPatientPhone(), request.getPatientEmail())
                : buildContactValue("", patient.getPhone(), patient.getEmail());
        items.add(new BookingStatusLineItem(context.getString(R.string.final_booking_status_patient_name),
                TextUtils.isEmpty(patientName) ? context.getString(R.string.booking_status_contact_missing)
                        : patientName, false));
        items.add(new BookingStatusLineItem(context.getString(R.string.final_booking_status_contact),
                TextUtils.isEmpty(patientContact) ? context.getString(R.string.booking_status_contact_missing)
                        : patientContact, false));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_guardian),
                buildParticipantDisplay(
                        detail.getGuardian(),
                        request.getGuardianName(),
                        request.getGuardianPhone(),
                        request.getGuardianEmail(),
                        request.getGuardianUserId()
                ),
                false
        ));
        addOptionalLine(items, R.string.booking_status_line_health_note, request.getPatientConditionSummary(), false);
        addOptionalLine(items, R.string.booking_status_line_medication_note, request.getMedicationSummary(), false);
        return items;
    }

    private List<BookingStatusLineItem> createSummaryLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<BookingStatusLineItem> items = new ArrayList<>();
        items.add(new BookingStatusLineItem(context.getString(R.string.final_booking_completion_department),
                request.getDepartmentName(), false));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_schedule),
                formatAppointmentAt(request.getAppointmentAt()),
                true
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_place),
                TextUtils.isEmpty(request.getMeetingPlace())
                        ? context.getString(R.string.booking_status_place_missing)
                        : request.getMeetingPlace(),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_mobility),
                formatter.toMobilityLabel(request.getMobilitySupportCode()),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_trip),
                formatter.toTripTypeLabel(request.getTripTypeCode()),
                false
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_manager_preference),
                formatter.toManagerGenderPreferenceLabel(request.getManagerGenderPreferenceCode()),
                false
        ));
        return items;
    }

    private String formatAppointmentAt(String value) {
        BookingCompletionDateTime.DisplayValue dateTime = BookingCompletionDateTime.format(value, Locale.KOREA);
        if (dateTime.getDate().isEmpty()) return context.getString(R.string.booking_completion_schedule_pending);
        return dateTime.getTime().isEmpty() ? dateTime.getDate()
                : context.getString(R.string.final_booking_status_schedule, dateTime.getDate(), dateTime.getTime());
    }

    private List<BookingStatusLineItem> createPaymentLines(AppointmentRequest request) {
        List<BookingStatusLineItem> items = new ArrayList<>();
        if (request.getFinalPrice() > 0) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_final_price),
                    formatter.formatPrice(request.getFinalPrice()), true));
        }
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_payment),
                formatter.toPaymentMethodLabel(request.getPaymentMethodCode()),
                false
        ));
        addOptionalLine(
                items,
                R.string.booking_status_line_payment_status,
                buildPaymentStatusValue(request),
                false
        );
        addOptionalLine(items, R.string.booking_status_line_payment_provider, request.getPaymentProviderLabel(), false);
        addOptionalLine(items, R.string.booking_status_line_payment_approval, request.getPaymentApprovalCode(), false);
        addOptionalLine(items, R.string.booking_status_line_payment_approved_at, request.getPaymentApprovedAt(), false);
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_coupon),
                formatter.toCouponLabel(request.getCouponCode()),
                false
        ));
        return items;
    }

    @Nullable
    private String buildPaymentStatusValue(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getPaymentStatusCode())) {
            return null;
        }
        return formatter.toPaymentStatusLabel(request.getPaymentStatusCode());
    }

    private List<BookingStatusLineItem> createLiveLines(AppointmentRequestDetail detail) {
        CompanionSession session = detail.getSession();
        HospitalGuide guide = detail.getHospitalGuide();
        List<BookingStatusLineItem> items = new ArrayList<>();

        if (session == null) {
            return items;
        }

        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_session),
                formatter.toSessionStatusLabel(session.getStatus()),
                true
        ));
        if (guide != null) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_step),
                    guide.getSteps().isEmpty()
                            ? context.getString(R.string.booking_status_guide_preparing)
                            : context.getString(
                                    R.string.booking_status_progress_step_format,
                                    session.getCurrentStepOrder(),
                                    guide.getSteps().size()
                            ),
                    false
            ));
            GuideStep currentStep = findCurrentStep(guide, session.getCurrentStepOrder());
            if (currentStep != null && !TextUtils.isEmpty(currentStep.getTitle())) {
                items.add(new BookingStatusLineItem(
                        context.getString(R.string.booking_status_line_current_guide),
                        currentStep.getTitle(),
                        true
                ));
            }
        }
        if (legacyManagerLocationEnabled) {
            addOptionalLine(
                    items,
                    R.string.booking_status_line_live_location,
                    session.getLocationSummary(),
                    false);
        }
        addOptionalLine(items, R.string.booking_status_line_guardian_update, session.getGuardianUpdate(), false);
        addOptionalLine(items, R.string.booking_status_line_live_photo, session.getFieldPhotoNote(), false);
        addOptionalLine(items, R.string.booking_status_line_live_medication, session.getMedicationNote(), false);
        addOptionalLine(items, R.string.booking_status_line_live_pharmacy, session.getPharmacySummary(), false);
        if (PharmacyProgressDisplayHelper.shouldShowDetail(session)) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_live_pharmacy_state),
                    PharmacyProgressDisplayHelper.buildOverallStateLabel(context, session),
                    false
            ));
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_live_pharmacy_detail),
                    PharmacyProgressDisplayHelper.buildStepSummary(context, session),
                    false
            ));
        }
        return items;
    }

    @Nullable
    private GuideStep findCurrentStep(HospitalGuide guide, int currentStepOrder) {
        for (GuideStep step : guide.getSteps()) {
            if (step.getOrder() == currentStepOrder) {
                return step;
            }
        }
        return null;
    }

    private List<BookingStatusSectionModel> createReportSections(
            AppointmentRequestDetail detail,
            @Nullable AppointmentFollowUpRecord followUpRecord
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        SessionReport report = detail.getSessionReport();
        List<BookingStatusSectionModel> sections = new ArrayList<>();
        List<BookingStatusLineItem> hospitalLines = new ArrayList<>();
        List<BookingStatusLineItem> medicationLines = new ArrayList<>();
        List<BookingStatusLineItem> followUpLines = new ArrayList<>();

        if (report != null) {
            addOptionalLine(hospitalLines, R.string.booking_status_line_report_summary, report.getSummary(), true);
            addOptionalLine(hospitalLines, R.string.booking_status_line_report_treatment, report.getTreatmentNotes(), false);
            addOptionalLine(hospitalLines, R.string.booking_status_line_report_next_visit, report.getNextVisitAt(), false);
            MedicationComparisonSummary comparisonSummary =
                    MedicationComparisonDisplayHelper.buildSummary(context, request, report);
            addOptionalLine(medicationLines, R.string.booking_status_line_report_medication, report.getMedicationNotes(), true);
            addOptionalLine(medicationLines, R.string.booking_status_line_report_medication_name, report.getMedicationName(), false);
            addOptionalLine(medicationLines, R.string.booking_status_line_report_medication_change, report.getMedicationChangeSummary(), false);
            addOptionalLine(medicationLines, R.string.booking_status_line_report_medication_schedule, report.getMedicationScheduleNote(), false);
            addOptionalLine(
                    medicationLines,
                    R.string.booking_status_line_report_medication_decision,
                    MedicationComparisonDecisionDisplayHelper.toLabel(
                            context,
                            report.getMedicationComparisonDecision()
                    ),
                    false
            );
            addOptionalLine(
                    medicationLines,
                    R.string.booking_status_line_report_medication_decision_note,
                    report.getMedicationComparisonNote(),
                    false
            );
            if (comparisonSummary != null) {
                addOptionalLine(
                        medicationLines,
                        R.string.booking_status_line_report_medication_compare,
                        comparisonSummary.getStatusLabel(),
                        false
                );
                addOptionalLine(
                        medicationLines,
                        R.string.booking_status_line_report_medication_detail,
                        comparisonSummary.getDetailLabel(),
                        false
                );
                addOptionalLine(
                        medicationLines,
                        R.string.booking_status_line_report_medication_follow_up,
                        comparisonSummary.getFollowUpLabel(),
                        false
                );
            }
        } else if (request.getStatus() == AppointmentStatus.COMPLETED) {
            hospitalLines.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_report_state),
                    context.getString(R.string.booking_status_report_pending),
                    false
            ));
        }

        addFollowUpLines(followUpLines, request, followUpRecord);

        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.booking_status_report_section_hospital),
                hospitalLines
        );
        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.booking_status_report_section_medication),
                medicationLines
        );
        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.booking_status_report_section_follow_up),
                followUpLines
        );
        return sections;
    }

    private void addFollowUpLines(
            List<BookingStatusLineItem> items,
            AppointmentRequest request,
            @Nullable AppointmentFollowUpRecord followUpRecord
    ) {
        if (request.getStatus() != AppointmentStatus.COMPLETED || followUpRecord == null) {
            return;
        }

        if (!AppointmentFollowUpVisibilityPolicy.hasVisibleAction(followUpRecord)) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_follow_up_state),
                    context.getString(R.string.booking_follow_up_summary_pending),
                    false
            ));
            return;
        }

        if (followUpRecord.hasSavedReview()) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_follow_up_review),
                    context.getString(
                            R.string.booking_status_follow_up_value_format,
                            formatter.formatFollowUpReviewRating(followUpRecord.getReviewRating()),
                            formatter.formatTimestamp(followUpRecord.getReviewSavedAtMillis())
                    ),
                    false
            ));
        }
        if (followUpRecord.hasSavedSettlement()) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_follow_up_settlement),
                    context.getString(
                            R.string.booking_status_follow_up_value_format,
                            formatter.formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus()),
                            formatter.formatTimestamp(followUpRecord.getSettlementSavedAtMillis())
                    ),
                    false
            ));
            addOptionalLine(
                    items,
                    R.string.booking_status_line_follow_up_settlement_note,
                    followUpRecord.getSettlementNote(),
                    false
            );
        }
    }

    @Nullable
    private BookingStatusActionModel buildPrimaryAction(User currentUser, AppointmentStatus status) {
        BookingStatusActionModel action;
        switch (status) {
            case REQUESTED:
                action = new BookingStatusActionModel(
                        BookingStatusActionType.EDIT,
                        context.getString(R.string.booking_status_action_edit)
                );
                break;
            case MATCHED:
                action = new BookingStatusActionModel(
                        BookingStatusActionType.REFRESH,
                        context.getString(R.string.booking_status_action_refresh)
                );
                break;
            case IN_PROGRESS:
                BookingStatusActionType inProgressActionType =
                        resolveInProgressPrimaryActionType(legacyManagerLocationEnabled);
                action = new BookingStatusActionModel(
                        inProgressActionType,
                        context.getString(inProgressActionType == BookingStatusActionType.OPEN_LIVE_TRACKING
                                ? R.string.booking_status_action_open_live_tracking
                                : R.string.booking_status_action_refresh)
                );
                break;
            case COMPLETED:
                action = new BookingStatusActionModel(
                        BookingStatusActionType.OPEN_FOLLOW_UP,
                        context.getString(R.string.booking_status_action_open_follow_up)
                );
                break;
            case CANCELED:
            default:
                action = new BookingStatusActionModel(
                        BookingStatusActionType.OPEN_HISTORY,
                        context.getString(R.string.booking_status_action_open_history)
                );
                break;
        }
        return BookingMutationPolicy.isStatusActionAllowed(
                currentUser.getRole(), action.getActionType()) ? action : null;
    }

    static BookingStatusActionType resolveInProgressPrimaryActionType(
            boolean legacyManagerLocationEnabled
    ) {
        return legacyManagerLocationEnabled
                ? BookingStatusActionType.OPEN_LIVE_TRACKING
                : BookingStatusActionType.REFRESH;
    }

    @Nullable
    private BookingStatusActionModel buildSecondaryAction(User currentUser, AppointmentStatus status) {
        switch (status) {
            case REQUESTED:
            case MATCHED:
                BookingStatusActionModel cancelAction = new BookingStatusActionModel(
                        BookingStatusActionType.CANCEL,
                        context.getString(R.string.booking_status_action_cancel)
                );
                return BookingMutationPolicy.isStatusActionAllowed(
                        currentUser.getRole(), cancelAction.getActionType())
                        ? cancelAction
                        : null;
            case IN_PROGRESS:
                if (currentUser.getRole() == UserRole.GUARDIAN) {
                    return new BookingStatusActionModel(
                            BookingStatusActionType.OPEN_REPORT,
                            context.getString(R.string.booking_status_action_open_report)
                    );
                }
                if (!shouldShowInProgressRefreshSecondary(
                        currentUser.getRole(), legacyManagerLocationEnabled)) {
                    return null;
                }
                return new BookingStatusActionModel(
                        BookingStatusActionType.REFRESH,
                        context.getString(R.string.booking_status_action_refresh)
                );
            case COMPLETED:
                if (currentUser.getRole() == UserRole.GUARDIAN) {
                    return new BookingStatusActionModel(
                            BookingStatusActionType.OPEN_REPORT,
                            context.getString(R.string.booking_status_action_open_report)
                    );
                }
                return new BookingStatusActionModel(
                        BookingStatusActionType.OPEN_HISTORY,
                        context.getString(R.string.booking_status_action_open_history)
                );
            case CANCELED:
            default:
                return null;
        }
    }

    static boolean shouldShowInProgressRefreshSecondary(
            UserRole role,
            boolean legacyManagerLocationEnabled
    ) {
        return legacyManagerLocationEnabled && role != UserRole.GUARDIAN;
    }

    private void addOptionalLine(List<BookingStatusLineItem> items, int titleResId, String value, boolean emphasized) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        items.add(new BookingStatusLineItem(
                context.getString(titleResId),
                value,
                emphasized
        ));
    }

    private void addSectionIfNotEmpty(
            List<BookingStatusSectionModel> sections,
            String title,
            List<BookingStatusLineItem> lines
    ) {
        if (lines.isEmpty()) {
            return;
        }
        sections.add(new BookingStatusSectionModel(title, new ArrayList<>(lines)));
    }

    private String buildParticipantDisplay(
            @Nullable User linkedUser,
            String fallbackName,
            String fallbackPhone,
            String fallbackEmail,
            String linkedUserId
    ) {
        String name = linkedUser != null ? linkedUser.getName() : fallbackName;
        String phone = linkedUser != null ? linkedUser.getPhone() : fallbackPhone;
        String email = linkedUser != null ? linkedUser.getEmail() : fallbackEmail;
        String baseValue = buildContactValue(name, phone, email);
        if (TextUtils.isEmpty(baseValue)) {
            return context.getString(R.string.booking_status_contact_missing);
        }
        if (linkedUser == null && TextUtils.isEmpty(linkedUserId)) {
            return context.getString(R.string.booking_status_contact_pending_format, baseValue);
        }
        return baseValue;
    }

    private String buildManagerDisplay(@Nullable User manager) {
        if (manager == null) {
            return context.getString(R.string.booking_status_manager_pending);
        }
        return buildContactValue(manager.getName(), manager.getPhone(), manager.getEmail());
    }

    private String buildContactValue(String name, String phone, String email) {
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(phone)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, phone);
        }
        if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(email)) {
            return context.getString(R.string.booking_status_contact_name_phone, name, email);
        }
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        if (!TextUtils.isEmpty(phone)) {
            return phone;
        }
        if (!TextUtils.isEmpty(email)) {
            return email;
        }
        return "";
    }

    private String buildGuideDisplay(@Nullable HospitalGuide hospitalGuide) {
        if (hospitalGuide == null) {
            return context.getString(R.string.booking_status_guide_missing);
        }
        if (hospitalGuide.getSteps().isEmpty()) {
            return context.getString(R.string.booking_status_guide_preparing);
        }
        return context.getString(
                R.string.booking_status_guide_ready,
                hospitalGuide.getSteps().size()
        );
    }

}
