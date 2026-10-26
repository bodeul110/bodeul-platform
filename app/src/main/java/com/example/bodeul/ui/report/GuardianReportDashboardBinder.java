package com.example.bodeul.ui.report;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.bodeul.R;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.example.bodeul.util.StatePanelHelper;

/**
 * 보호자 진행 화면 모델을 실제 뷰에 렌더링한다.
 */
public final class GuardianReportDashboardBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final GuardianReportEntryCardBinder entryCardBinder;
    private final TextView textMode;
    private final LinearLayout entryContainer;

    public GuardianReportDashboardBinder(
            Context context,
            LayoutInflater inflater,
            GuardianReportEntryCardBinder entryCardBinder,
            TextView textMode,
            LinearLayout entryContainer
    ) {
        this.context = context.getApplicationContext();
        this.inflater = inflater;
        this.entryCardBinder = entryCardBinder;
        this.textMode = textMode;
        this.entryContainer = entryContainer;
    }

    public void bindScreen(GuardianReportScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        bindEntries(screenModel);
    }

    private void bindEntries(GuardianReportScreenModel screenModel) {
        entryContainer.removeAllViews();
        if (!screenModel.hasEntries()) {
            View emptyPanel = inflater.inflate(R.layout.include_state_panel, entryContainer, false);
            StatePanelHelper.show(
                    emptyPanel,
                    StatePanelHelper.Tone.INFO,
                    context.getString(R.string.state_badge_notice),
                    context.getString(R.string.guardian_report_empty_title),
                    context.getString(R.string.guardian_report_list_empty),
                    null,
                    null,
                    null,
                    null
            );
            entryContainer.addView(emptyPanel);
            return;
        }

        for (GuardianReportEntryCardModel model : screenModel.getEntryCards()) {
            View itemView = inflater.inflate(R.layout.item_guardian_report, entryContainer, false);
            entryCardBinder.bind(itemView, model);
            entryContainer.addView(itemView);
        }
    }

}
