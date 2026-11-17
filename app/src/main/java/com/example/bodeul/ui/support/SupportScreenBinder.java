package com.example.bodeul.ui.support;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import com.example.bodeul.R;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 공통 화면의 목록·작성·상세 표시만 담당한다. */
final class SupportScreenBinder {
    private final Activity activity;
    private final SupportViewModel model;
    private SupportViewModel.Mode lastMode;
    private String lastTicket;
    private boolean binding;
    SupportScreenBinder(Activity activity, SupportViewModel model) { this.activity = activity; this.model = model; }
    boolean isBinding() { return binding; }

    void bind() {
        binding = true;
        SupportViewModel.Mode mode = model.mode();
        boolean ready = model.isReady();
        visible(R.id.supportScroll, ready);
        visible(R.id.supportHeaderTitle, mode == SupportViewModel.Mode.LIST);
        visible(R.id.supportList, mode == SupportViewModel.Mode.LIST);
        visible(R.id.supportForm, mode == SupportViewModel.Mode.COMPOSE);
        visible(R.id.supportDetail, mode == SupportViewModel.Mode.DETAIL);
        visible(R.id.supportProgress, model.isLoading() || model.isSubmitting());
        visible(R.id.supportSubmit, ready && mode != SupportViewModel.Mode.DETAIL);
        visible(R.id.supportStateScroll, model.gate() != SupportViewModel.Gate.NONE);
        visible(R.id.supportBookingContext, !model.appointmentId().isEmpty());
        activity.findViewById(R.id.supportFilterAll).setSelected(model.filter() == SupportViewModel.Filter.ALL);
        activity.findViewById(R.id.supportFilterWaiting).setSelected(model.filter() == SupportViewModel.Filter.WAITING);
        activity.findViewById(R.id.supportFilterAnswered).setSelected(model.filter() == SupportViewModel.Filter.ANSWERED);
        TextView category = activity.findViewById(R.id.supportCategoryText);
        category.setText(model.category().isEmpty() ? activity.getString(R.string.final_support_category_hint)
                : activity.getString(model.source.categoryLabel(model.category())));
        category.setTextColor(activity.getColor(model.category().isEmpty() ? R.color.final_text_hint : R.color.final_text_primary));
        setInput(R.id.supportTitle, model.title());
        setInput(R.id.supportBody, model.body());
        activity.findViewById(R.id.supportCategory).setEnabled(ready && !model.isSubmitting());
        activity.findViewById(R.id.supportAttachment).setEnabled(ready && !model.isSubmitting());
        bindButton();
        if (ready && mode == SupportViewModel.Mode.LIST) bindRows();
        SupportTicket ticket = model.selectedTicket();
        if (ready && mode == SupportViewModel.Mode.DETAIL && ticket != null) bindDetail(ticket);
        String ticketId = ticket == null ? "" : ticket.id;
        if (lastMode != mode || (mode == SupportViewModel.Mode.DETAIL && !ticketId.equals(lastTicket))) {
            ((NestedScrollView) activity.findViewById(R.id.supportScroll)).scrollTo(0, 0);
            lastMode = mode;
            lastTicket = ticketId;
        }
        binding = false;
    }

    void bindButton() {
        boolean enabled = model.isReady() && !model.isSubmitting()
                && (model.mode() == SupportViewModel.Mode.LIST || model.canSubmit());
        View button = activity.findViewById(R.id.supportSubmit);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .3f);
    }
    void clearPrivateViews() {
        binding = true;
        ((LinearLayout) activity.findViewById(R.id.supportRows)).removeAllViews();
        ((EditText) activity.findViewById(R.id.supportTitle)).setText("");
        ((EditText) activity.findViewById(R.id.supportBody)).setText("");
        for (int id : new int[]{R.id.supportDetailTitle, R.id.supportDetailBody, R.id.supportResponseBody, R.id.supportResponseAuthor}) text(id, "");
        visible(R.id.supportScroll, false);
        visible(R.id.supportSubmit, false);
        binding = false;
    }
    private void bindRows() {
        LinearLayout rows = activity.findViewById(R.id.supportRows);
        rows.removeAllViews();
        List<SupportTicket> values = model.visibleTickets();
        visible(R.id.supportEmpty, values.isEmpty());
        text(R.id.supportEmpty, activity.getString(model.filter() == SupportViewModel.Filter.ALL
                ? R.string.final_support_empty : R.string.final_support_filter_empty));
        for (SupportTicket ticket : values) {
            View row = activity.getLayoutInflater().inflate(R.layout.item_support_ticket, rows, false);
            row.setTag(ticket.id);
            ((TextView) row.findViewById(R.id.supportRowCategory)).setText(model.source.categoryLabel(ticket.category));
            ((TextView) row.findViewById(R.id.supportRowTitle)).setText(ticket.title);
            ((TextView) row.findViewById(R.id.supportRowDate)).setText(date(ticket.createdAt));
            TextView status = row.findViewById(R.id.supportRowStatus);
            status.setText(ticket.answered ? R.string.final_support_answered : R.string.final_support_waiting);
            status.setSelected(ticket.answered);
            row.setOnClickListener(view -> model.open(ticket.id));
            rows.addView(row);
        }
    }
    private void bindDetail(SupportTicket ticket) {
        text(R.id.supportDetailCategory, activity.getString(model.source.categoryLabel(ticket.category)));
        text(R.id.supportDetailTitle, ticket.title);
        text(R.id.supportDetailDate, date(ticket.createdAt));
        text(R.id.supportDetailBody, ticket.body);
        visible(R.id.supportPending, !ticket.answered);
        visible(R.id.supportResponse, ticket.answered);
        activity.findViewById(R.id.supportResponseBadge).setSelected(true);
        text(R.id.supportResponseBody, ticket.response.isEmpty() ? activity.getString(R.string.final_support_response_missing) : ticket.response);
        text(R.id.supportResponseDate, date(ticket.respondedAt));
        text(R.id.supportResponseAuthor, activity.getString(R.string.final_support_response_author, ticket.responder));
        visible(R.id.supportResponseAuthor, !ticket.responder.isEmpty());
        resizeDetail();
    }
    void resizeDetail() {
        View scroll = activity.findViewById(R.id.supportScroll);
        int minimum = Math.max(0, scroll.getHeight() - Math.round(310 * activity.getResources().getDisplayMetrics().density));
        View question = activity.findViewById(R.id.supportQuestion);
        if (question.getMinimumHeight() != minimum) question.setMinimumHeight(minimum);
    }
    private void setInput(int id, String value) {
        EditText input = activity.findViewById(id);
        if (!input.getText().toString().equals(value)) input.setText(value);
        input.setEnabled(model.isReady() && !model.isSubmitting());
    }
    private String date(long value) {
        return value <= 0 ? activity.getString(R.string.final_support_date_missing)
                : new SimpleDateFormat("yy.MM.dd HH:mm", Locale.KOREA).format(new Date(value));
    }
    private void text(int id, String text) { ((TextView) activity.findViewById(id)).setText(text); }
    private void visible(int id, boolean visible) { activity.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE); }
}
