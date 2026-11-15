package com.example.bodeul.ui.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CompanionChatMessageItemModel {
    private final String senderLabel;
    private final String dateLabel;
    private final String body;
    private final String sentAtLabel;
    private final boolean mine;
    private final List<CompanionChatAttachmentItemModel> attachments;

    public CompanionChatMessageItemModel(
            String dateLabel,
            String senderLabel,
            String body,
            String sentAtLabel,
            boolean mine,
            List<CompanionChatAttachmentItemModel> attachments
    ) {
        this.dateLabel = dateLabel;
        this.senderLabel = senderLabel;
        this.body = body;
        this.sentAtLabel = sentAtLabel;
        this.mine = mine;
        this.attachments = attachments == null
                ? new ArrayList<>()
                : new ArrayList<>(attachments);
    }

    public String getSenderLabel() {
        return senderLabel;
    }

    public String getDateLabel() { return dateLabel; }

    public String getBody() {
        return body;
    }

    public String getSentAtLabel() {
        return sentAtLabel;
    }

    public boolean isMine() {
        return mine;
    }

    public boolean hasBody() {
        return body != null && !body.trim().isEmpty();
    }

    public boolean hasAttachment() {
        return !attachments.isEmpty();
    }

    public List<CompanionChatAttachmentItemModel> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }
}
