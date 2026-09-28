package com.example.bodeul.ui.manager;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.ui.booking.BookingMeetingPointCatalog;
import com.example.bodeul.ui.common.HospitalMapPreviewModel;
import com.example.bodeul.util.CompanionLocationDisplayHelper;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;
import com.example.bodeul.util.PharmacyProgressDisplayHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 매니저 동행 가이드 화면에 필요한 상태를 화면 모델로 조합한다.
 */
public final class ManagerGuideCoordinator {
    private final Context context;
    private final ManagerGuidePresentationFormatter formatter;
    private final boolean legacyManagerLocationEnabled;

    public ManagerGuideCoordinator(Context context, ManagerGuidePresentationFormatter formatter) {
        this.context = context.getApplicationContext();
        this.formatter = formatter;
        this.legacyManagerLocationEnabled = LegacyManagerLocationSharingPolicy.isEnabled(context);
    }

    public ManagerGuideScreenModel createScreenModel(@Nullable ManagerDashboard dashboard, boolean isFirebaseBacked) {
        if (dashboard == null) {
            return createEmptyScreenModel(isFirebaseBacked);
        }

        CompanionSession session = dashboard.getSession();
        SessionReport report = dashboard.getSessionReport();
        List<ManagerGuideStageModel> stages = buildStages(dashboard);
        GuideStep focusStep = findFocusStep(dashboard);
        String presentationStepCode = ManagerGuidePresentationStepResolver.resolve(
                focusStep.getCode());
        ManagerGuideProgressPolicy.Decision advanceDecision =
                ManagerGuideProgressPolicy.resolve(
                        session,
                        dashboard.getHospitalGuide().getSteps().size(),
                        focusStep.getCode());
        ManagerGuidePrimaryAction primaryAction = resolvePrimaryAction(
                advanceDecision,
                focusStep.getCode());
        ManagerGuideSectionVisibility sectionVisibility =
                resolveSectionVisibility(focusStep, primaryAction);
        if (!legacyManagerLocationEnabled) {
            sectionVisibility = sectionVisibility.withoutLocation();
        }

        return new ManagerGuideScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                buildToolbarTitle(focusStep),
                context.getString(R.string.guide_progress_subtitle),
                formatter.toSessionStatusLabel(session.getStatus()),
                context.getString(R.string.guide_hero_title_format, dashboard.getPatient().getName()),
                formatter.buildHeroBody(dashboard),
                formatter.buildHeroNote(dashboard),
                valueOrFallback(dashboard.getPatient().getName()),
                resolveMeetingPlace(dashboard),
                valueOrFallback(dashboard.getAppointmentRequest().getDepartmentName()),
                createMapActions(dashboard, focusStep),
                buildHospitalMapPreviewModel(dashboard),
                stages,
                createFocusModel(focusStep, session, advanceDecision),
                sectionVisibility,
                buildMemoSummary(session),
                focusStep.getCode(),
                presentationStepCode,
                legacyManagerLocationEnabled
                        ? CompanionLocationDisplayHelper.buildLiveSharingStatus(context, session)
                        : "",
                legacyManagerLocationEnabled
                        ? CompanionLocationDisplayHelper.buildLocationHistory(context, session, 3)
                        : "",
                legacyManagerLocationEnabled ? session.getLocationSummary() : "",
                session.getGuardianUpdate(),
                session.getFieldPhotoNote(),
                session.isPreConsultationConfirmed(),
                session.getMedicationNote(),
                session.getPharmacySummary(),
                PharmacyProgressDisplayHelper.buildStepSummary(context, session),
                buildPrescriptionActionLabel(session),
                buildPharmacyActionLabel(session),
                buildMedicationGuidanceActionLabel(session),
                report == null ? session.getManagerJournal() : report.getSummary(),
                report == null ? "" : report.getTreatmentNotes(),
                report == null ? "" : report.getMedicationName(),
                report == null ? "" : report.getMedicationChangeSummary(),
                report == null ? "" : report.getMedicationScheduleNote(),
                report == null ? null : report.getMedicationComparisonDecision(),
                report == null ? "" : report.getMedicationComparisonNote(),
                report == null ? "" : report.getNextVisitAt(),
                buildAdvanceButtonLabel(focusStep, advanceDecision, primaryAction),
                primaryAction,
                isPrimaryActionEnabled(advanceDecision),
                context.getString(advanceDecision.getState()
                        == ManagerGuideProgressPolicy.State.REPORT_RETRY
                        ? R.string.guide_action_report_retry
                        : (report == null
                                ? R.string.guide_report_submit
                                : R.string.guide_report_update)),
                legacyManagerLocationEnabled && session.isLiveLocationSharingActive(),
                isStepInputEnabled(advanceDecision)
        );
    }

    private ManagerGuideScreenModel createEmptyScreenModel(boolean isFirebaseBacked) {
        return new ManagerGuideScreenModel(
                EnvironmentModeBadgeHelper.resolveUserFacingLabel(context, isFirebaseBacked),
                context.getString(R.string.guide_progress_title),
                context.getString(R.string.guide_progress_subtitle),
                context.getString(R.string.guide_status_pending),
                context.getString(R.string.guide_hero_title_empty),
                context.getString(R.string.guide_hero_body_empty),
                context.getString(R.string.guide_hero_note_empty),
                "",
                "",
                "",
                Collections.emptyList(),
                new HospitalMapPreviewModel(Collections.emptyList(), "", ""),
                Collections.emptyList(),
                new ManagerGuideFocusModel(
                        context.getString(R.string.guide_focus_badge_empty),
                        context.getString(R.string.guide_focus_title_empty),
                        context.getString(R.string.guide_focus_body_empty),
                        context.getString(R.string.guide_focus_preview_label),
                        context.getString(R.string.guide_focus_preview_empty),
                        R.drawable.bg_service_thumb_cool,
                        false,
                        "",
                        ""
                ),
                ManagerGuideSectionVisibility.hidden(),
                Collections.emptyList(),
                "",
                "",
                context.getString(R.string.live_location_status_inactive_empty),
                context.getString(R.string.live_location_history_empty),
                "",
                "",
                "",
                false,
                "",
                "",
                context.getString(R.string.pharmacy_progress_step_summary_pending),
                context.getString(R.string.guide_prescription_mark_collected),
                context.getString(R.string.guide_pharmacy_mark_completed),
                context.getString(R.string.guide_medication_guidance_mark_completed),
                "",
                "",
                "",
                "",
                "",
                null,
                "",
                "",
                context.getString(R.string.guide_button_waiting),
                ManagerGuidePrimaryAction.NONE,
                false,
                context.getString(R.string.guide_report_submit),
                false,
                false
        );
    }

    private List<ManagerGuideMapActionModel> createMapActions(
            ManagerDashboard dashboard,
            GuideStep focusStep
    ) {
        String hospitalName = dashboard.getAppointmentRequest().getHospitalName();
        String departmentName = dashboard.getAppointmentRequest().getDepartmentName();
        String meetingPlace = resolveMeetingPlace(dashboard);
        String fallbackQuery = buildExternalMapFallbackQuery(hospitalName);
        CompanionSession session = dashboard.getSession();

        double hospitalLat = dashboard.getAppointmentRequest().getHospitalLatitude();
        double hospitalLng = dashboard.getAppointmentRequest().getHospitalLongitude();
        boolean hasCoordinates = hospitalLat != 0.0 || hospitalLng != 0.0;
        String meetingMapUrl = hasCoordinates
                ? buildKakaoMapUrl(hospitalName + " " + meetingPlace, hospitalLat, hospitalLng)
                : null;

        List<ManagerGuideMapActionModel> actions = new ArrayList<>();
        if (isMeetingStep(focusStep)) {
            actions.add(new ManagerGuideMapActionModel(
                    context.getString(R.string.guide_map_action_meeting_title),
                    context.getString(R.string.guide_map_action_meeting_body, meetingPlace),
                    context.getString(R.string.guide_meeting_map_action),
                    fallbackQuery,
                    meetingMapUrl
            ));
            return actions;
        }
        if (legacyManagerLocationEnabled
                && (!TextUtils.isEmpty(session.getLocationSummary())
                || session.hasSharedLocationCoordinates())) {
            actions.add(new ManagerGuideMapActionModel(
                    context.getString(R.string.guide_map_action_shared_title),
                    buildSharedLocationBody(session, meetingPlace),
                    context.getString(R.string.guide_map_action_shared_button),
                    fallbackQuery,
                    buildSharedLocationDirectUrl(session)
            ));
        }

        String hospitalMapUrl = hasCoordinates
                ? buildKakaoMapUrl(hospitalName, hospitalLat, hospitalLng)
                : resolveHospitalFallbackUrl(hospitalName);

        actions.add(new ManagerGuideMapActionModel(
                context.getString(R.string.guide_map_action_hospital_title),
                context.getString(R.string.guide_map_action_hospital_body, hospitalName, departmentName),
                context.getString(R.string.guide_map_action_hospital_button),
                fallbackQuery,
                hospitalMapUrl
        ));

        actions.add(new ManagerGuideMapActionModel(
                context.getString(R.string.guide_map_action_meeting_title),
                context.getString(R.string.guide_map_action_meeting_body, meetingPlace),
                context.getString(R.string.guide_map_action_meeting_button),
                fallbackQuery,
                meetingMapUrl
        ));

        if (shouldShowPharmacyRouteAction(session)) {
            actions.add(ManagerGuideMapActionModel.createKakaoPlaceSearch(
                    context.getString(R.string.guide_map_action_pharmacy_title),
                    context.getString(R.string.guide_map_action_pharmacy_body),
                    context.getString(R.string.guide_map_action_pharmacy_button)
            ));
        }
        return actions;
    }

    static String buildExternalMapFallbackQuery(String hospitalName) {
        return hospitalName == null ? "" : hospitalName.trim();
    }

    private String buildToolbarTitle(GuideStep focusStep) {
        if (focusStep == null || focusStep.getOrder() <= 0) {
            return context.getString(R.string.guide_progress_title);
        }
        return context.getString(
                R.string.guide_step_toolbar_title,
                focusStep.getOrder(),
                valueOrFallback(focusStep.getTitle())
        );
    }

    private String resolveMeetingPlace(ManagerDashboard dashboard) {
        String meetingPlace = dashboard.getAppointmentRequest().getMeetingPlace();
        if (!TextUtils.isEmpty(meetingPlace)) {
            return meetingPlace;
        }
        return context.getString(
                R.string.guide_map_default_meeting_place,
                valueOrFallback(dashboard.getAppointmentRequest().getHospitalName())
        );
    }

    private boolean isMeetingStep(GuideStep step) {
        return step != null && ManagerGuidePresentationStepResolver.matches(
                step.getCode(), "MEETING_CONFIRMATION");
    }

    private String valueOrFallback(String value) {
        return TextUtils.isEmpty(value)
                ? context.getString(R.string.guide_meeting_value_missing)
                : value.trim();
    }

    private List<ManagerGuideMemoItem> buildMemoSummary(CompanionSession session) {
        return ManagerGuideMemoSummaryBuilder.build(
                List.of(
                        new ManagerGuideMemoSummaryBuilder.Candidate(
                                context.getString(R.string.guide_memo_route_title),
                                legacyManagerLocationEnabled ? session.getLocationSummary() : ""),
                        new ManagerGuideMemoSummaryBuilder.Candidate(
                                context.getString(R.string.guide_memo_guardian_title),
                                session.getGuardianUpdate()),
                        new ManagerGuideMemoSummaryBuilder.Candidate(
                                context.getString(R.string.guide_memo_field_title),
                                session.getFieldPhotoNote()),
                        new ManagerGuideMemoSummaryBuilder.Candidate(
                                context.getString(R.string.guide_memo_medication_title),
                                session.getMedicationNote()),
                        new ManagerGuideMemoSummaryBuilder.Candidate(
                                context.getString(R.string.guide_memo_pharmacy_title),
                                session.getPharmacySummary())),
                context.getString(R.string.guide_memo_empty));
    }

    static boolean shouldShowPharmacyRouteAction(CompanionSession session) {
        return ManagerGuideStepRegistry.isPharmacyRoute(session.getCurrentStepCode());
    }

    private String buildKakaoMapUrl(String label, double latitude, double longitude) {
        return "https://map.kakao.com/link/map/"
                + Uri.encode(label)
                + ","
                + latitude
                + ","
                + longitude;
    }

    private HospitalMapPreviewModel buildHospitalMapPreviewModel(ManagerDashboard dashboard) {
        List<com.example.bodeul.domain.model.BookingMeetingPointOption> pointOptions =
                BookingMeetingPointCatalog.createPointOptions(
                        context,
                        dashboard.getAppointmentRequest().getHospitalName(),
                        dashboard.getAppointmentRequest().getDepartmentName()
                );
        String selectedPointId = BookingMeetingPointCatalog.resolveSelectedPointId(
                pointOptions,
                dashboard.getAppointmentRequest().getMeetingPlace()
        );
        String highlightedPointId = BookingMeetingPointCatalog.POINT_ID_PHARMACY.equals(selectedPointId)
                ? ""
                : BookingMeetingPointCatalog.POINT_ID_PHARMACY;
        return new HospitalMapPreviewModel(pointOptions, selectedPointId, highlightedPointId);
    }

    @Nullable
    private String resolveHospitalFallbackUrl(String hospitalName) {
        if (TextUtils.isEmpty(hospitalName)) {
            return null;
        }
        if (hospitalName.contains("서울대") || hospitalName.contains("서울대학교병원")) {
            return "https://www.snuh.org/intro/locations/map.do";
        }
        return null;
    }

    private List<ManagerGuideStageModel> buildStages(ManagerDashboard dashboard) {
        List<ManagerGuideStageModel> items = new ArrayList<>();
        CompanionSession session = dashboard.getSession();
        int totalSteps = dashboard.getHospitalGuide().getSteps().size();
        for (GuideStep step : dashboard.getHospitalGuide().getSteps()) {
            ManagerGuideStageState state = resolveStageState(session, step, totalSteps);
            items.add(new ManagerGuideStageModel(
                    step.getOrder(),
                    step.getTitle(),
                    formatter.summarize(step.getDescription()),
                    formatter.toStageStateLabel(state),
                    state
            ));
        }
        return items;
    }

    private ManagerGuideFocusModel createFocusModel(
            GuideStep focusStep,
            CompanionSession session,
            ManagerGuideProgressPolicy.Decision advanceDecision
    ) {
        GuideStep presentationStep =
                ManagerGuidePresentationStepResolver.toPresentationStep(focusStep);
        ManagerGuideVideoGuidancePolicy.Result videoGuidance =
                ManagerGuideVideoGuidancePolicy.resolve(presentationStep);
        String videoGuidanceBody = videoGuidance.getFallbackText().isEmpty()
                ? context.getString(R.string.guide_video_fallback_default)
                : videoGuidance.getFallbackText();
        String videoGuidanceTitle = videoGuidance.hasRegisteredAsset()
                ? context.getString(R.string.guide_video_asset_registered_title)
                : context.getString(R.string.guide_video_unavailable_title);
        if (focusStep.getOrder() <= 0) {
            return new ManagerGuideFocusModel(
                    context.getString(R.string.guide_focus_badge_preparing),
                    context.getString(R.string.guide_focus_title_preparing),
                    focusStep.getDescription(),
                    context.getString(R.string.guide_focus_preview_label),
                    buildBlockedGuidance(advanceDecision),
                    R.drawable.bg_service_thumb_cool,
                    false,
                    "",
                    ""
            );
        }
        return new ManagerGuideFocusModel(
                context.getString(R.string.guide_focus_badge_format, focusStep.getOrder()),
                context.getString(R.string.guide_focus_title_format, focusStep.getOrder(), focusStep.getTitle()),
                focusStep.getDescription(),
                context.getString(R.string.guide_focus_preview_label),
                shouldShowBlockedGuidance(advanceDecision)
                        ? buildBlockedGuidance(advanceDecision)
                        : formatter.buildFocusPreviewBody(presentationStep, session),
                formatter.resolveFocusPreviewBackground(presentationStep),
                videoGuidance.isVisible(),
                videoGuidanceTitle,
                videoGuidanceBody
        );
    }

    private GuideStep findFocusStep(ManagerDashboard dashboard) {
        List<GuideStep> steps = dashboard.getHospitalGuide().getSteps();
        if (steps.isEmpty()) {
            return new GuideStep(
                    "",
                    0,
                    context.getString(R.string.guide_focus_title_preparing),
                    context.getString(R.string.guide_empty_steps_server)
            );
        }

        GuideStep resolved = ManagerGuideFocusResolver.resolve(
                steps,
                dashboard.getSession());
        return resolved == null ? steps.get(steps.size() - 1) : resolved;
    }

    private ManagerGuideStageState resolveStageState(CompanionSession session, GuideStep step, int totalSteps) {
        if (session.getStatus() == SessionStatus.COMPLETED) {
            return step.getOrder() <= Math.min(session.getCurrentStepOrder(), totalSteps)
                    ? ManagerGuideStageState.COMPLETED
                    : ManagerGuideStageState.UPCOMING;
        }
        if (step.getOrder() < session.getCurrentStepOrder()) {
            return ManagerGuideStageState.COMPLETED;
        }
        if (step.getOrder() == session.getCurrentStepOrder()) {
            return ManagerGuideStageState.ACTIVE;
        }
        return ManagerGuideStageState.UPCOMING;
    }

    private String buildAdvanceButtonLabel(
            GuideStep focusStep,
            ManagerGuideProgressPolicy.Decision decision,
            ManagerGuidePrimaryAction primaryAction
    ) {
        if (primaryAction == ManagerGuidePrimaryAction.SUBMIT_REPORT) {
            return decision.getState() == ManagerGuideProgressPolicy.State.REPORT_RETRY
                    ? context.getString(R.string.guide_action_report_retry)
                    : context.getString(R.string.guide_action_journal_complete);
        }
        if (primaryAction == ManagerGuidePrimaryAction.END_CARE) {
            return context.getString(R.string.guide_action_care_complete);
        }
        switch (decision.getState()) {
            case COMPLETED:
                return context.getString(R.string.guide_button_done);
            case LAST_STEP:
                return context.getString(R.string.guide_button_last);
            case REPORT_RETRY:
                return context.getString(R.string.guide_action_report_retry);
            case GUIDE_NOT_READY:
                return context.getString(R.string.guide_button_preparing);
            case CONTRACT_MISMATCH:
                return context.getString(R.string.guide_button_review_required);
            case INPUT_REQUIRED:
                return context.getString(R.string.guide_button_input_required);
            case BLOCKED:
                return context.getString(R.string.guide_button_blocked);
            case ADVANCE:
            default:
                return buildStepActionLabel(focusStep);
        }
    }

    static ManagerGuidePrimaryAction resolvePrimaryAction(
            ManagerGuideProgressPolicy.Decision decision
    ) {
        return resolvePrimaryAction(decision, "");
    }

    static ManagerGuidePrimaryAction resolvePrimaryAction(
            ManagerGuideProgressPolicy.Decision decision,
            String focusStepCode
    ) {
        switch (decision.getState()) {
            case ADVANCE:
                return "CARE_COMPLETION".equals(
                        focusStepCode == null ? "" : focusStepCode.trim())
                        ? ManagerGuidePrimaryAction.END_CARE
                        : ManagerGuidePrimaryAction.ADVANCE;
            case LAST_STEP:
            case REPORT_RETRY:
                return ManagerGuidePrimaryAction.SUBMIT_REPORT;
            default:
                return ManagerGuidePrimaryAction.NONE;
        }
    }

    static ManagerGuideSectionVisibility resolveSectionVisibility(
            GuideStep focusStep,
            ManagerGuidePrimaryAction primaryAction
    ) {
        GuideStep presentationStep =
                ManagerGuidePresentationStepResolver.toPresentationStep(focusStep);
        ManagerGuideSectionVisibility visibility =
                ManagerGuideSectionVisibility.forStep(presentationStep);
        if (primaryAction == ManagerGuidePrimaryAction.SUBMIT_REPORT) {
            return visibility.withReportSection();
        }
        return visibility;
    }

    static boolean isPrimaryActionEnabled(ManagerGuideProgressPolicy.Decision decision) {
        return resolvePrimaryAction(decision) != ManagerGuidePrimaryAction.NONE;
    }

    static boolean isStepInputEnabled(ManagerGuideProgressPolicy.Decision decision) {
        return decision.getState() == ManagerGuideProgressPolicy.State.ADVANCE
                || decision.getState() == ManagerGuideProgressPolicy.State.LAST_STEP
                || decision.getState() == ManagerGuideProgressPolicy.State.REPORT_RETRY
                || decision.getState() == ManagerGuideProgressPolicy.State.INPUT_REQUIRED;
    }

    private String buildStepActionLabel(GuideStep step) {
        String code = step == null
                ? ""
                : ManagerGuidePresentationStepResolver.resolve(step.getCode());
        switch (code) {
            case "MEETING_CONFIRMATION":
                return context.getString(R.string.guide_action_meeting_complete);
            case "HOSPITAL_ROUTE":
                return context.getString(R.string.guide_action_route_confirmed);
            case "RECEPTION_QUEUE":
                return context.getString(R.string.guide_action_reception_complete);
            case "VITALS_CHECK":
                return context.getString(R.string.guide_vitals_save_and_continue);
            case "PRE_CONSULTATION":
                return context.getString(R.string.guide_action_consultation_ready);
            case "CONSULTATION_SUPPORT":
                return context.getString(R.string.guide_action_consultation_complete);
            case "CONSULTATION_SUMMARY":
                return context.getString(R.string.guide_action_summary_complete);
            case "PAYMENT_EVIDENCE":
                return context.getString(R.string.guide_action_payment_complete);
            case "PHARMACY_ROUTE":
                return context.getString(R.string.guide_action_pharmacy_arrived);
            case "PRESCRIPTION_DOCUMENTS":
                return context.getString(R.string.guide_action_prescription_complete);
            case "MEDICATION_CONFIRMATION":
                return context.getString(R.string.guide_action_medication_complete);
            case "CARE_COMPLETION":
                return context.getString(R.string.guide_action_care_complete);
            default:
                return context.getString(R.string.guide_button_next);
        }
    }

    private boolean shouldShowBlockedGuidance(ManagerGuideProgressPolicy.Decision decision) {
        return decision.getState() == ManagerGuideProgressPolicy.State.GUIDE_NOT_READY
                || decision.getState() == ManagerGuideProgressPolicy.State.CONTRACT_MISMATCH
                || decision.getState() == ManagerGuideProgressPolicy.State.INPUT_REQUIRED
                || decision.getState() == ManagerGuideProgressPolicy.State.BLOCKED;
    }

    private String buildBlockedGuidance(ManagerGuideProgressPolicy.Decision decision) {
        switch (decision.getState()) {
            case CONTRACT_MISMATCH:
                return context.getString(R.string.guide_blocked_contract_mismatch);
            case INPUT_REQUIRED:
                return context.getString(R.string.guide_blocked_input_required);
            case BLOCKED:
                return context.getString(R.string.guide_blocked_unknown);
            case GUIDE_NOT_READY:
            default:
                return context.getString(R.string.guide_empty_steps_server);
        }
    }

    private String buildPharmacyActionLabel(CompanionSession session) {
        return context.getString(session.isPharmacyCompleted()
                ? R.string.guide_pharmacy_mark_incomplete
                : R.string.guide_pharmacy_mark_completed);
    }

    private String buildPrescriptionActionLabel(CompanionSession session) {
        return context.getString(session.isPrescriptionCollected()
                ? R.string.guide_prescription_mark_pending
                : R.string.guide_prescription_mark_collected);
    }

    private String buildMedicationGuidanceActionLabel(CompanionSession session) {
        return context.getString(session.isMedicationGuidanceCompleted()
                ? R.string.guide_medication_guidance_mark_pending
                : R.string.guide_medication_guidance_mark_completed);
    }

    private String buildSharedLocationBody(CompanionSession session, String fallbackPlace) {
        String locationText = TextUtils.isEmpty(session.getLocationSummary())
                ? fallbackPlace
                : session.getLocationSummary();
        if (session.getSharedLocationUpdatedAtMillis() <= 0L) {
            return context.getString(R.string.guide_map_action_shared_body, locationText);
        }
        return context.getString(
                R.string.guide_map_action_shared_body_with_time,
                locationText,
                CompanionLocationDisplayHelper.formatSharedLocationTime(
                        session.getSharedLocationUpdatedAtMillis()
                )
        );
    }

    @Nullable
    private String buildSharedLocationDirectUrl(CompanionSession session) {
        if (!session.hasSharedLocationCoordinates()) {
            return null;
        }
        return String.format(
                Locale.US,
                "kakaomap://look?p=%1$.6f,%2$.6f",
                session.getSharedLatitude(),
                session.getSharedLongitude()
        );
    }
}
