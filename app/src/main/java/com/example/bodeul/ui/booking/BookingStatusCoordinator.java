package com.example.bodeul.ui.booking;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
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
                formatter.toStatusLabel(request.getStatus()),
                buildHeroTitle(detail),
                buildHeroBody(detail),
                progressOverview.getTitleText(),
                progressOverview.getBodyText(),
                progressOverview,
                createParticipantLines(detail),
                createSummaryLines(detail),
                createLiveLines(detail),
                createReportSections(detail, followUpRecord),
                buildPrimaryAction(currentUser, request.getStatus()),
                buildSecondaryAction(currentUser, request.getStatus())
        );
    }

    private String buildHeroTitle(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        switch (request.getStatus()) {
            case MATCHED:
                return context.getString(
                        R.string.booking_status_hero_title_matched,
                        request.getHospitalName(),
                        request.getDepartmentName()
                );
            case IN_PROGRESS:
                return context.getString(
                        R.string.booking_status_hero_title_in_progress,
                        request.getHospitalName()
                );
            case COMPLETED:
                return context.getString(R.string.booking_status_hero_title_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_hero_title_canceled);
            case REQUESTED:
            default:
                return context.getString(
                        R.string.booking_status_hero_title_requested,
                        request.getHospitalName(),
                        request.getDepartmentName()
                );
        }
    }

    private String buildHeroBody(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        String meetingPlace = TextUtils.isEmpty(request.getMeetingPlace())
                ? context.getString(R.string.booking_status_place_missing)
                : request.getMeetingPlace();
        return context.getString(
                R.string.booking_status_hero_body_format,
                request.getAppointmentAt(),
                meetingPlace,
                buildHeroStatusNote(detail)
        );
    }

    private String buildHeroStatusNote(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        switch (request.getStatus()) {
            case MATCHED:
                return context.getString(
                        R.string.booking_status_hero_note_matched,
                        buildManagerDisplay(detail.getManager())
                );
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_hero_note_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_hero_note_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_hero_note_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_hero_note_requested);
        }
    }

    private List<BookingStatusLineItem> createParticipantLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<BookingStatusLineItem> items = new ArrayList<>();
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_patient),
                buildParticipantDisplay(
                        detail.getPatient(),
                        request.getPatientName(),
                        request.getPatientPhone(),
                        request.getPatientEmail(),
                        request.getPatientUserId()
                ),
                true
        ));
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
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_manager),
                buildManagerDisplay(detail.getManager()),
                false
        ));
        return items;
    }

    private List<BookingStatusLineItem> createSummaryLines(AppointmentRequestDetail detail) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<BookingStatusLineItem> items = new ArrayList<>();
        addOptionalLine(
                items,
                R.string.booking_status_line_public_code,
                request.getPublicCode(),
                true
        );
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_schedule),
                request.getAppointmentAt(),
                true
        ));
        items.add(new BookingStatusLineItem(
                context.getString(R.string.booking_status_line_hospital),
                context.getString(
                        R.string.booking_status_hospital_value,
                        request.getHospitalName(),
                        request.getDepartmentName()
                ),
                false
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
        if (request.getFinalPrice() > 0) {
            items.add(new BookingStatusLineItem(
                    context.getString(R.string.booking_status_line_final_price),
                    formatter.formatPrice(request.getFinalPrice()),
                    true
            ));
        }
        addOptionalLine(items, R.string.booking_status_line_request_note, request.getSpecialNotes(), false);
        addOptionalLine(items, R.string.booking_status_line_health_note, request.getPatientConditionSummary(), false);
        addOptionalLine(items, R.string.booking_status_line_medication_note, request.getMedicationSummary(), false);
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
