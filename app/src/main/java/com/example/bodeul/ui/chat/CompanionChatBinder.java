package com.example.bodeul.ui.chat;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.example.bodeul.R;
import com.example.bodeul.domain.model.CompanionChatAttachment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public final class CompanionChatBinder {
    public interface PendingAttachmentThumbnailBinder {
        void onBindPendingAttachmentThumbnail(
                ImageView imageView,
                CompanionChatPendingAttachment pendingAttachment
        );
    }

    public interface MessageAttachmentThumbnailBinder {
        void onBindMessageAttachmentThumbnail(
                ImageView imageView,
                CompanionChatAttachment attachment
        );
    }

    public interface PendingAttachmentActionListener {
        void onPreviewPendingAttachment(CompanionChatPendingAttachment pendingAttachment);

        void onRemovePendingAttachment(CompanionChatPendingAttachment pendingAttachment);
    }

    public interface MessageAttachmentActionListener {
        void onOpenAttachment(CompanionChatAttachment attachment);
    }

    private final Context context;
    private final LayoutInflater inflater;
    private final TextView textTitle;
    private final TextView textHeroBadge;
    private final TextView avatar;
    private final TextView textEmptyBody;
    private final LinearLayout messageContainer;
    private final View pendingAttachmentContainer;
    private final LinearLayout pendingAttachmentItemsContainer;
    private final PendingAttachmentThumbnailBinder pendingAttachmentThumbnailBinder;
    private final MessageAttachmentThumbnailBinder messageAttachmentThumbnailBinder;
    private final MessageAttachmentActionListener messageAttachmentActionListener;

    public CompanionChatBinder(
            Context context,
            LayoutInflater inflater,
            View root,
            PendingAttachmentThumbnailBinder pendingAttachmentThumbnailBinder,
            MessageAttachmentThumbnailBinder messageAttachmentThumbnailBinder,
            MessageAttachmentActionListener messageAttachmentActionListener
    ) {
        this.context = context;
        this.inflater = inflater;
        this.textTitle = root.findViewById(R.id.textCompanionChatTitle);
        this.textHeroBadge = root.findViewById(R.id.textCompanionChatHeroBadge);
        this.avatar = root.findViewById(R.id.textCompanionChatAvatar);
        this.textEmptyBody = root.findViewById(R.id.textCompanionChatEmptyBody);
        this.messageContainer = root.findViewById(R.id.layoutCompanionChatMessages);
        this.pendingAttachmentContainer = root.findViewById(R.id.layoutCompanionChatPendingAttachment);
        this.pendingAttachmentItemsContainer = root.findViewById(R.id.layoutCompanionChatPendingAttachmentItems);
        this.pendingAttachmentThumbnailBinder = pendingAttachmentThumbnailBinder;
        this.messageAttachmentThumbnailBinder = messageAttachmentThumbnailBinder;
        this.messageAttachmentActionListener = messageAttachmentActionListener;
    }

    public void bindScreen(CompanionChatScreenModel screenModel) {
        textTitle.setText(screenModel.getTitle());
        textHeroBadge.setText(screenModel.getHeroBadge());
        textHeroBadge.setVisibility(View.VISIBLE);
        avatar.setText(initial(screenModel.getTitle()));
        avatar.setVisibility(View.VISIBLE);
        textEmptyBody.setText(screenModel.getEmptyBody());
        bindMessages(screenModel.getMessages());
    }

    public void clearScreen() {
        textTitle.setText(R.string.companion_chat_title);
        textHeroBadge.setText("");
        textHeroBadge.setVisibility(View.GONE);
        avatar.setText("");
        avatar.setVisibility(View.GONE);
        messageContainer.removeAllViews();
    }

    private String initial(String label) {
        return label == null || label.isEmpty() ? "" : label.substring(0, label.offsetByCodePoints(0, 1));
    }

    public void bindPendingAttachments(
            List<CompanionChatPendingAttachment> pendingAttachments,
            boolean actionsEnabled,
            PendingAttachmentActionListener actionListener
    ) {
        pendingAttachmentItemsContainer.removeAllViews();
        if (pendingAttachments == null || pendingAttachments.isEmpty()) {
            pendingAttachmentContainer.setVisibility(View.GONE);
            return;
        }

        pendingAttachmentContainer.setVisibility(View.VISIBLE);
        for (int index = 0; index < pendingAttachments.size(); index++) {
            CompanionChatPendingAttachment item = pendingAttachments.get(index);
            View itemView = inflater.inflate(
                    R.layout.item_companion_chat_pending_attachment,
                    pendingAttachmentItemsContainer,
                    false
            );
            ImageView imagePreview = itemView.findViewById(R.id.imageCompanionChatPendingAttachmentPreview);
            TextView textTitle = itemView.findViewById(R.id.textCompanionChatPendingAttachmentTitle);
            TextView textMeta = itemView.findViewById(R.id.textCompanionChatPendingAttachmentMeta);
            View buttonPreview = itemView.findViewById(R.id.buttonCompanionChatAttachmentPreview);
            View buttonClear = itemView.findViewById(R.id.buttonCompanionChatAttachmentClear);

            if (index == pendingAttachments.size() - 1) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
                params.bottomMargin = 0;
                itemView.setLayoutParams(params);
            }

            textTitle.setText(item.getFileName());
            textMeta.setText(resolvePendingAttachmentMeta(item));
            if (item.isImageType()) {
                imagePreview.setVisibility(View.VISIBLE);
                pendingAttachmentThumbnailBinder.onBindPendingAttachmentThumbnail(imagePreview, item);
            } else {
                imagePreview.setVisibility(View.GONE);
                imagePreview.setImageDrawable(null);
                imagePreview.setTag(null);
            }
            buttonPreview.setOnClickListener(view -> actionListener.onPreviewPendingAttachment(item));
            buttonClear.setOnClickListener(view -> actionListener.onRemovePendingAttachment(item));
            buttonPreview.setEnabled(actionsEnabled);
            buttonClear.setEnabled(actionsEnabled);
            pendingAttachmentItemsContainer.addView(itemView);
        }
    }

    private void bindMessages(List<CompanionChatMessageItemModel> items) {
        messageContainer.removeAllViews();
        textEmptyBody.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (int index = 0; index < items.size(); index++) {
            CompanionChatMessageItemModel item = items.get(index);
            View itemView = inflater.inflate(R.layout.item_companion_chat_message, messageContainer, false);
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(20);
            }
            itemView.setLayoutParams(params);

            LinearLayout root = itemView.findViewById(R.id.layoutCompanionChatMessageRoot);
            TextView date = itemView.findViewById(R.id.textCompanionChatDate);
            date.setText(item.getDateLabel());
            date.setVisibility(item.getDateLabel().isEmpty() ? View.GONE : View.VISIBLE);
            TextView senderAvatar = itemView.findViewById(R.id.textCompanionChatSenderAvatar);
            senderAvatar.setText(initial(item.getSenderLabel()));
            senderAvatar.setVisibility(item.isMine() ? View.GONE : View.VISIBLE);
            LinearLayout column = itemView.findViewById(R.id.layoutCompanionChatMessageColumn);
            column.setGravity(item.isMine() ? Gravity.END : Gravity.START);
            TextView senderView = itemView.findViewById(R.id.textCompanionChatSender);
            TextView bodyView = itemView.findViewById(R.id.textCompanionChatBody);
            TextView timeView = itemView.findViewById(R.id.textCompanionChatSentAt);
            LinearLayout bubbleRow = itemView.findViewById(R.id.layoutCompanionChatBubbleRow);
            MaterialCardView cardView = itemView.findViewById(R.id.cardCompanionChatBody);
            View attachmentContainer = itemView.findViewById(R.id.layoutCompanionChatAttachment);
            LinearLayout attachmentItemsContainer =
                    itemView.findViewById(R.id.layoutCompanionChatAttachmentItems);

            root.setGravity(item.isMine() ? Gravity.END : Gravity.START);
            senderView.setText(item.getSenderLabel());
            senderView.setVisibility(item.isMine() ? View.GONE : View.VISIBLE);
            bodyView.setText(item.getBody());
            bodyView.setVisibility(item.hasBody() ? View.VISIBLE : View.GONE);
            timeView.setText(item.getSentAtLabel());
            boolean stacked = item.hasAttachment() || context.getResources().getConfiguration().fontScale > 1.3f;
            bubbleRow.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            bubbleRow.setGravity(stacked ? (item.isMine() ? Gravity.END : Gravity.START) : Gravity.BOTTOM);
            if (!stacked && item.isMine()) {
                bubbleRow.removeView(timeView);
                bubbleRow.addView(timeView, 0);
            }
            LinearLayout.LayoutParams timeParams = (LinearLayout.LayoutParams) timeView.getLayoutParams();
            timeParams.topMargin = stacked ? dp(4) : 0;
            timeParams.setMarginStart(!stacked && !item.isMine() ? dp(5) : 0);
            timeParams.setMarginEnd(!stacked && item.isMine() ? dp(5) : 0);
            timeView.setLayoutParams(timeParams);
            cardView.setCardBackgroundColor(ContextCompat.getColor(
                    context,
                    item.isMine() ? R.color.final_brand_primary : R.color.final_surface
            ));
            senderView.setTextColor(ContextCompat.getColor(
                    context,
                    R.color.final_text_secondary
            ));
            // 실제 대화 영역 폭을 기준으로 긴 본문과 첨부의 가로 넘침을 막는다.
            itemView.post(() -> {
                if (itemView.getParent() != messageContainer) return;
                int available = messageContainer.getWidth() - (item.isMine() ? 0 : dp(38)) - dp(30);
                if (!stacked) available -= (int) Math.ceil(timeView.getPaint().measureText(item.getSentAtLabel())) + dp(5);
                int maxWidth = Math.max(dp(100), available);
                int senderWidth = maxWidth;
                ViewGroup.LayoutParams cardParams = cardView.getLayoutParams();
                if (item.hasAttachment()) {
                    cardParams.width = Math.min(dp(260), messageContainer.getWidth() - dp(38));
                } else {
                    int desired = (int) Math.ceil(android.text.Layout.getDesiredWidth(bodyView.getText(), bodyView.getPaint()));
                    cardParams.width = Math.min(maxWidth, desired) + dp(30);
                }
                cardView.setLayoutParams(cardParams);
                maxWidth = cardParams.width - dp(30);
                bodyView.setMaxWidth(maxWidth);
                senderView.setMaxWidth(senderWidth);
                if (item.hasAttachment()) {
                    ViewGroup.LayoutParams attachmentParams = attachmentItemsContainer.getLayoutParams();
                    attachmentParams.width = maxWidth;
                    attachmentItemsContainer.setLayoutParams(attachmentParams);
                }
            });
            bindMessageAttachments(item.getAttachments(), attachmentContainer, attachmentItemsContainer);
            messageContainer.addView(itemView);
        }
    }

    private void bindMessageAttachments(
            List<CompanionChatAttachmentItemModel> attachments,
            View attachmentContainer,
            LinearLayout attachmentItemsContainer
    ) {
        attachmentItemsContainer.removeAllViews();
        if (attachments == null || attachments.isEmpty()) {
            attachmentContainer.setVisibility(View.GONE);
            return;
        }

        attachmentContainer.setVisibility(View.VISIBLE);
        for (int index = 0; index < attachments.size(); index++) {
            CompanionChatAttachmentItemModel item = attachments.get(index);
            View itemView = inflater.inflate(
                    R.layout.item_companion_chat_message_attachment,
                    attachmentItemsContainer,
                    false
            );
            ImageView imagePreview = itemView.findViewById(R.id.imageCompanionChatAttachmentPreview);
            TextView textSummary = itemView.findViewById(R.id.textCompanionChatAttachmentSummary);
            MaterialButton buttonOpen = itemView.findViewById(R.id.buttonCompanionChatAttachmentOpen);

            if (index == attachments.size() - 1) {
                ViewGroup.MarginLayoutParams params =
                        (ViewGroup.MarginLayoutParams) itemView.getLayoutParams();
                params.bottomMargin = 0;
                itemView.setLayoutParams(params);
            }

            if (item.hasImageAttachment() && item.getAttachment() != null) {
                imagePreview.setVisibility(View.VISIBLE);
                messageAttachmentThumbnailBinder.onBindMessageAttachmentThumbnail(
                        imagePreview,
                        item.getAttachment()
                );
            } else {
                imagePreview.setVisibility(View.GONE);
                imagePreview.setImageDrawable(null);
                imagePreview.setTag(null);
            }
            textSummary.setText(item.getSummary());
            buttonOpen.setText(item.getActionLabel());
            buttonOpen.setOnClickListener(view -> {
                if (item.getAttachment() != null) {
                    messageAttachmentActionListener.onOpenAttachment(item.getAttachment());
                }
            });
            attachmentItemsContainer.addView(itemView);
        }
    }

    private String resolvePendingAttachmentMeta(CompanionChatPendingAttachment pendingAttachment) {
        if (pendingAttachment.isImageType()) {
            return context.getString(R.string.companion_chat_attachment_image);
        }
        if (pendingAttachment.isPdfType()) {
            return context.getString(R.string.companion_chat_attachment_pdf);
        }
        return context.getString(R.string.companion_chat_attachment_file);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
