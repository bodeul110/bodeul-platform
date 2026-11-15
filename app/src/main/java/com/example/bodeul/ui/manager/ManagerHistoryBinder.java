package com.example.bodeul.ui.manager;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

/**
 * 과거 동행 이력 화면 모델을 레이아웃에 바인딩한다.
 */
public final class ManagerHistoryBinder {
    public interface Listener {
        void onSelectFilter(ManagerHistoryFilter filter);
        void onOpenReport(String requestId);
    }

    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textSummary;
    private final TextView textListHelper;
    private final LinearLayout metricContainer;
    private final LinearLayout filterContainer;
    private final LinearLayout entryContainer;
    private final TextView textEmpty;
    private final ManagerHistoryEntryCardBinder entryCardBinder;
    private final ManagerHistoryMetricBinder metricBinder;

    public ManagerHistoryBinder(
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textSummary,
            TextView textListHelper,
            LinearLayout metricContainer,
            LinearLayout filterContainer,
            LinearLayout entryContainer,
            TextView textEmpty
    ) {
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textSummary = textSummary;
        this.textListHelper = textListHelper;
        this.metricContainer = metricContainer;
        this.filterContainer = filterContainer;
        this.entryContainer = entryContainer;
        this.textEmpty = textEmpty;
        this.entryCardBinder = new ManagerHistoryEntryCardBinder(inflater);
        this.metricBinder = new ManagerHistoryMetricBinder();
    }

    public void bindScreen(ManagerHistoryScreenModel screenModel, Listener listener) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
        textSummary.setText(screenModel.getSummaryText());
        textListHelper.setText(screenModel.getListHelperText());
        textEmpty.setText(screenModel.getEmptyText());

        bindMetrics(screenModel);
        bindFilters(screenModel, listener);

        entryContainer.removeAllViews();
        textEmpty.setVisibility(screenModel.getEntryCards().isEmpty() ? View.VISIBLE : View.GONE);
        for (ManagerHistoryEntryCardModel cardModel : screenModel.getEntryCards()) {
            View itemView = inflater.inflate(R.layout.item_manager_history_entry, entryContainer, false);
            entryCardBinder.bind(itemView, cardModel, listener);
            entryContainer.addView(itemView);
        }
    }

    private void bindMetrics(ManagerHistoryScreenModel screenModel) {
        metricContainer.removeAllViews();
        for (ManagerHistoryMetricModel metricModel : screenModel.getMetricCards()) {
            View itemView = inflater.inflate(R.layout.item_manager_history_metric, metricContainer, false);
            metricBinder.bind(itemView, metricModel);
            metricContainer.addView(itemView);
        }
    }

    private void bindFilters(ManagerHistoryScreenModel screenModel, Listener listener) {
        filterContainer.removeAllViews();
        for (ManagerHistoryFilterChipModel chipModel : screenModel.getFilterChips()) {
            MaterialButton button = new MaterialButton(
                    filterContainer.getContext(),
                    null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle
            );
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dpToPx(8));
            button.setLayoutParams(params);
            button.setAllCaps(false);
            button.setCornerRadius(dpToPx(18));
            button.setText(chipModel.getButtonText());
            bindFilterButtonStyle(button, chipModel.isSelected());
            button.setOnClickListener(view -> listener.onSelectFilter(chipModel.getFilter()));
            filterContainer.addView(button);
        }
    }

    private void bindFilterButtonStyle(MaterialButton button, boolean selected) {
        if (selected) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    button.getContext().getColor(R.color.bodeul_primary)
            ));
            button.setStrokeColor(ColorStateList.valueOf(
                    button.getContext().getColor(R.color.bodeul_primary)
            ));
            button.setTextColor(button.getContext().getColor(R.color.white));
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(
                button.getContext().getColor(R.color.white)
        ));
        button.setStrokeColor(ColorStateList.valueOf(
                button.getContext().getColor(R.color.bodeul_primary)
        ));
        button.setTextColor(button.getContext().getColor(R.color.bodeul_primary));
    }

    private int dpToPx(int value) {
        return Math.round(value * filterContainer.getResources().getDisplayMetrics().density);
    }
}
