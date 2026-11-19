package com.example.bodeul.ui.manager;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.ui.common.GuideTimelineMarker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/** 완성본의 목록/입력 이동만 담당하며 저장과 단계 전환은 기존 바인더에 맡긴다. */
final class ManagerGuideFinalBinder {
    private static final String STATE_KEY = "guide.final.routeKey";
    private static final String STATE_DETAILS = "guide.final.details";
    private final View root;
    private final Context context;
    private final View detailsScroll;
    private final View timelineScroll;
    private final LinearLayout timeline;
    private final View detailsFooter;
    private final View timelineFooter;
    private final TextView toolbarTitle;
    private final MaterialButton finishButton;
    private String routeKey;
    private boolean details;
    private boolean available;
    private boolean busy;
    private boolean initialDetails;
    private ManagerGuideScreenModel model;

    ManagerGuideFinalBinder(View root, Bundle state, boolean initialDetails) {
        this.root = root;
        this.context = root.getContext();
        this.initialDetails = initialDetails;
        routeKey = state == null ? null : state.getString(STATE_KEY);
        details = state != null && state.getBoolean(STATE_DETAILS);
        detailsScroll = root.findViewById(R.id.finalGuideDetailScroll);
        timelineScroll = root.findViewById(R.id.finalGuideTimelineScroll);
        timeline = root.findViewById(R.id.finalGuideTimeline);
        detailsFooter = root.findViewById(R.id.managerGuideBottomAction);
        timelineFooter = root.findViewById(R.id.finalGuideTimelineFooter);
        toolbarTitle = root.findViewById(R.id.textFinalGuideToolbar);
        finishButton = root.findViewById(R.id.buttonFinalGuideFinish);
        finishButton.setOnClickListener(v -> openDetails());
    }

    void bind(ManagerGuideScreenModel screen, ManagerDashboard dashboard, boolean busy) {
        model = screen;
        this.busy = busy;
        available = dashboard != null && dashboard.getSession() != null;
        if (!available) {
            showState();
            return;
        }
        String nextKey = dashboard.getSession().getId() + ":" + screen.getCurrentStepCode();
        if (!nextKey.equals(routeKey)) {
            details = routeKey == null && initialDetails;
            initialDetails = false;
            routeKey = nextKey;
        }
        List<ManagerGuideTimelinePolicy.Item> items = ManagerGuideTimelinePolicy.build(
                dashboard.getHospitalGuide().getSteps(), screen.getStages());
        timeline.removeAllViews();
        for (int index = 0; index < items.size(); index++) {
            bindItem(items.get(index), index == items.size() - 1, busy);
        }
        int group = ManagerGuideTimelinePolicy.groupOf(screen.getCurrentStepCode());
        boolean finalStage = group == 10 || "LEGACY_CORE_RETURN_AND_CLOSE".equals(
                screen.getCurrentStepCode());
        finishButton.setEnabled(finalStage && !busy);
        finishButton.setText(finalStage && !screen.isInputsEnabled()
                ? R.string.final_guide_journal : R.string.final_guide_final_check);
        styleDetails(screen);
        applyRoute();
    }

