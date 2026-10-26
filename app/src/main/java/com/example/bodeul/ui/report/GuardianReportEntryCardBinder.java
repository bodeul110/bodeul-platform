package com.example.bodeul.ui.report;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

/**
 * 보호자 요청별 진행 카드를 뷰에 바인딩한다.
 */
public final class GuardianReportEntryCardBinder {
    public interface Listener {
        void onOpenRequestDetail(String requestId);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final Listener listener;

    public GuardianReportEntryCardBinder(Context context, LayoutInflater inflater, Listener listener) {
        this.context = context.getApplicationContext();
        this.inflater = inflater;
        this.listener = listener;
    }

    public void bind(View cardView, GuardianReportEntryCardModel model) {
        MaterialCardView rootCard = (MaterialCardView) cardView;
        View finalReportGroup = cardView.findViewById(R.id.guardianFinalReportGroup);
        View progressGroup = cardView.findViewById(R.id.guardianReportProgressGroup);
        TextView textFinalDate = cardView.findViewById(R.id.textGuardianFinalReportDate);
        View cardVisitSummary = cardView.findViewById(R.id.cardGuardianFinalReportVisitSummary);
        View cardHospital = cardView.findViewById(R.id.cardGuardianFinalReportHospital);
        TextView textHospital = cardView.findViewById(R.id.textGuardianFinalReportHospital);
        View cardDepartment = cardView.findViewById(R.id.cardGuardianFinalReportDepartment);
        TextView textDepartment = cardView.findViewById(R.id.textGuardianFinalReportDepartment);
        View cardCondition = cardView.findViewById(R.id.cardGuardianFinalReportCondition);
        TextView textCondition = cardView.findViewById(R.id.textGuardianFinalReportCondition);
        TextView textTitle = cardView.findViewById(R.id.textGuardianReportEntryTitle);
        TextView textStatus = cardView.findViewById(R.id.textGuardianReportEntryStatus);
        TextView textHeroBody = cardView.findViewById(R.id.textGuardianReportEntryHeroBody);
        TextView textLiveTitle = cardView.findViewById(R.id.textGuardianReportEntryLiveTitle);
        LinearLayout liveContainer = cardView.findViewById(R.id.guardianReportEntryLiveContainer);
        TextView textHistoryTitle = cardView.findViewById(R.id.textGuardianReportEntryHistoryTitle);
        LinearLayout historyContainer = cardView.findViewById(R.id.guardianReportEntryHistoryContainer);
        TextView textMemoTitle = cardView.findViewById(R.id.textGuardianReportEntryMemoTitle);
        LinearLayout memoContainer = cardView.findViewById(R.id.guardianReportEntryMemoContainer);
        TextView textReportTitle = cardView.findViewById(R.id.textGuardianReportEntryReportTitle);
        LinearLayout reportContainer = cardView.findViewById(R.id.guardianReportEntryReportContainer);
        TextView textPending = cardView.findViewById(R.id.textGuardianReportEntryPending);
        View cardManager = cardView.findViewById(R.id.cardGuardianFinalReportManager);
        TextView textManagerName = cardView.findViewById(R.id.textGuardianFinalReportManagerName);
        TextView textManagerMessageLabel = cardView.findViewById(
                R.id.textGuardianFinalReportManagerMessageLabel
        );
        TextView textManagerMessage = cardView.findViewById(R.id.textGuardianFinalReportManagerMessage);
        MaterialButton buttonAction = cardView.findViewById(R.id.buttonGuardianReportEntryAction);

        boolean finalReportReady = model.isFinalReportReady();
        finalReportGroup.setVisibility(finalReportReady ? View.VISIBLE : View.GONE);
        progressGroup.setVisibility(finalReportReady ? View.GONE : View.VISIBLE);

        if (finalReportReady) {
            bindOptionalText(textFinalDate, model.getAppointmentDateText());
            bindOptionalCard(cardHospital, textHospital, model.getHospitalNameText());
            bindOptionalCard(cardDepartment, textDepartment, model.getDepartmentNameText());
            bindOptionalCard(cardCondition, textCondition, model.getPatientConditionText());
            cardVisitSummary.setVisibility(
                    hasText(model.getHospitalNameText())
                            || hasText(model.getDepartmentNameText())
                            || hasText(model.getPatientConditionText())
                            ? View.VISIBLE
                            : View.GONE
            );
        } else {
            textTitle.setText(model.getTitleText());
            textStatus.setText(toStatusLabel(model.getStatus()));
            tintStatusBadge(textStatus, model.getStatus());
            textHeroBody.setText(model.getHeroBodyText());
            textLiveTitle.setText(model.getLiveSectionTitleText());
            bindLines(liveContainer, model.getLiveLines());
            bindOptionalLines(
                    textHistoryTitle,
                    historyContainer,
                    model.getHistorySectionTitleText(),
                    model.getHistoryLines()
            );
            bindOptionalLines(
                    textMemoTitle,
                    memoContainer,
                    model.getMemoSectionTitleText(),
                    model.getMemoLines()
            );
        }

        textReportTitle.setText(finalReportReady
                ? context.getString(R.string.guardian_final_report_treatment_title)
                : model.getReportSectionTitleText());
        bindSections(reportContainer, model.getReportSections());

        if (model.getPendingReportText() == null) {
            textPending.setVisibility(View.GONE);
        } else {
            textPending.setVisibility(View.VISIBLE);
            textPending.setText(model.getPendingReportText());
        }

        boolean showManagerMessage = finalReportReady && model.getManagerMessageText() != null;
        cardManager.setVisibility(showManagerMessage ? View.VISIBLE : View.GONE);
        if (showManagerMessage) {
            bindOptionalText(textManagerName, model.getManagerNameText());
            textManagerMessageLabel.setText(model.getManagerMessageLabelText());
            textManagerMessage.setText(model.getManagerMessageText());
        }

        buttonAction.setText(model.getActionLabelText());
        if (model.getRequestId() == null) {
            buttonAction.setVisibility(View.GONE);
        } else {
            buttonAction.setVisibility(View.VISIBLE);
            buttonAction.setOnClickListener(view -> listener.onOpenRequestDetail(model.getRequestId()));
        }
        rootCard.setOnClickListener(null);
        rootCard.setClickable(false);
    }

