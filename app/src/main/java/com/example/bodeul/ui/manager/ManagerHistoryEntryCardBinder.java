package com.example.bodeul.ui.manager;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;

/**
 * 과거 동행 이력 카드 하나를 뷰에 바인딩한다.
 */
public final class ManagerHistoryEntryCardBinder {
    private final LayoutInflater inflater;

    public ManagerHistoryEntryCardBinder(LayoutInflater inflater) {
        this.inflater = inflater;
    }

    public void bind(View itemView, ManagerHistoryEntryCardModel model, ManagerHistoryBinder.Listener listener) {
        View reportButton = itemView.findViewById(R.id.buttonManagerHistoryFinalReport);
        reportButton.setVisibility(model.getReportRequestId() == null ? View.GONE : View.VISIBLE);
        reportButton.setOnClickListener(model.getReportRequestId() == null ? null
                : view -> listener.onOpenReport(model.getReportRequestId()));
        TextView statusBadgeView = itemView.findViewById(R.id.textManagerHistoryEntryBadge);
        TextView followUpBadgeView = itemView.findViewById(R.id.textManagerHistoryEntryFollowUpBadge);
        TextView titleView = itemView.findViewById(R.id.textManagerHistoryEntryTitle);
        TextView subtitleView = itemView.findViewById(R.id.textManagerHistoryEntrySubtitle);
        TextView summaryView = itemView.findViewById(R.id.textManagerHistoryEntrySummary);
        TextView activityView = itemView.findViewById(R.id.textManagerHistoryEntryActivity);
        LinearLayout lineContainer = itemView.findViewById(R.id.managerHistoryEntryLineContainer);

        bindBadge(itemView, statusBadgeView, model.getStatusBadge());
        if (model.getFollowUpBadge() == null) {
            followUpBadgeView.setVisibility(View.GONE);
        } else {
            followUpBadgeView.setVisibility(View.VISIBLE);
            bindBadge(itemView, followUpBadgeView, model.getFollowUpBadge());
        }
        titleView.setText(model.getTitleText());
        subtitleView.setText(model.getSubtitleText());
        summaryView.setText(model.getSummaryText());
        if (model.getActivityText().isEmpty()) {
            activityView.setVisibility(View.GONE);
        } else {
            activityView.setVisibility(View.VISIBLE);
            activityView.setText(model.getActivityText());
        }

        lineContainer.removeAllViews();
        for (ManagerInfoLineItem lineItem : model.getDetailLines()) {
            View lineView = inflater.inflate(R.layout.item_manager_info_line, lineContainer, false);
            TextView labelView = lineView.findViewById(R.id.textManagerInfoLineLabel);
            TextView valueView = lineView.findViewById(R.id.textManagerInfoLineValue);
            labelView.setText(lineItem.getLabelText());
            valueView.setText(lineItem.getValueText());
            valueView.setTypeface(valueView.getTypeface(), lineItem.isEmphasized() ? Typeface.BOLD : Typeface.NORMAL);
            valueView.setTextSize(lineItem.isEmphasized() ? 16f : 14f);
            lineContainer.addView(lineView);
        }
    }

    private void bindBadge(View itemView, TextView badgeView, ManagerHistoryBadgeModel badgeModel) {
        badgeView.setText(badgeModel.getText());
        switch (badgeModel.getTone()) {
            case WARNING:
                badgeView.setBackgroundResource(R.drawable.bg_badge_yellow);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_text_primary));
                return;
            case SUCCESS:
                badgeView.setBackgroundResource(R.drawable.bg_badge_green);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_success));
                return;
            case PURPLE:
                badgeView.setBackgroundResource(R.drawable.bg_badge_purple);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary));
                return;
            case PRIMARY:
            default:
                badgeView.setBackgroundResource(R.drawable.bg_badge_blue);
                badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.bodeul_primary));
        }
    }
}
