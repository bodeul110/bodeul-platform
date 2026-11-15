package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentFollowUpRecord;
import com.example.bodeul.domain.model.AppointmentFollowUpSettlementStatus;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.booking.AppointmentFollowUpVisibilityPolicy;
import com.example.bodeul.ui.booking.BookingPresentationFormatter;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 완료된 동행 이력을 카드 목록으로 변환한다.
 */
public final class ManagerHistoryCoordinator {
    private final Context context;
    private final ManagerHomePresentationFormatter formatter;
    private final BookingPresentationFormatter bookingFormatter;

    public ManagerHistoryCoordinator(Context context, ManagerHomePresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
        this.bookingFormatter = new BookingPresentationFormatter(this.context);
    }

    public ManagerHistoryScreenModel createScreenModel(
            User currentUser,
            List<AppointmentRequestDetail> details,
            boolean firebaseBacked,
            ManagerHistoryFilter selectedFilter
    ) {
        List<AppointmentRequestDetail> filteredDetails = filterDetails(details, selectedFilter);
        return new ManagerHistoryScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, firebaseBacked),
                context.getString(R.string.manager_history_hero_badge, details.size()),
                context.getString(R.string.manager_history_hero_title, currentUser.getName()),
                buildHeroBody(details),
                buildSummaryText(details),
                selectedFilter == ManagerHistoryFilter.ALL
                        ? context.getString(R.string.manager_history_list_helper)
                        : context.getString(
                                R.string.manager_history_list_helper_filtered,
                                toFilterLabel(selectedFilter),
                                filteredDetails.size(),
                                details.size()
                        ),
                details.isEmpty()
                        ? context.getString(R.string.manager_history_empty)
                        : context.getString(
                                R.string.manager_history_filtered_empty,
                                toFilterLabel(selectedFilter)
                        ),
                createMetricCards(details),
                createFilterChips(details, selectedFilter),
                createEntryCards(filteredDetails)
        );
    }

    private String buildHeroBody(List<AppointmentRequestDetail> details) {
        if (details.isEmpty()) {
            return context.getString(R.string.manager_history_hero_body_empty);
        }

        AppointmentRequestDetail latestDetail = details.get(0);
        AppointmentRequest request = latestDetail.getAppointmentRequest();
        return context.getString(
                R.string.manager_history_hero_body_value,
                buildPatientName(latestDetail),
                request.getHospitalName(),
                request.getAppointmentAt()
        );
    }

    private String buildSummaryText(List<AppointmentRequestDetail> details) {
        int reviewSavedCount = 0;
        int settlementSavedCount = 0;
        int followUpPendingCount = 0;
        for (AppointmentRequestDetail detail : details) {
            AppointmentFollowUpRecord record = resolveFollowUpRecord(detail);
            if (record.hasSavedReview()) {
                reviewSavedCount++;
            }
            if (record.hasSavedSettlement()) {
                settlementSavedCount++;
            }
            if (!hasVisibleFollowUpAction(record)) {
                followUpPendingCount++;
            }
        }
        return context.getString(
                R.string.manager_history_summary_value,
                details.size(),
                reviewSavedCount,
                settlementSavedCount,
                followUpPendingCount
        );
    }

    private List<ManagerHistoryFilterChipModel> createFilterChips(
            List<AppointmentRequestDetail> details,
            ManagerHistoryFilter selectedFilter
    ) {
        List<ManagerHistoryFilterChipModel> chips = new ArrayList<>();
        for (ManagerHistoryFilter filter : ManagerHistoryFilter.values()) {
            chips.add(new ManagerHistoryFilterChipModel(
                    filter,
                    context.getString(
                            R.string.manager_history_filter_button,
                            toFilterLabel(filter),
                            countByFilter(details, filter)
                    ),
                    filter == selectedFilter
            ));
        }
        return chips;
    }

    private List<ManagerHistoryMetricModel> createMetricCards(
            List<AppointmentRequestDetail> details
    ) {
        int followUpSavedCount = 0;
        int settlementHelpCount = 0;
        for (AppointmentRequestDetail detail : details) {
            AppointmentFollowUpRecord record = resolveFollowUpRecord(detail);
            if (hasVisibleFollowUpAction(record)) {
                followUpSavedCount++;
            }
            if (needsSettlementHelp(record)) {
                settlementHelpCount++;
            }
        }

        int completionRate = details.isEmpty()
                ? 0
                : Math.round((followUpSavedCount * 100f) / details.size());
        List<ManagerHistoryMetricModel> cards = new ArrayList<>();
        cards.add(new ManagerHistoryMetricModel(
                context.getString(R.string.manager_history_metric_completed_label),
                context.getString(R.string.manager_history_metric_completed_value, details.size()),
                context.getString(R.string.manager_history_metric_completed_helper),
                ManagerHistoryBadgeTone.PRIMARY
        ));
        cards.add(new ManagerHistoryMetricModel(
                context.getString(R.string.manager_history_metric_follow_up_label),
                context.getString(R.string.manager_history_metric_follow_up_value, completionRate),
                context.getString(
                        R.string.manager_history_metric_follow_up_helper,
                        followUpSavedCount,
                        details.size()
                ),
                ManagerHistoryBadgeTone.SUCCESS
        ));
        cards.add(new ManagerHistoryMetricModel(
                context.getString(R.string.manager_history_metric_settlement_label),
                context.getString(R.string.manager_history_metric_settlement_value, settlementHelpCount),
                context.getString(R.string.manager_history_metric_settlement_helper),
                settlementHelpCount > 0
                        ? ManagerHistoryBadgeTone.WARNING
                        : ManagerHistoryBadgeTone.SUCCESS
        ));
        return cards;
    }

    private List<AppointmentRequestDetail> filterDetails(
            List<AppointmentRequestDetail> details,
            ManagerHistoryFilter selectedFilter
    ) {
        List<AppointmentRequestDetail> filteredDetails = new ArrayList<>();
        for (AppointmentRequestDetail detail : details) {
            if (matchesFilter(resolveFollowUpRecord(detail), selectedFilter)) {
                filteredDetails.add(detail);
            }
        }
        return filteredDetails;
    }

    private int countByFilter(List<AppointmentRequestDetail> details, ManagerHistoryFilter filter) {
        int count = 0;
        for (AppointmentRequestDetail detail : details) {
            if (matchesFilter(resolveFollowUpRecord(detail), filter)) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesFilter(
            AppointmentFollowUpRecord followUpRecord,
            ManagerHistoryFilter filter
    ) {
        switch (filter) {
            case FOLLOW_UP_PENDING:
                return !hasVisibleFollowUpAction(followUpRecord);
            case REVIEW_SAVED:
                return followUpRecord.hasSavedReview();
            case SETTLEMENT_SAVED:
                return followUpRecord.hasSavedSettlement();
            case ALL:
            default:
                return true;
        }
    }

    private List<ManagerHistoryEntryCardModel> createEntryCards(List<AppointmentRequestDetail> details) {
        List<ManagerHistoryEntryCardModel> cards = new ArrayList<>();
        for (AppointmentRequestDetail detail : details) {
            AppointmentRequest request = detail.getAppointmentRequest();
            AppointmentFollowUpRecord followUpRecord = resolveFollowUpRecord(detail);
            cards.add(new ManagerHistoryEntryCardModel(
                    request.getStatus() == AppointmentStatus.COMPLETED
                            ? request.getId() : null,
                    new ManagerHistoryBadgeModel(
                            context.getString(R.string.manager_history_entry_badge),
                            ManagerHistoryBadgeTone.PRIMARY
                    ),
                    buildFollowUpBadge(followUpRecord),
                    context.getString(R.string.manager_history_entry_title, buildPatientName(detail)),
                    context.getString(
                            R.string.manager_history_entry_subtitle,
                            request.getHospitalName(),
                            request.getDepartmentName(),
                            request.getAppointmentAt()
                    ),
                    buildSummary(detail),
                    buildActivityText(followUpRecord),
                    createDetailLines(detail, followUpRecord)
            ));
        }
        return cards;
    }

    private String buildSummary(AppointmentRequestDetail detail) {
        String summary = resolveManagerVisibleSummary(detail);
        if (TextUtils.isEmpty(summary)) {
            return context.getString(R.string.manager_history_report_empty);
        }
        return formatter.summarizeCardText(summary);
    }

    private String buildActivityText(AppointmentFollowUpRecord followUpRecord) {
        String latestLabel = "";
        long latestTimestamp = 0L;

        if (followUpRecord.hasSavedReview()) {
            latestLabel = context.getString(
                    R.string.manager_history_activity_review,
                    bookingFormatter.formatFollowUpReviewRating(followUpRecord.getReviewRating())
            );
            latestTimestamp = followUpRecord.getReviewSavedAtMillis();
        }
        if (followUpRecord.hasSavedSettlement()
                && followUpRecord.getSettlementSavedAtMillis() >= latestTimestamp) {
            latestLabel = context.getString(
                    R.string.manager_history_activity_settlement,
                    bookingFormatter.formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus())
            );
            latestTimestamp = followUpRecord.getSettlementSavedAtMillis();
        }
        if (TextUtils.isEmpty(latestLabel)) {
            return "";
        }
        return context.getString(
                R.string.manager_history_activity_format,
                latestLabel,
                formatter.formatTimestamp(latestTimestamp)
        );
    }

    private ManagerHistoryBadgeModel buildFollowUpBadge(AppointmentFollowUpRecord followUpRecord) {
        if (needsSettlementHelp(followUpRecord)) {
            return new ManagerHistoryBadgeModel(
                    resolveSettlementHelpBadgeLabel(followUpRecord),
                    ManagerHistoryBadgeTone.WARNING
            );
        }
        if (followUpRecord.hasSavedSettlement()) {
            return new ManagerHistoryBadgeModel(
                    context.getString(R.string.manager_history_badge_settlement_saved),
                    ManagerHistoryBadgeTone.SUCCESS
            );
        }
        if (followUpRecord.hasSavedReview()) {
            return new ManagerHistoryBadgeModel(
                    context.getString(R.string.manager_history_badge_review_saved),
                    ManagerHistoryBadgeTone.PURPLE
            );
        }
        return new ManagerHistoryBadgeModel(
                context.getString(R.string.manager_history_badge_follow_up_pending),
                ManagerHistoryBadgeTone.PRIMARY
        );
    }

    private boolean needsSettlementHelp(AppointmentFollowUpRecord followUpRecord) {
        return followUpRecord.hasSavedSettlement()
                && followUpRecord.getSettlementStatus() != null
                && followUpRecord.getSettlementStatus().requiresAdminFollowUp();
    }

    private String resolveSettlementHelpBadgeLabel(AppointmentFollowUpRecord followUpRecord) {
        AppointmentFollowUpSettlementStatus status = followUpRecord.getSettlementStatus();
        if (status == AppointmentFollowUpSettlementStatus.OVERTIME_REVIEW) {
            return context.getString(R.string.manager_history_badge_settlement_overtime);
        }
        if (status == AppointmentFollowUpSettlementStatus.REFUND_REVIEW) {
            return context.getString(R.string.manager_history_badge_settlement_refund);
        }
        return context.getString(R.string.manager_history_badge_settlement_help);
    }

    private List<ManagerInfoLineItem> createDetailLines(
            AppointmentRequestDetail detail,
            AppointmentFollowUpRecord followUpRecord
    ) {
        AppointmentRequest request = detail.getAppointmentRequest();
        List<ManagerInfoLineItem> items = new ArrayList<>();
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_history_line_patient),
                buildPatientName(detail),
                true
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_history_line_guardian),
                detail.getGuardian() == null
                        ? context.getString(R.string.manager_history_contact_missing)
                        : buildNamePhone(detail.getGuardian()),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_history_line_place),
                safeValue(request.getMeetingPlace(), R.string.manager_history_contact_missing),
                false
        ));
        items.add(new ManagerInfoLineItem(
                context.getString(R.string.manager_history_line_request_note),
                safeValue(request.getSpecialNotes(), R.string.manager_history_report_empty),
                false
        ));

        SessionReport report = detail.getSessionReport();
        if (report != null) {
            items.add(new ManagerInfoLineItem(
                    context.getString(R.string.manager_history_line_report),
                    safeValue(report.getSummary(), R.string.manager_history_report_empty),
                    false
            ));
            if (!TextUtils.isEmpty(report.getNextVisitAt())) {
                items.add(new ManagerInfoLineItem(
                        context.getString(R.string.manager_history_line_next_visit),
                        report.getNextVisitAt(),
                        false
                ));
            }
        } else if (detail.getSession() != null
                && !TextUtils.isEmpty(detail.getSession().getManagerJournal())) {
            items.add(new ManagerInfoLineItem(
                    context.getString(R.string.manager_history_line_journal),
                    detail.getSession().getManagerJournal(),
                    false
            ));
        }

        if (!AppointmentFollowUpVisibilityPolicy.hasVisibleAction(followUpRecord)) {
            items.add(new ManagerInfoLineItem(
                    context.getString(R.string.manager_history_line_follow_up_state),
                    context.getString(R.string.manager_history_follow_up_pending),
                    false
            ));
            return items;
        }

        if (followUpRecord.hasSavedReview()) {
            items.add(new ManagerInfoLineItem(
                    context.getString(R.string.manager_history_line_follow_up_review),
                    bookingFormatter.formatFollowUpReviewRating(followUpRecord.getReviewRating()),
                    false
            ));
        }
        if (followUpRecord.hasSavedSettlement()) {
            items.add(new ManagerInfoLineItem(
                    context.getString(R.string.manager_history_line_follow_up_settlement),
                    bookingFormatter.formatFollowUpSettlementStatus(followUpRecord.getSettlementStatus()),
                    false
            ));
            if (!TextUtils.isEmpty(followUpRecord.getSettlementNote())) {
                items.add(new ManagerInfoLineItem(
                        context.getString(R.string.manager_history_line_follow_up_note),
                        formatter.summarizeCardText(followUpRecord.getSettlementNote()),
                        false
                ));
            }
        }
        return items;
    }

    static String resolveManagerVisibleSummary(AppointmentRequestDetail detail) {
        SessionReport report = detail.getSessionReport();
        if (report != null
                && report.getSummary() != null
                && !report.getSummary().isEmpty()) {
            return report.getSummary();
        }
        return detail.getSession() == null ? "" : detail.getSession().getManagerJournal();
    }

    private AppointmentFollowUpRecord resolveFollowUpRecord(AppointmentRequestDetail detail) {
        if (detail.getFollowUpRecord() != null) {
            return detail.getFollowUpRecord();
        }
        return AppointmentFollowUpRecord.empty(detail.getAppointmentRequest().getId());
    }

    private String buildPatientName(AppointmentRequestDetail detail) {
        if (detail.getPatient() != null && !TextUtils.isEmpty(detail.getPatient().getName())) {
            return detail.getPatient().getName();
        }
        return safeValue(detail.getAppointmentRequest().getPatientName(), R.string.manager_history_contact_missing);
    }

    private String buildNamePhone(User user) {
        if (TextUtils.isEmpty(user.getPhone())) {
            return user.getName();
        }
        return context.getString(R.string.manager_history_name_phone, user.getName(), user.getPhone());
    }

    private String safeValue(String value, int emptyResId) {
        if (TextUtils.isEmpty(value)) {
            return context.getString(emptyResId);
        }
        return formatter.summarizeCardText(value);
    }

    private String toFilterLabel(ManagerHistoryFilter filter) {
        switch (filter) {
            case FOLLOW_UP_PENDING:
                return context.getString(R.string.manager_history_filter_follow_up_pending);
            case REVIEW_SAVED:
                return context.getString(R.string.manager_history_filter_review_saved);
            case SETTLEMENT_SAVED:
                return context.getString(R.string.manager_history_filter_settlement_saved);
            case ALL:
            default:
                return context.getString(R.string.manager_history_filter_all);
        }
    }

    private boolean hasVisibleFollowUpAction(AppointmentFollowUpRecord followUpRecord) {
        return AppointmentFollowUpVisibilityPolicy.hasVisibleAction(followUpRecord);
    }
}
