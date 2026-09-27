package com.example.bodeul.ui.manager;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.User;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** Figma Step 12의 최종 확인 위계를 기존 종료 액션과 메모 모아보기에 연결한다. */
final class ManagerGuideCareCompletionBinder {
    private final LayoutInflater inflater;
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
    private final View legacyMap;
    private final View legacyLocationCard;
    private final View legacyNotesCard;
    private final View legacyReportCard;
    private final TextView patient;
    private final TextView hospital;
    private final LinearLayout memoContainer;
    private final TextView memoEmpty;
    private final MaterialButton advance;

    ManagerGuideCareCompletionBinder(LayoutInflater inflater, View root) {
        this.inflater = inflater;
        content = root.findViewById(R.id.managerGuideCareCompletionContent);
        toolbar = root.findViewById(R.id.guideCareCompletionToolbar);
        defaultToolbar = root.findViewById(R.id.guideDefaultToolbar);
        scrollContent = root.findViewById(R.id.guideScrollContent);
        mode = root.findViewById(R.id.textGuideMode);
        subtitle = root.findViewById(R.id.textGuideSubtitle);
        meetingOverview = root.findViewById(R.id.managerGuideMeetingOverview);
        legacySummary = root.findViewById(R.id.cardGuideLegacySummary);
        legacyFocus = root.findViewById(R.id.cardGuideLegacyFocus);
        actionsTitle = root.findViewById(R.id.textGuideActionsTitle);
        actionsHelper = root.findViewById(R.id.textGuideActionsHelper);
        legacyMap = root.findViewById(R.id.cardGuideMap);
        legacyLocationCard = root.findViewById(R.id.cardGuideLocationActions);
        legacyNotesCard = root.findViewById(R.id.cardGuideNotesActions);
        legacyReportCard = root.findViewById(R.id.cardGuideReportActions);
        patient = root.findViewById(R.id.textGuideCompletionPatient);
        hospital = root.findViewById(R.id.textGuideCompletionHospital);
        memoContainer = root.findViewById(R.id.guideCompletionMemoContainer);
        memoEmpty = root.findViewById(R.id.textGuideCompletionMemoEmpty);
        advance = root.findViewById(R.id.buttonAdvanceGuide);
    }

    void bind(ManagerGuideScreenModel model, ManagerDashboard dashboard) {
        boolean completionStep = "CARE_COMPLETION".equals(model.getCurrentStepCode());
        content.setVisibility(completionStep ? View.VISIBLE : View.GONE);
        toolbar.setVisibility(completionStep ? View.VISIBLE : View.GONE);
        if (!completionStep) {
            memoContainer.removeAllViews();
            return;
        }

        defaultToolbar.setVisibility(View.GONE);
        setScrollTopPadding(8);
        hideLegacySections();
        bindDashboard(dashboard);
        bindMemos(model.getMemoSummaryItems());
        advance.setIconResource(R.drawable.ic_figma_medication_check);
        advance.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
    }

    void hideForState() {
        content.setVisibility(View.GONE);
        toolbar.setVisibility(View.GONE);
        defaultToolbar.setVisibility(View.VISIBLE);
        memoContainer.removeAllViews();
        setScrollTopPadding(24);
    }

    private void bindDashboard(ManagerDashboard dashboard) {
        User patientUser = dashboard == null ? null : dashboard.getPatient();
        AppointmentRequest request = dashboard == null
                ? null : dashboard.getAppointmentRequest();
        patient.setText(valueOrUnknown(patientUser == null ? "" : patientUser.getName()));
        String hospitalName = valueOrUnknown(request == null ? "" : request.getHospitalName());
        String departmentName = valueOrUnknown(
                request == null ? "" : request.getDepartmentName());
        hospital.setText(hospitalName + " · " + departmentName);
    }

    private void bindMemos(List<ManagerGuideMemoItem> items) {
        memoContainer.removeAllViews();
        boolean empty = items == null || items.isEmpty();
        memoEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (ManagerGuideMemoItem item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_manager_guide_memo_summary,
                    memoContainer,
                    false);
            TextView title = itemView.findViewById(R.id.textGuideMemoItemTitle);
            TextView body = itemView.findViewById(R.id.textGuideMemoItemBody);
            title.setText(item.getTitle());
            body.setText(item.getBody());
            body.setTextColor(ContextCompat.getColor(
                    itemView.getContext(),
                    item.isEmpty()
                            ? R.color.figma_mvp_text_tertiary
                            : R.color.figma_mvp_text_primary));
            memoContainer.addView(itemView);
        }
    }

    private void hideLegacySections() {
        mode.setVisibility(View.GONE);
        subtitle.setVisibility(View.GONE);
        meetingOverview.setVisibility(View.GONE);
        legacySummary.setVisibility(View.GONE);
        legacyFocus.setVisibility(View.GONE);
        actionsTitle.setVisibility(View.GONE);
        actionsHelper.setVisibility(View.GONE);
        legacyMap.setVisibility(View.GONE);
        legacyLocationCard.setVisibility(View.GONE);
        legacyNotesCard.setVisibility(View.GONE);
        legacyReportCard.setVisibility(View.GONE);
    }

    private String valueOrUnknown(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty()
                ? patient.getContext().getString(R.string.guide_remaining_value_unknown)
                : normalized;
    }

    private void setScrollTopPadding(int topDp) {
        int topPx = Math.round(topDp * scrollContent.getResources().getDisplayMetrics().density);
        scrollContent.setPadding(
                scrollContent.getPaddingLeft(), topPx,
                scrollContent.getPaddingRight(), scrollContent.getPaddingBottom());
    }
}
