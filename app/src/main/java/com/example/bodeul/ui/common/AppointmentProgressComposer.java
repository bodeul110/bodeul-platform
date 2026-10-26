package com.example.bodeul.ui.common;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.CompanionSession;
import com.example.bodeul.domain.model.GuideStep;
import com.example.bodeul.domain.model.HospitalGuide;
import com.example.bodeul.domain.model.SessionReport;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.util.LegacyManagerLocationSharingPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 예약 상태를 홈/상세 화면에서 공통으로 쓰는 진행 로드맵 모델로 조합한다.
 */
public final class AppointmentProgressComposer {
    private final Context context;
    private final boolean legacyManagerLocationEnabled;

    public AppointmentProgressComposer(Context context) {
        this.context = context.getApplicationContext();
        this.legacyManagerLocationEnabled = LegacyManagerLocationSharingPolicy.isEnabled(context);
    }

    public AppointmentProgressOverviewModel create(
            UserRole viewerRole,
            AppointmentRequest request,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport report,
            @Nullable HospitalGuide guide
    ) {
        return new AppointmentProgressOverviewModel(
                toStatusLabel(request.getStatus()),
                buildOverviewTitle(request.getStatus()),
                buildOverviewBody(viewerRole, request, manager, session, report, guide),
                buildGuideTitle(request.getStatus()),
                buildGuideBody(viewerRole, request.getStatus()),
                createStages(request, manager, session, report, guide)
        );
    }

    private List<AppointmentProgressStageModel> createStages(
            AppointmentRequest request,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport report,
            @Nullable HospitalGuide guide
    ) {
        List<AppointmentProgressStageModel> stages = new ArrayList<>();
        int activeOrder = resolveActiveStageOrder(request);
        stages.add(new AppointmentProgressStageModel(
                1,
                context.getString(R.string.appointment_progress_stage_request_title),
                buildRequestStageBody(request, activeOrder),
                toStageStateLabel(resolveStageState(1, activeOrder)),
                resolveStageState(1, activeOrder)
        ));
        stages.add(new AppointmentProgressStageModel(
                2,
                context.getString(R.string.appointment_progress_stage_match_title),
                buildMatchStageBody(request, manager),
                toStageStateLabel(resolveStageState(2, activeOrder)),
                resolveStageState(2, activeOrder)
        ));
        stages.add(new AppointmentProgressStageModel(
                3,
                context.getString(R.string.appointment_progress_stage_service_title),
                buildServiceStageBody(request, session, guide),
                toStageStateLabel(resolveStageState(3, activeOrder)),
                resolveStageState(3, activeOrder)
        ));
        stages.add(new AppointmentProgressStageModel(
                4,
                context.getString(R.string.appointment_progress_stage_report_title),
                buildReportStageBody(request, report),
                toStageStateLabel(resolveStageState(4, activeOrder)),
                resolveStageState(4, activeOrder)
        ));
        return stages;
    }

    private int resolveActiveStageOrder(AppointmentRequest request) {
        switch (request.getStatus()) {
            case MATCHED:
                return 2;
            case IN_PROGRESS:
                return 3;
            case COMPLETED:
                return 4;
            case CANCELED:
                return TextUtils.isEmpty(request.getManagerUserId()) ? 1 : 2;
            case REQUESTED:
            default:
                return 1;
        }
    }

    private AppointmentProgressStageState resolveStageState(int order, int activeOrder) {
        if (order < activeOrder) {
            return AppointmentProgressStageState.COMPLETED;
        }
        if (order == activeOrder) {
            return AppointmentProgressStageState.ACTIVE;
        }
        return AppointmentProgressStageState.UPCOMING;
    }

