package com.example.bodeul.ui.manager;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** Figma Step 9의 지도 위계를 기존 카카오 약국 검색 액션에 연결한다. */
final class ManagerGuidePharmacyRouteBinder {
    private final View content;
    private final View toolbar;
    private final View defaultToolbar;
    private final LinearLayout scrollContent;
    private final View mode;
    private final View subtitle;
    private final View meetingOverview;
    private final View legacySummary;
    private final View legacyFocus;
    private final View actionsTitle;
    private final View actionsHelper;
    private final View mapCard;
    private final View mapHeader;
    private final LinearLayout mapActionContainer;
    private final View legacyLocationCard;
    private final View legacyNotesCard;
    private final View legacyReportCard;
    private final TextView hospital;
    private final TextView department;
    private final TextView actionHelp;
    private final MaterialButton advance;
    private MaterialButton pharmacyActionButton;

    ManagerGuidePharmacyRouteBinder(View root) {
        content = root.findViewById(R.id.managerGuidePharmacyRouteContent);
        toolbar = root.findViewById(R.id.guidePharmacyRouteToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        mapCard = root.findViewById(R.id.cardGuideMap);
        mapHeader = root.findViewById(R.id.layoutGuideMapHeader);
        mapActionContainer = root.findViewById(R.id.guideMapActionContainer);
        legacyLocationCard = root.findViewById(R.id.cardGuideLocationActions);
        legacyNotesCard = root.findViewById(R.id.cardGuideNotesActions);
        legacyReportCard = root.findViewById(R.id.cardGuideReportActions);
        hospital = root.findViewById(R.id.textGuidePharmacyHospital);
        department = root.findViewById(R.id.textGuidePharmacyDepartment);
        actionHelp = root.findViewById(R.id.textGuidePharmacyActionHelp);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
    }

    void bind(
            ManagerGuideScreenModel model,
            ManagerDashboard dashboard,
            boolean mutationInFlight
    ) {
        boolean pharmacyStep = "PHARMACY_ROUTE".equals(
                model.getPresentationStepCode());
        content.setVisibility(pharmacyStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(pharmacyStep ? View.VISIBLE : View.GONE);
        if (!pharmacyStep) {
            pharmacyActionButton = null;
            mapHeader.setVisibility(View.VISIBLE);
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        hideLegacySectionsExceptMap();
        bindDashboard(dashboard);
        bindPharmacyAction(model.getMapActions(), !mutationInFlight);
        advance.setIconResource(R.drawable.ic_figma_guide_arrow_vector);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        pharmacyActionButton = null;
        mapHeader.setVisibility(View.VISIBLE);
        defaultToolbar.setVisibility(View.VISIBLE);
        setScrollTopPadding(24);
    }

    void setActionEnabled(boolean enabled) {
        if (pharmacyActionButton != null) {
            pharmacyActionButton.setEnabled(enabled);
        }
    }

    private void bindDashboard(ManagerDashboard dashboard) {
        AppointmentRequest request = dashboard == null
                ? null : dashboard.getAppointmentRequest();
        hospital.setText(valueOrUnknown(request == null ? "" : request.getHospitalName()));
        String departmentName = valueOrUnknown(
                request == null ? "" : request.getDepartmentName());
        department.setText(department.getContext().getString(
                R.string.guide_pharmacy_department_label) + " · " + departmentName);
    }

    private void bindPharmacyAction(
            List<ManagerGuideMapActionModel> actions,
            boolean enabled
    ) {
        int childCount = mapActionContainer.getChildCount();
        boolean found = false;
        ManagerGuideMapActionModel pharmacyAction = null;
        pharmacyActionButton = null;
        for (int index = 0; index < childCount; index++) {
            View child = mapActionContainer.getChildAt(index);
            ManagerGuideMapActionModel action = index < actions.size()
                    ? actions.get(index) : null;
            boolean pharmacy = action != null && action.isKakaoPlaceSearch();
            child.setVisibility(pharmacy ? View.VISIBLE : View.GONE);
            if (pharmacy) {
                found = true;
                pharmacyAction = action;
                stylePrimaryMapAction(child, enabled);
            }
        }
        mapActionContainer.setVisibility(found ? View.VISIBLE : View.GONE);
        if (pharmacyAction == null) {
            actionHelp.setText(R.string.guide_pharmacy_action_unavailable);
        } else {
            actionHelp.setText(pharmacyAction.getBody());
        }
    }

    private void stylePrimaryMapAction(View actionView, boolean enabled) {
        MaterialButton button = actionView.findViewById(R.id.buttonGuideMapAction);
        pharmacyActionButton = button;
        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        button.setLayoutParams(params);
        button.setMinHeight(dp(54));
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                button.getContext(), R.color.figma_mvp_primary)));
        button.setTextColor(ContextCompat.getColor(button.getContext(), R.color.white));
        button.setStrokeWidth(0);
        button.setCornerRadius(dp(28));
        button.setIconResource(R.drawable.ic_figma_guide_arrow_vector);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        button.setIconPadding(dp(10));
        button.setIconTint(null);
        button.setEnabled(enabled);
    }

    private void hideLegacySectionsExceptMap() {
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        meetingOverview.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        legacyLocationCard.setVisibility(View.GONE);
        legacyNotesCard.setVisibility(View.GONE);
        legacyReportCard.setVisibility(View.GONE);
        mapCard.setVisibility(View.VISIBLE);
        mapHeader.setVisibility(View.GONE);
    }

    private String valueOrUnknown(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty()
                ? hospital.getContext().getString(R.string.guide_remaining_value_unknown)
                : normalized;
    }

    private int dp(int value) {
        return Math.round(value * content.getResources().getDisplayMetrics().density);
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = dp(topDp);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
