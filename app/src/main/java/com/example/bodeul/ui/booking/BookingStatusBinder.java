package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.ui.common.AppointmentProgressStageItemBinder;
import com.example.bodeul.ui.common.AppointmentProgressStageModel;
import com.example.bodeul.util.EnvironmentModeBadgeHelper;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** 예약 상세 표시와 펼침 상태만 관리하며 데이터 조회·변경은 Activity에 맡긴다. */
public final class BookingStatusBinder {
    private final Context context;
    private final View root;
    private final LayoutInflater inflater;
    private final AppointmentProgressStageItemBinder stageItemBinder;
    private final boolean largeText;
    private final int[][] disclosures = {
            {R.id.buttonBookingStatusManager, R.id.textBookingStatusManagerContact},
            {R.id.buttonBookingStatusNote, R.id.textBookingStatusNote},
            {R.id.buttonBookingStatusProgress, R.id.bookingStatusProgressDetails},
            {R.id.buttonBookingStatusReport, R.id.bookingStatusReportContainer}
    };

    public BookingStatusBinder(View root, @Nullable Bundle savedState) {
        this.root = root;
        context = root.getContext();
        inflater = LayoutInflater.from(context);
        stageItemBinder = new AppointmentProgressStageItemBinder(context);
        largeText = context.getResources().getConfiguration().fontScale >= 1.5f;
        for (int[] disclosure : disclosures) {
            MaterialButton button = root.findViewById(disclosure[0]);
            View content = root.findViewById(disclosure[1]);
            setExpanded(button, content, savedState != null
                    && savedState.getBoolean(stateKey(disclosure[0]), false));
            button.setOnClickListener(view -> setExpanded(button, content,
                    content.getVisibility() != View.VISIBLE));
        }
        for (int id : new int[]{R.id.textBookingStatusHeroBadge, R.id.textBookingStatusPatientTitle,
                R.id.textBookingStatusMeetingTitle, R.id.textBookingStatusPaymentTitle}) {
            ViewCompat.setAccessibilityHeading(root.findViewById(id), true);
        }
        if (largeText) {
            stack((LinearLayout) root.findViewById(R.id.bookingStatusHeading), 8);
            stack((LinearLayout) root.findViewById(R.id.bookingStatusHospital), 10);
            root.findViewById(R.id.imageBookingStatusHospital).setVisibility(View.GONE);
        }
    }

    public void saveState(Bundle outState) {
        for (int[] disclosure : disclosures) {
            outState.putBoolean(stateKey(disclosure[0]),
                    root.findViewById(disclosure[1]).getVisibility() == View.VISIBLE);
        }
    }

    public void collapseDetails() {
        for (int[] disclosure : disclosures) {
            setExpanded(root.findViewById(disclosure[0]), root.findViewById(disclosure[1]), false);
        }
    }