    private String buildOverviewTitle(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_progress_title_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_progress_title_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_progress_title_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_progress_title_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_progress_title_requested);
        }
    }

    private String buildOverviewBody(
            UserRole viewerRole,
            AppointmentRequest request,
            @Nullable User manager,
            @Nullable CompanionSession session,
            @Nullable SessionReport report,
            @Nullable HospitalGuide guide
    ) {
        switch (request.getStatus()) {
            case MATCHED:
                return context.getString(
                        R.string.booking_status_progress_body_matched,
                        buildManagerDisplay(manager),
                        buildGuideDisplay(guide)
                );
            case IN_PROGRESS:
                return buildLiveProgressBody(session, guide);
            case COMPLETED:
                return buildCompletedProgressBody(report);
            case CANCELED:
                return context.getString(R.string.booking_status_progress_body_canceled);
            case REQUESTED:
            default:
                return context.getString(
                        R.string.booking_status_progress_body_requested,
                        viewerRole == UserRole.GUARDIAN
                                ? context.getString(R.string.login_role_patient)
                                : context.getString(R.string.login_role_guardian)
                );
        }
    }

    private String buildGuideTitle(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.appointment_progress_guide_title_matched);
            case IN_PROGRESS:
                return context.getString(R.string.appointment_progress_guide_title_in_progress);
            case COMPLETED:
                return context.getString(R.string.appointment_progress_guide_title_completed);
            case CANCELED:
                return context.getString(R.string.appointment_progress_guide_title_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.appointment_progress_guide_title_requested);
        }
    }

    private String buildGuideBody(UserRole viewerRole, AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.appointment_progress_guide_body_matched);
            case IN_PROGRESS:
                return context.getString(viewerRole == UserRole.GUARDIAN
                        ? R.string.appointment_progress_guide_body_in_progress_guardian
                        : R.string.appointment_progress_guide_body_in_progress_patient);
            case COMPLETED:
                return context.getString(R.string.appointment_progress_guide_body_completed);
            case CANCELED:
                return context.getString(R.string.appointment_progress_guide_body_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.appointment_progress_guide_body_requested);
        }
    }

    private String buildRequestStageBody(AppointmentRequest request, int activeOrder) {
        if (request.getStatus() == AppointmentStatus.CANCELED && activeOrder == 1) {
            return context.getString(R.string.appointment_progress_stage_request_body_canceled);
        }
        if (activeOrder == 1) {
            return context.getString(R.string.appointment_progress_stage_request_body_active);
        }
        return context.getString(R.string.appointment_progress_stage_request_body_done);
    }

    private String buildMatchStageBody(AppointmentRequest request, @Nullable User manager) {
        if (request.getStatus() == AppointmentStatus.CANCELED) {
            return context.getString(TextUtils.isEmpty(request.getManagerUserId())
                    ? R.string.appointment_progress_stage_match_body_canceled_before
                    : R.string.appointment_progress_stage_match_body_canceled_after);
        }
        if (!TextUtils.isEmpty(request.getManagerUserId()) || manager != null) {
            return context.getString(
                    R.string.appointment_progress_stage_match_body_assigned,
                    buildManagerDisplay(manager)
            );
        }
        return context.getString(R.string.appointment_progress_stage_match_body_pending);
    }

    private String buildServiceStageBody(
            AppointmentRequest request,
            @Nullable CompanionSession session,
            @Nullable HospitalGuide guide
    ) {
        switch (request.getStatus()) {
            case MATCHED:
                return context.getString(
                        R.string.appointment_progress_stage_service_body_preparing,
                        buildMeetingPlaceDisplay(request)
                );
            case IN_PROGRESS:
                return buildLiveProgressBody(session, guide);
            case COMPLETED:
                return context.getString(R.string.appointment_progress_stage_service_body_done);
            case CANCELED:
                return context.getString(R.string.appointment_progress_stage_service_body_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.appointment_progress_stage_service_body_pending);
        }
    }

    private String buildReportStageBody(AppointmentRequest request, @Nullable SessionReport report) {
        if (request.getStatus() == AppointmentStatus.COMPLETED) {
            if (report != null && !TextUtils.isEmpty(report.getSummary())) {
                return context.getString(
                        R.string.appointment_progress_stage_report_body_ready,
                        report.getSummary()
                );
            }
            return context.getString(R.string.appointment_progress_stage_report_body_ready_pending);
        }
        if (request.getStatus() == AppointmentStatus.CANCELED) {
            return context.getString(R.string.appointment_progress_stage_report_body_canceled);
        }
        return context.getString(R.string.appointment_progress_stage_report_body_pending);
    }

    private String buildLiveProgressBody(@Nullable CompanionSession session, @Nullable HospitalGuide guide) {
        if (session == null) {
            return context.getString(R.string.booking_status_progress_body_in_progress_fallback);
        }

        List<String> lines = new ArrayList<>();
        lines.add(toSessionStatusLabel(session.getStatus()));
        if (guide != null) {
            if (guide.getSteps().isEmpty()) {
                lines.add(context.getString(R.string.booking_status_guide_preparing));
            } else {
                lines.add(context.getString(
                        R.string.booking_status_progress_step_format,
                        session.getCurrentStepOrder(),
                        guide.getSteps().size()
                ));
                GuideStep currentStep = findCurrentStep(guide, session.getCurrentStepOrder());
                if (currentStep != null) {
                    if (!TextUtils.isEmpty(currentStep.getTitle())) {
                        lines.add(context.getString(
                                R.string.booking_status_progress_current_step_format,
                                currentStep.getTitle()
                        ));
                    }
                }
            }
        }
        if (legacyManagerLocationEnabled) {
            addLine(lines, R.string.booking_status_line_live_location, session.getLocationSummary());
        }
        if (!TextUtils.isEmpty(session.getGuardianUpdate())) {
            lines.add(context.getString(
                    R.string.booking_status_progress_update_format,
                    session.getGuardianUpdate()
            ));
        }
        addLine(lines, R.string.booking_status_line_live_photo, session.getFieldPhotoNote());
        addLine(lines, R.string.booking_status_line_live_medication, session.getMedicationNote());
        return TextUtils.join("\n", lines);
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

    private String buildCompletedProgressBody(@Nullable SessionReport report) {
        if (report == null || TextUtils.isEmpty(report.getSummary())) {
            return context.getString(R.string.booking_status_progress_body_completed_pending);
        }

        List<String> lines = new ArrayList<>();
        lines.add(report.getSummary());
        addLine(lines, R.string.booking_status_line_report_treatment, report.getTreatmentNotes());
        addLine(lines, R.string.booking_status_line_report_medication, report.getMedicationNotes());
        addLine(lines, R.string.booking_status_line_report_next_visit, report.getNextVisitAt());
        return TextUtils.join("\n", lines);
    }

    private void addLine(List<String> lines, int labelResId, String value) {
        if (TextUtils.isEmpty(value)) {
            return;
        }
        lines.add(context.getString(
                R.string.appointment_progress_line_format,
                context.getString(labelResId),
                value
        ));
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
        return context.getString(R.string.booking_status_contact_missing);
    }

    private String buildGuideDisplay(@Nullable HospitalGuide guide) {
        if (guide == null) {
            return context.getString(R.string.booking_status_guide_missing);
        }
        if (guide.getSteps().isEmpty()) {
            return context.getString(R.string.booking_status_guide_preparing);
        }
        return context.getString(R.string.booking_status_guide_ready, guide.getSteps().size());
    }

    private String buildMeetingPlaceDisplay(AppointmentRequest request) {
        if (TextUtils.isEmpty(request.getMeetingPlace())) {
            return context.getString(R.string.booking_status_place_missing);
        }
        return request.getMeetingPlace();
    }

    private String toStageStateLabel(AppointmentProgressStageState state) {
        switch (state) {
            case COMPLETED:
                return context.getString(R.string.appointment_progress_state_completed);
            case ACTIVE:
                return context.getString(R.string.appointment_progress_state_active);
            case UPCOMING:
            default:
                return context.getString(R.string.appointment_progress_state_upcoming);
        }
    }

    private String toStatusLabel(AppointmentStatus status) {
        switch (status) {
            case MATCHED:
                return context.getString(R.string.booking_status_matched);
            case IN_PROGRESS:
                return context.getString(R.string.booking_status_in_progress);
            case COMPLETED:
                return context.getString(R.string.booking_status_completed);
            case CANCELED:
                return context.getString(R.string.booking_status_canceled);
            case REQUESTED:
            default:
                return context.getString(R.string.booking_status_requested);
        }
    }

    private String toSessionStatusLabel(SessionStatus status) {
        switch (status) {
            case READY:
                return context.getString(R.string.guardian_report_session_ready);
            case WAITING:
                return context.getString(R.string.guardian_report_session_waiting);
            case IN_TREATMENT:
                return context.getString(R.string.guardian_report_session_treatment);
            case PAYMENT:
                return context.getString(R.string.guardian_report_session_payment);
            case CARE_ENDED:
                return context.getString(R.string.guardian_report_session_care_ended);
            case CANCELED:
                return context.getString(R.string.guardian_report_session_canceled);
            case COMPLETED:
                return context.getString(R.string.guardian_report_session_completed);
            case MEETING:
            default:
                return context.getString(R.string.guardian_report_session_meeting);
        }
    }
}
