package com.example.bodeul.ui.report;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionLocationHistoryEntry;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.util.CompanionLocationAlertDisplayHelper;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;
import com.example.bodeul.util.MedicationComparisonDecisionDisplayHelper;
import com.example.bodeul.util.MedicationComparisonDisplayHelper;
import com.example.bodeul.util.MedicationComparisonSummary;
import com.example.bodeul.util.PharmacyProgressDisplayHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 보호자 진행 데이터를 화면 모델로 조합한다.
 */
public final class GuardianReportCoordinator {
    private final Context context;
    private final GuardianReportPresentationFormatter formatter;
    private final boolean legacyManagerLocationEnabled;

    public GuardianReportCoordinator(Context context, GuardianReportPresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
        this.legacyManagerLocationEnabled = LegacyManagerLocationSharingPolicy.isEnabled(context);
    }

    public GuardianReportScreenModel createScreenModel(
            @Nullable GuardianReportDashboard dashboard,
            boolean isFirebaseBacked
    ) {
        if (dashboard == null) {
            return createEmptyScreenModel(isFirebaseBacked);
        }

        List<GuardianReportEntry> entries = dashboard.getEntries();
        GuardianReportEntry highlightEntry = findHighlightEntry(entries);
        return new GuardianReportScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(
                        R.string.guardian_report_greeting,
                        dashboard.getGuardian().getName()
                ),
                context.getString(
                        R.string.guardian_report_summary,
                        entries.size(),
                        TextUtils.isEmpty(dashboard.getGuardian().getPhone())
                                ? context.getString(R.string.guardian_report_phone_missing)
                                : dashboard.getGuardian().getPhone()
                ),
                createHighlightModel(highlightEntry, entries.size()),
                createEntryCards(entries)
        );
    }

    public GuardianReportScreenModel createEmptyScreenModel(boolean isFirebaseBacked) {
        return new GuardianReportScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.guardian_report_empty_greeting),
                context.getString(R.string.guardian_report_empty_summary),
                new GuardianReportHighlightModel(
                        AppointmentStatus.REQUESTED,
                        context.getString(R.string.guardian_report_empty_title),
                        context.getString(R.string.guardian_report_empty_body),
                        null,
                        null
                ),
                Collections.emptyList()
        );
    }

    @Nullable
    private GuardianReportEntry findHighlightEntry(List<GuardianReportEntry> entries) {
        for (GuardianReportEntry entry : entries) {
            AppointmentStatus status = entry.getAppointmentRequest().getStatus();
            if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.CANCELED) {
                return entry;
            }
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private GuardianReportHighlightModel createHighlightModel(@Nullable GuardianReportEntry entry, int count) {
        if (entry == null) {
            return new GuardianReportHighlightModel(
                    AppointmentStatus.REQUESTED,
                    context.getString(R.string.guardian_report_empty_title),
                    context.getString(R.string.guardian_report_empty_body),
                    null,
                    null
            );
        }

        CompanionSession session = entry.getSession();
        String progressText = buildProgressText(entry);
        String locationText = !legacyManagerLocationEnabled
                ? ""
                : session == null || TextUtils.isEmpty(session.getLocationSummary())
                        ? context.getString(R.string.guardian_report_location_empty)
                        : session.getLocationSummary();
        String liveText = session == null || TextUtils.isEmpty(session.getGuardianUpdate())
                ? context.getString(R.string.guardian_report_update_empty)
                : session.getGuardianUpdate();

        return new GuardianReportHighlightModel(
                entry.getAppointmentRequest().getStatus(),
                context.getString(
                        R.string.guardian_report_highlight_title,
                        entry.getAppointmentRequest().getHospitalName(),
                        entry.getAppointmentRequest().getDepartmentName()
                ),
                legacyManagerLocationEnabled
                        ? context.getString(
                                R.string.guardian_report_highlight_body_v2,
                                count,
                                buildPatientDisplay(entry),
                                entry.getAppointmentRequest().getAppointmentAt(),
                                formatter.buildManagerDisplay(entry.getManager()),
                                progressText,
                                locationText,
                                liveText
                        )
                        : context.getString(
                                R.string.guardian_report_highlight_body_without_location,
                                count,
                                buildPatientDisplay(entry),
                                entry.getAppointmentRequest().getAppointmentAt(),
                                formatter.buildManagerDisplay(entry.getManager()),
                                progressText,
                                liveText
                        ),
                entry.getAppointmentRequest().getId(),
                context.getString(R.string.guardian_report_action_open_detail)
        );
    }

    private List<GuardianReportEntryCardModel> createEntryCards(List<GuardianReportEntry> entries) {
        List<GuardianReportEntryCardModel> cards = new ArrayList<>();
        for (GuardianReportEntry entry : entries) {
            List<GuardianReportSectionModel> reportSections = createReportSections(entry);
            boolean finalReportReady = GuardianFinalReportPolicy.hasReportContent(
                    entry.getSessionReport()
            ) && GuardianFinalReportPolicy.shouldRenderFinalReport(
                    entry.getAppointmentRequest().getStatus(),
                    reportSections
            );
            CompanionSession session = entry.getSession();
            cards.add(new GuardianReportEntryCardModel(
                    entry.getAppointmentRequest().getId(),
                    entry.getAppointmentRequest().getStatus(),
                    finalReportReady,
                    context.getString(
                            R.string.guardian_report_item_title,
                            entry.getAppointmentRequest().getHospitalName(),
                            entry.getAppointmentRequest().getDepartmentName()
                    ),
                    buildHeroBody(entry),
                    GuardianFinalReportPolicy.resolveDateText(
                            session,
                            entry.getAppointmentRequest().getAppointmentAt()
                    ),
                    entry.getAppointmentRequest().getHospitalName(),
                    entry.getAppointmentRequest().getDepartmentName(),
                    GuardianFinalReportPolicy.optionalText(
                            entry.getAppointmentRequest().getPatientConditionSummary()
                    ),
                    resolveManagerName(entry),
                    resolveManagerMessageLabel(session),
                    GuardianFinalReportPolicy.resolveManagerMessage(session),
                    context.getString(R.string.guardian_report_live_section_title),
                    createLiveLines(entry),
                    context.getString(R.string.guardian_report_history_section_title),
                    createHistoryLines(entry),
                    context.getString(R.string.guardian_report_memo_section_title),
                    createMemoLines(entry),
                    context.getString(R.string.guardian_report_report_section_title),
                    reportSections,
                    reportSections.isEmpty()
                            ? context.getString(R.string.guardian_report_report_pending)
                            : null,
                    context.getString(R.string.guardian_report_action_open_detail)
            ));
        }
        return cards;
    }

    @Nullable
    private String resolveManagerName(GuardianReportEntry entry) {
        if (entry.getManager() != null) {
            return GuardianFinalReportPolicy.optionalText(entry.getManager().getName());
        }
        return GuardianFinalReportPolicy.optionalText(entry.getAppointmentRequest().getManagerName());
    }

    @Nullable
    private String resolveManagerMessageLabel(@Nullable CompanionSession session) {
        if (GuardianFinalReportPolicy.resolveManagerMessage(session) == null) {
            return null;
        }
        return context.getString(GuardianFinalReportPolicy.hasCompletionJournal(session)
                ? R.string.guardian_final_report_manager_message
                : R.string.guardian_final_report_manager_live_update);
    }

    private String buildHeroBody(GuardianReportEntry entry) {
        String place = TextUtils.isEmpty(entry.getAppointmentRequest().getMeetingPlace())
                ? context.getString(R.string.booking_status_place_missing)
                : entry.getAppointmentRequest().getMeetingPlace();
        return context.getString(
                R.string.guardian_report_card_body,
                buildPatientDisplay(entry),
                entry.getAppointmentRequest().getAppointmentAt(),
                place,
                formatter.buildManagerDisplay(entry.getManager())
        );
    }

    private List<GuardianReportLineItem> createLiveLines(GuardianReportEntry entry) {
        List<GuardianReportLineItem> items = new ArrayList<>();
        items.add(new GuardianReportLineItem(
                context.getString(R.string.guardian_report_line_progress),
                buildProgressText(entry),
                true
        ));
        if (legacyManagerLocationEnabled) {
            addOptionalLine(items, R.string.guardian_report_line_location, entry.getSession() == null
                    ? ""
                    : entry.getSession().getLocationSummary(), false);
            items.add(new GuardianReportLineItem(
                    context.getString(R.string.guardian_report_line_live_status),
                    CompanionLocationDisplayHelper.buildLiveSharingStatus(context, entry.getSession()),
                    false
            ));
            items.add(new GuardianReportLineItem(
                    context.getString(R.string.guardian_report_line_location_alert),
                    CompanionLocationAlertDisplayHelper.buildAlertSummary(context, entry.getSession()),
                    false
            ));
            if (entry.getSession() != null
                    && entry.getSession().getSharedLocationUpdatedAtMillis() > 0L) {
                items.add(new GuardianReportLineItem(
                        context.getString(R.string.guardian_report_line_location_updated_at),
                        formatSharedLocationTime(entry.getSession().getSharedLocationUpdatedAtMillis()),
                        false
                ));
            }
        }
        return items;
    }

    private List<GuardianReportLineItem> createHistoryLines(GuardianReportEntry entry) {
        List<GuardianReportLineItem> items = new ArrayList<>();
        if (!legacyManagerLocationEnabled) {
            return items;
        }
        List<CompanionLocationHistoryEntry> history =
                CompanionLocationDisplayHelper.resolveHistoryEntries(entry.getSession(), 6);
        if (history.isEmpty()) {
            items.add(new GuardianReportLineItem(
                    context.getString(R.string.guardian_report_history_empty_label),
                    context.getString(R.string.live_location_history_empty),
                    false
            ));
            return items;
        }

        for (CompanionLocationHistoryEntry historyEntry : history) {
            items.add(new GuardianReportLineItem(
                    CompanionLocationDisplayHelper.buildHistoryTimeLabel(historyEntry),
                    CompanionLocationDisplayHelper.buildHistoryValue(context, historyEntry),
                    false
            ));
        }
        return items;
    }

    private List<GuardianReportLineItem> createMemoLines(GuardianReportEntry entry) {
        List<GuardianReportLineItem> items = new ArrayList<>();
        addOptionalLine(items, R.string.guardian_report_line_update, entry.getSession() == null
                ? ""
                : entry.getSession().getGuardianUpdate(), false);
        addOptionalLine(items, R.string.guardian_report_line_photo, entry.getSession() == null
                ? ""
                : entry.getSession().getFieldPhotoNote(), false);
        addOptionalLine(items, R.string.guardian_report_line_medication, entry.getSession() == null
                ? ""
                : entry.getSession().getMedicationNote(), false);
        addOptionalLine(items, R.string.guardian_report_line_pharmacy, entry.getSession() == null
                ? ""
                : entry.getSession().getPharmacySummary(), false);
        if (PharmacyProgressDisplayHelper.shouldShowDetail(entry.getSession())) {
            items.add(new GuardianReportLineItem(
                    context.getString(R.string.guardian_report_line_pharmacy_state),
                    PharmacyProgressDisplayHelper.buildOverallStateLabel(context, entry.getSession()),
                    false
            ));
            items.add(new GuardianReportLineItem(
                    context.getString(R.string.guardian_report_line_pharmacy_detail),
                    PharmacyProgressDisplayHelper.buildStepSummary(context, entry.getSession()),
                    false
            ));
        }
        addOptionalLine(items, R.string.guardian_report_line_guide, buildGuideLine(entry.getHospitalGuide()), false);
        return items;
    }

    private List<GuardianReportSectionModel> createReportSections(GuardianReportEntry entry) {
        List<GuardianReportSectionModel> sections = new ArrayList<>();
        SessionReport report = entry.getSessionReport();
        if (report == null) {
            return sections;
        }

        List<GuardianReportLineItem> hospitalLines = new ArrayList<>();
        addOptionalLine(hospitalLines, R.string.guardian_report_line_report_summary, report.getSummary(), true);
        addOptionalLine(hospitalLines, R.string.guardian_report_line_report_treatment, report.getTreatmentNotes(), false);
        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.guardian_final_report_clinical_section),
                hospitalLines,
                GuardianReportSectionModel.Style.CLINICAL
        );

        List<GuardianReportLineItem> nextVisitLines = new ArrayList<>();
        addOptionalLine(
                nextVisitLines,
                R.string.guardian_report_line_report_next_visit,
                report.getNextVisitAt(),
                true
        );
        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.guardian_final_report_next_visit_section),
                nextVisitLines,
                GuardianReportSectionModel.Style.APPOINTMENT
        );

        List<GuardianReportLineItem> medicationLines = new ArrayList<>();
        MedicationComparisonSummary comparisonSummary =
                MedicationComparisonDisplayHelper.buildSummary(
                        context,
                        entry.getAppointmentRequest(),
                        report
                );
        addOptionalLine(medicationLines, R.string.guardian_report_line_report_medication, report.getMedicationNotes(), true);
        addOptionalLine(medicationLines, R.string.guardian_report_line_report_medication_name, report.getMedicationName(), false);
        addOptionalLine(medicationLines, R.string.guardian_report_line_report_medication_change, report.getMedicationChangeSummary(), false);
        addOptionalLine(medicationLines, R.string.guardian_report_line_report_medication_schedule, report.getMedicationScheduleNote(), false);
        addOptionalLine(
                medicationLines,
                R.string.guardian_report_line_report_medication_decision,
                MedicationComparisonDecisionDisplayHelper.toLabel(
                        context,
                        report.getMedicationComparisonDecision()
                ),
                false
        );
        addOptionalLine(
                medicationLines,
                R.string.guardian_report_line_report_medication_decision_note,
                report.getMedicationComparisonNote(),
                false
        );
        if (comparisonSummary != null) {
            addOptionalLine(
                    medicationLines,
                    R.string.guardian_report_line_report_medication_compare,
                    comparisonSummary.getStatusLabel(),
                    false
            );
            addOptionalLine(
                    medicationLines,
                    R.string.guardian_report_line_report_medication_detail,
                    comparisonSummary.getDetailLabel(),
                    false
            );
            addOptionalLine(
                    medicationLines,
                    R.string.guardian_report_line_report_medication_follow_up,
                    comparisonSummary.getFollowUpLabel(),
                    false
            );
        }
        addSectionIfNotEmpty(
                sections,
                context.getString(R.string.guardian_final_report_medication_section),
                medicationLines,
                GuardianReportSectionModel.Style.MEDICATION
        );
        return sections;
    }

    private void addOptionalLine(List<GuardianReportLineItem> items, int labelResId, String value, boolean emphasized) {
        String normalizedValue = GuardianFinalReportPolicy.optionalText(value);
        if (normalizedValue == null) {
            return;
        }
        items.add(new GuardianReportLineItem(
                context.getString(labelResId),
                normalizedValue,
                emphasized
        ));
    }

    private void addSectionIfNotEmpty(
            List<GuardianReportSectionModel> sections,
            String title,
            List<GuardianReportLineItem> lines,
            GuardianReportSectionModel.Style style
    ) {
        if (lines.isEmpty()) {
            return;
        }
        sections.add(new GuardianReportSectionModel(title, new ArrayList<>(lines), style));
    }

    private String buildPatientDisplay(GuardianReportEntry entry) {
        if (entry.getPatient() != null) {
            return formatter.buildContactText(
                    entry.getPatient().getName(),
                    entry.getPatient().getPhone(),
                    false
            );
        }
        return formatter.buildContactText(
                entry.getAppointmentRequest().getPatientName(),
                entry.getAppointmentRequest().getPatientPhone(),
                true
        );
    }

    private String buildProgressText(GuardianReportEntry entry) {
        CompanionSession session = entry.getSession();
        HospitalGuide guide = entry.getHospitalGuide();
        if (session == null || guide == null) {
            return formatter.toStatusLabel(entry.getAppointmentRequest().getStatus());
        }
        if (guide.getSteps().isEmpty()) {
            return context.getString(R.string.guardian_report_progress_preparing);
        }
        return context.getString(
                R.string.guardian_report_progress_value,
                session.getCurrentStepOrder(),
                guide.getSteps().size(),
                formatter.toSessionStatusLabel(session)
        );
    }

    private String buildGuideLine(@Nullable HospitalGuide guide) {
        if (guide == null) {
            return "";
        }
        if (guide.getSteps().isEmpty()) {
            return context.getString(R.string.guardian_report_guide_preparing);
        }
        return context.getString(
                R.string.guardian_report_guide_line,
                guide.getSteps().size()
        );
    }

    private String formatSharedLocationTime(long updatedAtMillis) {
        return CompanionLocationDisplayHelper.formatSharedLocationTime(updatedAtMillis);
    }
}
