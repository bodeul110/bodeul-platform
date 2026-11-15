package com.example.bodeul.ui.chat;

import java.util.List;

public final class CompanionChatScreenModel {
    private final String requestId;
    private final String ownerKey;
    private final boolean manager;
    private final boolean writable;
    private final String modeLabel;
    private final String title;
    private final String subtitle;
    private final String heroBadge;
    private final String heroTitle;
    private final String heroBody;
    private final String sectionTitle;
    private final String emptyBody;
    private final String inputHint;
    private final String sendButtonLabel;
    private final List<CompanionChatMessageItemModel> messages;

    public CompanionChatScreenModel(
            String requestId,
            String ownerKey,
            boolean manager,
            boolean writable,
            String modeLabel,
            String title,
            String subtitle,
            String heroBadge,
            String heroTitle,
            String heroBody,
            String sectionTitle,
            String emptyBody,
            String inputHint,
            String sendButtonLabel,
            List<CompanionChatMessageItemModel> messages
    ) {
        this.requestId = requestId;
        this.ownerKey = ownerKey;
        this.manager = manager;
        this.writable = writable;
        this.modeLabel = modeLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.heroBadge = heroBadge;
        this.heroTitle = heroTitle;
        this.heroBody = heroBody;
        this.sectionTitle = sectionTitle;
        this.emptyBody = emptyBody;
        this.inputHint = inputHint;
        this.sendButtonLabel = sendButtonLabel;
        this.messages = messages;
    }

    public String getModeLabel() {
        return modeLabel;
    }

    public String getRequestId() { return requestId; }
    public String getOwnerKey() { return ownerKey; }
    public boolean isManager() { return manager; }
    public boolean isWritable() { return writable; }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroBadge() {
        return heroBadge;
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public String getHeroBody() {
        return heroBody;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getEmptyBody() {
        return emptyBody;
    }

    public String getInputHint() {
        return inputHint;
    }

    public String getSendButtonLabel() {
        return sendButtonLabel;
    }

    public List<CompanionChatMessageItemModel> getMessages() {
        return messages;
    }
}
