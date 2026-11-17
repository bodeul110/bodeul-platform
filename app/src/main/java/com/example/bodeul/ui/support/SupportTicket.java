package com.example.bodeul.ui.support;

import com.example.bodeul.domain.model.ClientSupportRequest;
import com.example.bodeul.domain.model.ClientSupportStatus;
import com.example.bodeul.domain.model.SupportInquiry;
import com.example.bodeul.domain.model.SupportInquiryStatus;

/** 역할별 원문을 고객센터에서 공통으로 표시하며 줄바꿈을 보존한다. */
public final class SupportTicket {
    public final String id, ownerId, category, title, body, response, responder, appointmentId;
    public final boolean answered, unread;
    public final long createdAt, respondedAt;

    private SupportTicket(String id, String ownerId, String category, String title, String body,
                          String response, String responder, String appointmentId, boolean answered,
                          boolean unread, long createdAt, long respondedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.response = response;
        this.responder = responder;
        this.appointmentId = appointmentId;
        this.answered = answered;
        this.unread = unread;
        this.createdAt = createdAt;
        this.respondedAt = respondedAt;
    }

    public static SupportTicket from(ClientSupportRequest value) {
        return new SupportTicket(value.getId(), value.getUserId(), value.getCategory().name(),
                value.getTitle(), value.getBody(), value.getResponseText(), value.getRespondedByName(),
                value.getAppointmentRequestId(), value.getStatus() == ClientSupportStatus.ANSWERED,
                value.hasUnreadResponse(), value.getCreatedAtMillis(), value.getRespondedAtMillis());
    }

    public static SupportTicket from(SupportInquiry value) {
        return new SupportTicket(value.getId(), value.getManagerUserId(), value.getCategory().name(),
                value.getTitle(), value.getBody(), value.getResponseText(), value.getRespondedByName(),
                "", value.getStatus() == SupportInquiryStatus.ANSWERED, false,
                value.getCreatedAtMillis(), value.getRespondedAtMillis());
    }
}
