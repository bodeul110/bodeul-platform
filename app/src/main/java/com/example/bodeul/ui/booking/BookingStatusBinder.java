package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.ui.common.AppointmentProgressOverviewModel;
import com.example.bodeul.ui.common.AppointmentProgressStageItemBinder;
import com.example.bodeul.ui.common.AppointmentProgressStageModel;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 예약 상세 화면 모델을 실제 뷰에 연결한다.
 */
public final class BookingStatusBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textMode;
    private final TextView textHeroBadge;
    private final TextView textHeroTitle;
    private final TextView textHeroBody;
    private final TextView textProgressTitle;
    private final TextView textProgressBody;
    private final LinearLayout progressStageContainer;
    private final View guideCard;
    private final TextView textGuideTitle;
    private final TextView textGuideBody;
    private final LinearLayout participantContainer;
    private final LinearLayout summaryContainer;
    private final View liveCard;
    private final LinearLayout liveContainer;
    private final View reportCard;
    private final LinearLayout reportContainer;
    private final MaterialButton buttonPrimary;
    private final MaterialButton buttonSecondary;
    private final AppointmentProgressStageItemBinder stageItemBinder;

    public BookingStatusBinder(
            Context context,
            LayoutInflater inflater,
            TextView textMode,
            TextView textHeroBadge,
            TextView textHeroTitle,
            TextView textHeroBody,
            TextView textProgressTitle,
            TextView textProgressBody,
            LinearLayout progressStageContainer,
            View guideCard,
            TextView textGuideTitle,
            TextView textGuideBody,
            LinearLayout participantContainer,
            LinearLayout summaryContainer,
            View liveCard,
            LinearLayout liveContainer,
            View reportCard,
            LinearLayout reportContainer,
            MaterialButton buttonPrimary,
            MaterialButton buttonSecondary
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textMode = textMode;
        this.textHeroBadge = textHeroBadge;
        this.textHeroTitle = textHeroTitle;
        this.textHeroBody = textHeroBody;
        this.textProgressTitle = textProgressTitle;
        this.textProgressBody = textProgressBody;
        this.progressStageContainer = progressStageContainer;
        this.guideCard = guideCard;
        this.textGuideTitle = textGuideTitle;
        this.textGuideBody = textGuideBody;
        this.participantContainer = participantContainer;
        this.summaryContainer = summaryContainer;
        this.liveCard = liveCard;
        this.liveContainer = liveContainer;
        this.reportCard = reportCard;
        this.reportContainer = reportContainer;
        this.buttonPrimary = buttonPrimary;
        this.buttonSecondary = buttonSecondary;
        this.stageItemBinder = new AppointmentProgressStageItemBinder(context);
    }

    public void bindScreen(BookingStatusScreenModel screenModel) {
        EnvironmentModeBadgeHelper.bind(textMode, screenModel.getModeText());
        bindHero(screenModel);
        textProgressTitle.setText(screenModel.getProgressTitleText());
        textProgressBody.setText(screenModel.getProgressBodyText());
        bindProgressOverview(screenModel.getProgressOverview());
        bindLines(participantContainer, screenModel.getParticipantLines());
        bindLines(summaryContainer, screenModel.getSummaryLines());
        bindLive(screenModel);
        bindReport(screenModel);
        bindActionButton(buttonPrimary, screenModel.getPrimaryAction());
        bindActionButton(buttonSecondary, screenModel.getSecondaryAction());
    }

    private void bindHero(BookingStatusScreenModel screenModel) {
        textHeroBadge.setText(screenModel.getHeroBadgeText());
        tintStatusBadge(textHeroBadge, screenModel.getStatus());
        textHeroTitle.setText(screenModel.getHeroTitleText());
        textHeroBody.setText(screenModel.getHeroBodyText());
    }

    private void bindProgressOverview(AppointmentProgressOverviewModel progressOverview) {
        bindStages(progressOverview.getStages());
        textGuideTitle.setText(progressOverview.getGuideTitleText());
        textGuideBody.setText(progressOverview.getGuideBodyText());
        guideCard.setVisibility(View.VISIBLE);
    }

    private void bindReport(BookingStatusScreenModel screenModel) {
        if (!screenModel.hasReportLines()) {
            reportCard.setVisibility(View.GONE);
            reportContainer.removeAllViews();
            return;
        }
        reportCard.setVisibility(View.VISIBLE);
        bindSections(reportContainer, screenModel.getReportSections());
    }

    private void bindLive(BookingStatusScreenModel screenModel) {
        if (!screenModel.hasLiveLines()) {
            liveCard.setVisibility(View.GONE);
            liveContainer.removeAllViews();
            return;
        }
        liveCard.setVisibility(View.VISIBLE);
        bindLines(liveContainer, screenModel.getLiveLines());
    }

    private void bindLines(LinearLayout container, List<BookingStatusLineItem> items) {
        container.removeAllViews();
        addLineViews(container, items);
    }

    private void addLineViews(LinearLayout container, List<BookingStatusLineItem> items) {
        for (int index = 0; index < items.size(); index++) {
            View itemView = inflater.inflate(R.layout.item_booking_status_line, container, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(10);
            }
            itemView.setLayoutParams(params);

            TextView labelView = itemView.findViewById(R.id.textBookingStatusLineLabel);
            TextView valueView = itemView.findViewById(R.id.textBookingStatusLineValue);
            BookingStatusLineItem item = items.get(index);
            labelView.setText(item.getLabelText());
            valueView.setText(item.getValueText());
            valueView.setTextColor(ContextCompat.getColor(
                    context,
                    item.isEmphasized() ? R.color.bodeul_text_primary : R.color.bodeul_text_secondary
            ));
            valueView.setTextSize(item.isEmphasized() ? 15f : 14f);
            container.addView(itemView);
        }
    }

    private void bindSections(LinearLayout container, List<BookingStatusSectionModel> sections) {
        container.removeAllViews();
        for (int index = 0; index < sections.size(); index++) {
            BookingStatusSectionModel section = sections.get(index);

            TextView titleView = new TextView(context);
            titleView.setText(section.getTitleText());
            titleView.setTextColor(ContextCompat.getColor(context, R.color.bodeul_text_primary));
            titleView.setTextSize(16f);
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);

            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (index > 0) {
                titleParams.topMargin = dp(18);
            }
            titleView.setLayoutParams(titleParams);
            container.addView(titleView);

            LinearLayout linesLayout = new LinearLayout(context);
            linesLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams linesParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            linesParams.topMargin = dp(12);
            linesLayout.setLayoutParams(linesParams);
            addLineViews(linesLayout, section.getLines());
            container.addView(linesLayout);
        }
    }

    private void bindStages(List<AppointmentProgressStageModel> stages) {
        progressStageContainer.removeAllViews();
        for (AppointmentProgressStageModel stage : stages) {
            View itemView = inflater.inflate(R.layout.item_appointment_progress_stage, progressStageContainer, false);
            stageItemBinder.bind(itemView, stage);
            progressStageContainer.addView(itemView);
        }
    }

    private void bindActionButton(MaterialButton button, BookingStatusActionModel actionModel) {
        if (actionModel == null) {
            button.setVisibility(View.GONE);
            return;
        }
        button.setVisibility(View.VISIBLE);
        button.setText(actionModel.getLabelText());
    }

    private void tintStatusBadge(TextView textView, AppointmentStatus status) {
        int backgroundColor;
        int textColor;
        switch (status) {
            case MATCHED:
                backgroundColor = R.color.bodeul_primary;
                textColor = R.color.white;
                break;
            case IN_PROGRESS:
            case COMPLETED:
                backgroundColor = R.color.bodeul_success;
                textColor = R.color.white;
                break;
            case CANCELED:
                backgroundColor = R.color.bodeul_surface_alt;
                textColor = R.color.bodeul_text_primary;
                break;
            case REQUESTED:
            default:
                backgroundColor = R.color.bodeul_warning;
                textColor = R.color.bodeul_text_primary;
                break;
        }
        textView.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColor)));
        textView.setTextColor(ContextCompat.getColor(context, textColor));
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
