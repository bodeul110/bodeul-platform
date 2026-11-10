package com.example.bodeul.ui.booking;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** 최신 이용내역 카드에 표시 모델과 동작을 연결한다. */
public final class ClientBookingHistoryBinder {
    private final Context context;
    private final LayoutInflater inflater;
    private final LinearLayout entryContainer;

    public ClientBookingHistoryBinder(
            Context context,
            LayoutInflater inflater,
            LinearLayout entryContainer
    ) {
        this.context = context;
        this.inflater = inflater;
        this.entryContainer = entryContainer;
    }

    public void bindScreen(
            ClientBookingHistoryScreenModel screenModel,
            EntryActionListener listener
    ) {
        bindEntries(screenModel.getEntries(), listener);
    }

    private void bindEntries(
            List<ClientBookingHistoryEntryModel> items,
            EntryActionListener listener
    ) {
        entryContainer.removeAllViews();
        for (ClientBookingHistoryEntryModel item : items) {
            View itemView = inflater.inflate(
                    R.layout.item_client_booking_history,
                    entryContainer,
                    false
            );
            bindEntryView(itemView, item, listener);
            entryContainer.addView(itemView);
        }
    }

    private void bindEntryView(
            View itemView,
            ClientBookingHistoryEntryModel model,
            EntryActionListener listener
    ) {
        TextView statusView = itemView.findViewById(R.id.textBookingRequestStatus);
        TextView titleView = itemView.findViewById(R.id.textBookingRequestTitle);
        TextView detailView = itemView.findViewById(R.id.textBookingRequestDetail);
        TextView placeView = itemView.findViewById(R.id.textBookingRequestPlace);
        TextView linkedView = itemView.findViewById(R.id.textBookingRequestLinked);
        View actionsView = itemView.findViewById(R.id.layoutBookingRequestActions);
        MaterialButton reviewButton = itemView.findViewById(R.id.buttonBookingRequestReview);
        MaterialButton reportButton = itemView.findViewById(R.id.buttonBookingRequestReport);

        statusView.setText(model.getStatusLabel());
        statusView.setTextColor(ContextCompat.getColor(
                context,
                model.getStatusTextColorResId()
        ));
        titleView.setText(model.getTitle());
        detailView.setText(model.getDetail());
        placeView.setText(model.getPlace());
        linkedView.setText(model.getLinked());

        actionsView.setVisibility(model.isCompleted() ? View.VISIBLE : View.GONE);
        reviewButton.setVisibility(model.isReviewAllowed() ? View.VISIBLE : View.GONE);
        reviewButton.setOnClickListener(view -> listener.onOpenReview(model.getRequestId()));
        reportButton.setOnClickListener(view -> listener.onOpenReport(model.getRequestId()));
        itemView.setOnClickListener(view -> listener.onOpenRequest(model.getRequestId()));
    }

    public interface EntryActionListener {
        void onOpenRequest(String requestId);
        void onOpenReview(String requestId);
        void onOpenReport(String requestId);
    }
}