    private void bindItem(ManagerGuideTimelinePolicy.Item item, boolean last, boolean busy) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_final_guide_timeline, timeline, false);
        boolean active = item.state() == ManagerGuideStageState.ACTIVE;
        boolean done = item.state() == ManagerGuideStageState.COMPLETED;
        String title = item.group > 0 ? context.getString(GuideDisplayGroups.title(item.group)) : item.fallbackTitle;
        GuideTimelineMarker.bind(row,
                item.group > 0 ? item.group : timeline.getChildCount() + 1, active, done, last);
        TextView label = row.findViewById(R.id.finalGuideTimelineTitle);
        label.setText(title);
        label.setTextColor(color(done || active ? R.color.final_text_primary : R.color.final_text_secondary));
        ((ImageView) row.findViewById(R.id.finalGuideTimelineNext)).setImageResource(
                active || done ? R.drawable.bodeul_final_guide_next_black : R.drawable.bodeul_final_guide_next_gray);
        View header = row.findViewById(R.id.finalGuideTimelineHeader);
        header.setBackgroundColor(color(done || active ? R.color.final_guide_light_blue : R.color.final_input_surface));
        MaterialCardView card = row.findViewById(R.id.finalGuideTimelineCard);
        card.setStrokeWidth(done ? 0 : dp(1));
        card.setStrokeColor(color(active ? R.color.final_brand_primary : R.color.final_guide_outline));
        row.findViewById(R.id.finalGuideTimelineBody).setVisibility(done ? View.GONE : View.VISIBLE);
        TextView body = row.findViewById(R.id.finalGuideTimelineDescription);
        body.setText(description(item));
        MaterialButton open = row.findViewById(R.id.buttonFinalGuideOpenStep);
        open.setVisibility(active ? View.VISIBLE : View.GONE);
        open.setEnabled(!busy);
        open.setText(item.group == 6 || item.group == 8 ? R.string.final_guide_resume : R.string.final_guide_open);
        open.setOnClickListener(v -> openDetails());
        header.setEnabled(!busy && (active || done));
        header.setFocusable(active || done);
        header.setClickable(active || done);
        header.setContentDescription(context.getString(R.string.final_guide_timeline_accessibility,
                item.group > 0 ? item.group : timeline.getChildCount() + 1, title, context.getString(done ? R.string.final_guide_done
                        : active ? R.string.final_guide_active : R.string.final_guide_upcoming)));
        header.setOnClickListener(v -> {
            if (active) openDetails();
            else if (done) new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(String.join("\n\n", item.descriptions))
                    .setPositiveButton(android.R.string.ok, null).show();
        });
        timeline.addView(row);
    }

    private String description(ManagerGuideTimelinePolicy.Item item) {
        if (item.group == 1) return model.getMeetingPatient() + "\n" + model.getMeetingPlace();
        if (item.group == 2) return model.getMeetingDepartment() + "\n"
                + context.getString(R.string.final_guide_hospital_helper);
        int help = switch (item.group) {
            case 3 -> R.string.final_guide_reception_helper;
            case 4 -> R.string.guide_vitals_helper_unknown;
            case 5 -> R.string.guide_pre_consultation_screen_subtitle;
            case 6 -> R.string.final_guide_consultation_helper;
            case 7 -> R.string.final_guide_payment_helper;
            case 8 -> R.string.final_guide_pharmacy_helper;
            case 9 -> R.string.final_guide_return_helper;
            default -> R.string.final_guide_step_help;
        };
        return context.getString(help);
    }

    private void styleDetails(ManagerGuideScreenModel screen) {
        for (int id : new int[]{R.id.guideDefaultToolbar, R.id.finalGuideLegacyToolbars,
                R.id.cardGuideLegacySummary, R.id.textGuideSubtitle, R.id.textGuideActionsTitle,
                R.id.textGuideActionsHelper, R.id.textGuideMode}) root.findViewById(id).setVisibility(View.GONE);
        root.findViewById(R.id.guideScrollContent).setPadding(dp(15), dp(20), dp(15), dp(24));
        String step = screen.getPresentationStepCode();
        boolean hospital = "HOSPITAL_ROUTE".equals(step);
        boolean unknown = ManagerGuideTimelinePolicy.groupOf(screen.getCurrentStepCode()) == 0;
        root.findViewById(R.id.finalGuideHospitalIntro).setVisibility(hospital ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.cardGuideLegacyFocus).setVisibility(hospital || unknown ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.viewGuideFocusPreview).setVisibility(View.GONE);
        MaterialCardView map = root.findViewById(R.id.cardGuideMap);
        map.setRadius(dp(15));
        map.setCardElevation(0);
        map.setStrokeColor(color(R.color.final_line));
        root.findViewById(R.id.layoutGuideMapHeader).setVisibility(View.GONE);
        MaterialButton advance = root.findViewById(R.id.buttonAdvanceGuide);
        advance.setIcon(null);
        if (screen.getPrimaryAction() == ManagerGuidePrimaryAction.ADVANCE) {
            int label = switch (step) {
                case "MEETING_CONFIRMATION" -> R.string.final_guide_meeting_done;
                case "HOSPITAL_ROUTE" -> R.string.final_guide_hospital_done;
                case "VITALS_CHECK" -> R.string.final_guide_vitals_save;
                case "PAYMENT_EVIDENCE" -> R.string.final_guide_payment_done;
                case "PHARMACY_ROUTE" -> R.string.final_guide_pharmacy_done;
                case "MEDICATION_CONFIRMATION" -> R.string.final_guide_medication_done;
                case "CARE_COMPLETION" -> R.string.final_guide_return_done;
                default -> 0;
            };
            if (label != 0) advance.setText(label);
        }
    }

    private void openDetails() {
        if (!available || busy) return;
        details = true;
        applyRoute();
    }

    boolean backToTimeline() {
        if (!available || !details) return false;
        InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(root.getWindowToken(), 0);
        details = false;
        applyRoute();
        return true;
    }

    private void applyRoute() {
        detailsScroll.setVisibility(details ? View.VISIBLE : View.GONE);
        detailsFooter.setVisibility(details ? View.VISIBLE : View.GONE);
        timelineScroll.setVisibility(details ? View.GONE : View.VISIBLE);
        timelineFooter.setVisibility(details ? View.GONE : View.VISIBLE);
        int group = ManagerGuideTimelinePolicy.groupOf(model.getCurrentStepCode());
        toolbarTitle.setText(details ? (group == 0 ? model.getTitle() : context.getString(GuideDisplayGroups.title(group)))
                : context.getString(R.string.final_guide_title));
        toolbarTitle.setTextSize(details ? 14 : 22);
        toolbarTitle.setGravity(details ? Gravity.CENTER : Gravity.START | Gravity.CENTER_VERTICAL);
        toolbarTitle.setTextColor(color(details ? R.color.final_text_accent_accessible : R.color.final_text_primary));
        toolbarTitle.setPadding(0, dp(10), details ? dp(33) : 0, dp(10));
        root.findViewById(R.id.buttonFinalGuideBack).setContentDescription(context.getString(
                details ? R.string.final_guide_overview_back : R.string.guide_back));
    }

    void showState() {
        available = false;
        timelineScroll.setVisibility(View.GONE);
        timelineFooter.setVisibility(View.GONE);
        detailsScroll.setVisibility(View.VISIBLE);
        detailsFooter.setVisibility(View.GONE);
        root.findViewById(R.id.guideDefaultToolbar).setVisibility(View.GONE);
        toolbarTitle.setText(R.string.final_guide_title);
        toolbarTitle.setTextSize(22);
        toolbarTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        toolbarTitle.setTextColor(color(R.color.final_text_primary));
        toolbarTitle.setPadding(0, dp(10), 0, dp(10));
    }

    void disableActions() {
        busy = true;
        finishButton.setEnabled(false);
        for (int i = 0; i < timeline.getChildCount(); i++) {
            View row = timeline.getChildAt(i);
            row.findViewById(R.id.buttonFinalGuideOpenStep).setEnabled(false);
            row.findViewById(R.id.finalGuideTimelineHeader).setEnabled(false);
        }
    }

    void save(Bundle state) {
        state.putString(STATE_KEY, routeKey);
        state.putBoolean(STATE_DETAILS, details);
    }

    private int color(int resource) { return ContextCompat.getColor(context, resource); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