    public void bindScreen(BookingStatusScreenModel model) {
        EnvironmentModeBadgeHelper.bind(root.findViewById(R.id.textBookingStatusMode), model.getModeText());
        text(R.id.textBookingStatusHeroBadge, model.getHeroBadgeText());
        ((TextView) root.findViewById(R.id.textBookingStatusHeroBadge)).setTextColor(color(
                model.isAwaitingDeposit() ? R.color.final_text_error
                        : model.getStatus() == AppointmentStatus.IN_PROGRESS ? R.color.final_text_accent_accessible
                        : model.getStatus() == AppointmentStatus.MATCHED || model.getStatus() == AppointmentStatus.CANCELED
                        ? R.color.final_text_secondary : R.color.final_text_primary));
        text(R.id.textBookingStatusReference, model.getReferenceText());
        visible(R.id.textBookingStatusReference, !model.getReferenceText().isEmpty());
        text(R.id.textBookingStatusHeroTitle, model.getHeroTitleText().isEmpty()
                ? context.getString(R.string.booking_completion_hospital_pending) : model.getHeroTitleText());
        text(R.id.textBookingStatusHeroBody, model.getHeroBodyText().isEmpty()
                ? context.getString(R.string.booking_completion_department_pending) : model.getHeroBodyText());
        visible(R.id.buttonBookingStatusMap, !model.getHeroTitleText().isEmpty());
        root.findViewById(R.id.buttonBookingStatusMap).setContentDescription(context.getString(
                R.string.final_hospital_map_description, model.getHeroTitleText()));
        visible(R.id.bookingStatusManager, !model.getManagerName().isEmpty());
        text(R.id.buttonBookingStatusManager, model.getManagerName());
        text(R.id.textBookingStatusManagerContact, model.getManagerContact());
        text(R.id.textBookingStatusNote, model.getManagerNote());
        bindLines(root.findViewById(R.id.bookingStatusParticipantContainer), model.getParticipantLines());
        bindLines(root.findViewById(R.id.bookingStatusSummaryContainer), model.getSummaryLines());
        bindLines(root.findViewById(R.id.bookingStatusPaymentContainer), model.getPaymentLines());
        LinearLayout information = root.findViewById(R.id.bookingStatusInformation);
        View payment = root.findViewById(R.id.bookingStatusPaymentSection);
        information.removeView(payment);
        int paymentIndex = model.isAwaitingDeposit()
                ? information.indexOfChild(root.findViewById(R.id.bookingStatusPatientSection))
                : information.indexOfChild(root.findViewById(R.id.bookingStatusMeetingSection)) + 1;
        information.addView(payment, paymentIndex);
        text(R.id.textBookingStatusProgressTitle, model.getProgressTitleText());
        text(R.id.textBookingStatusProgressBody, model.getProgressBodyText());
        text(R.id.textBookingStatusGuideTitle, model.getProgressOverview().getGuideTitleText());
        text(R.id.textBookingStatusGuideBody, model.getProgressOverview().getGuideBodyText());
        LinearLayout stages = root.findViewById(R.id.bookingStatusStageContainer);
        stages.removeAllViews();
        for (AppointmentProgressStageModel stage : model.getProgressOverview().getStages()) {
            View item = inflater.inflate(R.layout.item_appointment_progress_stage, stages, false);
            stageItemBinder.bind(item, stage);
            stages.addView(item);
        }
        visible(R.id.cardBookingStatusLive, model.hasLiveLines());
        bindLines(root.findViewById(R.id.bookingStatusLiveContainer), model.getLiveLines());
        visible(R.id.cardBookingStatusReport, model.hasReportLines());
        bindSections(root.findViewById(R.id.bookingStatusReportContainer), model.getReportSections());
        boolean edit = model.getPrimaryAction() != null
                && model.getPrimaryAction().getActionType() == BookingStatusActionType.EDIT;
        visible(R.id.buttonBookingStatusEdit, edit);
        bindAction(R.id.buttonBookingStatusPrimary, edit ? null : model.getPrimaryAction());
        bindAction(R.id.buttonBookingStatusSecondary, model.getSecondaryAction());
        MaterialButton secondary = root.findViewById(R.id.buttonBookingStatusSecondary);
        boolean cancel = model.getSecondaryAction() != null
                && model.getSecondaryAction().getActionType() == BookingStatusActionType.CANCEL;
        secondary.setBackgroundTintList(ColorStateList.valueOf(color(cancel ? R.color.white : R.color.final_input_surface)));
        secondary.setTextColor(color(cancel ? R.color.final_text_error : R.color.final_text_primary));
        secondary.setPaintFlags(cancel ? secondary.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                : secondary.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
    }

    private void bindLines(LinearLayout container, List<BookingStatusLineItem> items) {
        container.removeAllViews();
        for (int i = 0; i < items.size(); i++) {
            LinearLayout row = (LinearLayout) inflater.inflate(R.layout.item_final_booking_status_line, container, false);
            if (i > 0) ((ViewGroup.MarginLayoutParams) row.getLayoutParams()).topMargin = dp(15);
            TextView label = row.findViewById(R.id.textBookingStatusLineLabel);
            label.setText(items.get(i).getLabelText());
            ((TextView) row.findViewById(R.id.textBookingStatusLineValue)).setText(items.get(i).getValueText());
            if (largeText) {
                stack(row, 5);
            } else {
                // 글자 확대 시 짧은 항목명이 한 글자씩 끊기지 않게 칸도 함께 넓힌다.
                label.getLayoutParams().width = Math.round(dp(70)
                        * Math.max(1f, context.getResources().getConfiguration().fontScale));
            }
            container.addView(row);
        }
    }

    private void bindSections(LinearLayout container, List<BookingStatusSectionModel> sections) {
        container.removeAllViews();
        for (BookingStatusSectionModel section : sections) {
            View item = inflater.inflate(R.layout.item_final_booking_status_section, container, false);
            TextView title = item.findViewById(R.id.textBookingStatusSectionTitle);
            title.setText(section.getTitleText());
            ViewCompat.setAccessibilityHeading(title, true);
            bindLines(item.findViewById(R.id.bookingStatusSectionLines), section.getLines());
            container.addView(item);
        }
    }

    private void bindAction(int id, @Nullable BookingStatusActionModel action) {
        visible(id, action != null);
        if (action != null) text(id, action.getLabelText());
    }

    private void setExpanded(MaterialButton button, View content, boolean expanded) {
        content.setVisibility(expanded ? View.VISIBLE : View.GONE);
        button.setIconResource(expanded ? R.drawable.bodeul_final_booking_dropdown_up
                : R.drawable.bodeul_final_booking_dropdown_down);
        ViewCompat.setStateDescription(button, context.getString(expanded
                ? R.string.final_booking_status_collapse : R.string.final_booking_status_expand));
    }

    private String stateKey(int buttonId) { return "bookingStatus.expanded." + buttonId; }
    private void text(int id, String text) { ((TextView) root.findViewById(id)).setText(text); }
    private void visible(int id, boolean visible) { root.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
    private int color(int id) { return ContextCompat.getColor(context, id); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private void stack(LinearLayout layout, int gap) {
        layout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.weight = 0;
            params.setMarginStart(0);
            params.topMargin = i == 0 ? 0 : dp(gap);
            child.setLayoutParams(params);
        }
    }
}