    private void bindLines(LinearLayout container, List<GuardianReportLineItem> items) {
        container.removeAllViews();
        addLineViews(container, items, R.layout.item_guardian_report_line);
    }

    private void bindOptionalLines(
            TextView titleView,
            LinearLayout container,
            String title,
            List<GuardianReportLineItem> items
    ) {
        if (items.isEmpty()) {
            titleView.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            container.removeAllViews();
            return;
        }
        titleView.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
        titleView.setText(title);
        bindLines(container, items);
    }

    private void bindOptionalText(TextView textView, String value) {
        boolean visible = hasText(value);
        textView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            textView.setText(value);
        }
    }

    private void bindOptionalCard(View card, TextView textView, String value) {
        boolean visible = hasText(value);
        card.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            textView.setText(value);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void addLineViews(
            LinearLayout container,
            List<GuardianReportLineItem> items,
            int layoutResId
    ) {
        for (int index = 0; index < items.size(); index++) {
            View itemView = inflater.inflate(layoutResId, container, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(10);
            }
            itemView.setLayoutParams(params);

            TextView labelView = itemView.findViewById(R.id.textGuardianReportLineLabel);
            TextView valueView = itemView.findViewById(R.id.textGuardianReportLineValue);
            GuardianReportLineItem item = items.get(index);
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

    private void bindSections(LinearLayout container, List<GuardianReportSectionModel> sections) {
        container.removeAllViews();
        for (int index = 0; index < sections.size(); index++) {
            GuardianReportSectionModel section = sections.get(index);
            View sectionView = inflater.inflate(
                    R.layout.item_guardian_final_report_section,
                    container,
                    false
            );
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) sectionView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(12);
            }
            sectionView.setLayoutParams(params);

            View accent = sectionView.findViewById(R.id.viewGuardianFinalReportSectionAccent);
            ImageView icon = sectionView.findViewById(R.id.imageGuardianFinalReportSectionIcon);
            TextView title = sectionView.findViewById(R.id.textGuardianFinalReportSectionTitle);
            LinearLayout lines = sectionView.findViewById(R.id.guardianFinalReportSectionLines);
            accent.setBackgroundColor(ContextCompat.getColor(
                    context,
                    sectionAccentColor(section.getStyle())
            ));
            icon.setImageResource(sectionIcon(section.getStyle()));
            title.setText(section.getTitleText());
            addLineViews(lines, section.getLines(), R.layout.item_guardian_final_report_line);
            container.addView(sectionView);
        }
    }

    private int sectionAccentColor(GuardianReportSectionModel.Style style) {
        switch (style) {
            case APPOINTMENT:
                return R.color.guardian_final_report_section_purple;
            case MEDICATION:
                return R.color.guardian_final_report_section_orange;
            case CLINICAL:
            default:
                return R.color.guardian_final_report_section_blue;
        }
    }

    private int sectionIcon(GuardianReportSectionModel.Style style) {
        switch (style) {
            case APPOINTMENT:
                return R.drawable.ic_figma_medication_calendar;
            case MEDICATION:
                return R.drawable.ic_figma_prescription_pharmacy;
            case CLINICAL:
            default:
                return R.drawable.ic_figma_prescription_document;
        }
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

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
