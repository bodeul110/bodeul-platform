package com.example.bodeul.ui.manager;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.ui.common.FinalHomeNoticeBinder;
import com.example.bodeul.ui.home.ClientHomeNotice;
import java.util.Arrays;
import java.util.List;

/** 완성본 매니저 홈의 표시만 담당하며 조회와 화면 이동은 Activity에 위임한다. */
public final class ManagerHomeDashboardBinder {
    private final Context context;
    private final View root;

    public ManagerHomeDashboardBinder(Context context, View root) {
        this.context = context;
        this.root = root;
        FinalHomeNoticeBinder.bind(context, root.findViewById(R.id.layoutManagerNoticeContainer), Arrays.asList(
                new ClientHomeNotice(R.drawable.bodeul_final_home_article_1, R.string.final_manager_prepare_label,
                        R.string.final_manager_notice_prepare_title, R.string.final_manager_notice_prepare_body),
                new ClientHomeNotice(R.drawable.bodeul_final_home_article_2, R.string.final_manager_matching_history,
                        R.string.final_manager_notice_matching_title, R.string.final_manager_notice_matching_body),
                new ClientHomeNotice(R.drawable.bodeul_final_home_article_3, R.string.final_manager_guide_label,
                        R.string.final_home_guide_steps_title, R.string.final_manager_notice_steps_body),
                new ClientHomeNotice(R.drawable.bodeul_final_home_article_4, R.string.final_home_support,
                        R.string.final_manager_notice_support_title, R.string.final_manager_notice_support_body)));
    }

    public void bindUser(User user) {
        String name = TextUtils.isEmpty(user.getName()) ? context.getString(R.string.final_manager_name_fallback) : user.getName();
        text(R.id.textManagerGreeting, context.getString(R.string.final_manager_greeting, name));
    }

    public void bindCounts(@Nullable ManagerMatchingOverview overview, boolean failed) {
        int[] views = {R.id.textManagerOngoingCount, R.id.textManagerCompletedCount, R.id.textManagerCanceledCount};
        ManagerMatchingOverview.Filter[] filters = {ManagerMatchingOverview.Filter.ONGOING,
                ManagerMatchingOverview.Filter.COMPLETED, ManagerMatchingOverview.Filter.CANCELED};
        for (int i = 0; i < views.length; i++) text(views[i], overview == null
                ? context.getString(R.string.final_manager_unknown_count) : String.valueOf(overview.count(filters[i])));
        text(R.id.textManagerCountState, context.getString(overview != null ? R.string.final_manager_recent_scope
                : failed ? R.string.final_manager_count_error : R.string.final_manager_count_loading));
        root.findViewById(R.id.buttonManagerCountRetry).setVisibility(failed ? View.VISIBLE : View.GONE);
    }

    public void bindSession(@Nullable ManagerHomeScreenModel model, boolean loaded, boolean failed) {
        boolean active = model != null && model.hasActiveSession();
        root.findViewById(R.id.layoutManagerActiveSession).setVisibility(active || !loaded || failed ? View.VISIBLE : View.GONE);
        if (active) {
            ManagerHomeHeroModel hero = model.getHeroModel();
            text(R.id.textManagerActiveTitle, hero.getTitleText());
            String body = hero.getStatusText() + "\n" + hero.getBodyText();
            ManagerHomeLiveFeedModel feed = model.getLiveFeedModel();
            if (feed != null) body += "\n" + feed.getTimeText() + "\n" + feed.getTitleText()
                    + "\n" + feed.getSubtitleText() + "\n" + feed.getNoteText() + "\n" + feed.getFooterText();
            text(R.id.textManagerActiveBody, body);
            text(R.id.buttonManagerHeroPrimary, hero.getActionText());
        } else {
            text(R.id.textManagerActiveTitle, context.getString(failed
                    ? R.string.final_manager_session_error : R.string.final_manager_session_loading));
            text(R.id.textManagerActiveBody, "");
            text(R.id.buttonManagerHeroPrimary, context.getString(R.string.state_action_retry));
        }
        root.findViewById(R.id.buttonManagerHeroPrimary).setVisibility(active || failed ? View.VISIBLE : View.GONE);
    }

    public void bindAppointments(@Nullable ManagerMatchingOverview overview, boolean failed,
                                 boolean calendar, String date, ManagerMatchingOverview.Filter filter,
                                 @Nullable String activeRequestId, View.OnClickListener openGuide) {
        text(R.id.textManagerAppointmentsTitle, context.getString(calendar
                ? R.string.final_home_calendar : R.string.final_manager_matching_history));
        root.findViewById(R.id.calendarManagerAppointments).setVisibility(calendar ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.textManagerSelectedDate).setVisibility(calendar ? View.VISIBLE : View.GONE);
        text(R.id.textManagerSelectedDate, date);
        root.findViewById(R.id.textManagerUndated).setVisibility(calendar && overview != null && overview.hasUndatedRequests()
                ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.buttonManagerScheduleNote).setVisibility(calendar ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.textManagerScheduleNote).setVisibility(calendar ? View.VISIBLE : View.GONE);
        LinearLayout list = root.findViewById(R.id.layoutManagerAppointmentsList);
        list.removeAllViews();
        List<AppointmentRequest> requests = overview == null ? java.util.Collections.emptyList()
                : overview.select(filter, calendar ? date : null);
        int state = overview == null ? (failed ? R.string.final_manager_count_error : R.string.final_manager_count_loading)
                : overview.count(ManagerMatchingOverview.Filter.ALL) == 0 ? R.string.final_manager_empty_matches
                : R.string.final_manager_filtered_empty;
        text(R.id.textManagerAppointmentsState, context.getString(state));
        root.findViewById(R.id.textManagerAppointmentsState).setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
        for (AppointmentRequest request : requests) {
            View row = LayoutInflater.from(context).inflate(R.layout.item_manager_matching, list, false);
            ((TextView) row.findViewById(R.id.textManagerMatchingStatus)).setText(statusLabel(request.getStatus()));
            ((TextView) row.findViewById(R.id.textManagerMatchingHospital)).setText(orMissing(request.getHospitalName()));
            ((TextView) row.findViewById(R.id.textManagerMatchingDetails)).setText(context.getString(
                    R.string.final_manager_item_summary, orMissing(request.getAppointmentAt()),
                    orMissing(request.getDepartmentName()), orMissing(request.getMeetingPlace())));
            View guide = row.findViewById(R.id.buttonManagerMatchingGuide);
            // 가이드는 현재 활성 세션 하나를 연다. 다른 예약 행에서 그 세션으로 잘못 이동하지 않는다.
            boolean active = activeRequestId != null && activeRequestId.equals(request.getId());
            guide.setVisibility(active ? View.VISIBLE : View.GONE);
            guide.setOnClickListener(openGuide);
            list.addView(row);
        }
    }

    private int statusLabel(@Nullable AppointmentStatus status) {
        if (status == AppointmentStatus.REQUESTED) return R.string.final_manager_item_requested;
        if (status == AppointmentStatus.MATCHED) return R.string.final_manager_item_matched;
        if (status == AppointmentStatus.IN_PROGRESS) return R.string.final_manager_item_in_progress;
        if (status == AppointmentStatus.COMPLETED) return R.string.final_manager_completed;
        if (status == AppointmentStatus.CANCELED) return R.string.final_manager_canceled;
        return R.string.final_manager_item_missing;
    }

    private String orMissing(String value) {
        return TextUtils.isEmpty(value) ? context.getString(R.string.final_manager_item_missing) : value;
    }

    private void text(int id, CharSequence text) { ((TextView) root.findViewById(id)).setText(text); }
}
