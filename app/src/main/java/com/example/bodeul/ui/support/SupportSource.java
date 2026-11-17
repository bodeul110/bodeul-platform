package com.example.bodeul.ui.support;

import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.ClientSupportRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 화면의 공통 모델과 기존 역할별 저장소 계약 사이를 연결한다. */
public final class SupportSource {
    private final ClientSupportRepository client;
    private final BookingRepository booking;
    private final ManagerRepository manager;

    public SupportSource(ClientSupportRepository client, BookingRepository booking) {
        this.client = client;
        this.booking = booking;
        this.manager = null;
    }

    public SupportSource(ManagerRepository manager) {
        this.manager = manager;
        this.client = null;
        this.booking = null;
    }

    public boolean isManager() { return manager != null; }
    public boolean allows(User user) {
        return user != null && user.getId() != null && !user.getId().isEmpty()
                && (isManager() ? user.getRole() == UserRole.MANAGER
                : user.getRole() == UserRole.PATIENT || user.getRole() == UserRole.GUARDIAN);
    }

    public String[] categories() {
        return isManager() ? new String[]{"MATCHING", "DOCUMENT", "SETTLEMENT", "OTHER"}
                : new String[]{"RESERVATION", "PROGRESS", "REPORT", "SETTLEMENT", "OTHER"};
    }

    public boolean hasCategory(String category) {
        for (String value : categories()) if (value.equals(category)) return true;
        return false;
    }

    public int categoryLabel(String value) {
        if (isManager()) {
            switch (value) {
                case "MATCHING": return R.string.manager_support_category_matching;
                case "DOCUMENT": return R.string.manager_support_category_document;
                case "SETTLEMENT": return R.string.manager_support_category_settlement;
                default: return R.string.manager_support_category_other;
            }
        }
        switch (value) {
            case "RESERVATION": return R.string.client_support_category_reservation;
            case "PROGRESS": return R.string.client_support_category_progress;
            case "REPORT": return R.string.client_support_category_report;
            case "SETTLEMENT": return R.string.client_support_category_settlement;
            default: return R.string.client_support_category_other;
        }
    }

    public void validateContext(User user, String appointmentId, RepositoryCallback<String> callback) {
        if (isManager() || appointmentId.isEmpty()) { callback.onSuccess(""); return; }
        booking.getAppointmentRequestDetail(user, appointmentId, new RepositoryCallback<AppointmentRequestDetail>() {
            @Override public void onSuccess(AppointmentRequestDetail value) {
                if (value != null && value.getAppointmentRequest() != null
                        && appointmentId.equals(value.getAppointmentRequest().getId())) callback.onSuccess(appointmentId);
                else callback.onError("연결할 예약을 확인하지 못했습니다.");
            }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    public void load(User user, RepositoryCallback<List<SupportTicket>> callback) {
        if (isManager()) manager.getSupportInquiries(user.getId(), managerCallback(user, callback));
        else client.getClientSupportRequests(user, clientCallback(user, callback));
    }

    public void submit(User user, String appointmentId, String category, String title, String body,
                       RepositoryCallback<List<SupportTicket>> callback) {
        if (!allows(user) || !hasCategory(category)) { callback.onError("문의 권한과 유형을 확인해 주세요."); return; }
        if (isManager()) manager.submitSupportInquiry(user.getId(), SupportInquiryCategory.valueOf(category),
                title, body, managerCallback(user, callback));
        else client.submitClientSupportRequest(user, appointmentId, ClientSupportCategory.valueOf(category),
                title, body, clientCallback(user, callback));
    }

    public void markRead(User user, List<SupportTicket> tickets) {
        if (isManager()) return;
        for (SupportTicket ticket : tickets) {
            if (!ticket.unread) continue;
            client.markClientSupportResponsesRead(user, new RepositoryCallback<Void>() {
                @Override public void onSuccess(Void ignored) { }
                @Override public void onError(String message) { }
            });
            return;
        }
    }

    private RepositoryCallback<List<ClientSupportRequest>> clientCallback(User user, RepositoryCallback<List<SupportTicket>> callback) {
        return new RepositoryCallback<List<ClientSupportRequest>>() {
            @Override public void onSuccess(List<ClientSupportRequest> rows) {
                List<SupportTicket> tickets = new ArrayList<>();
                if (rows != null) for (ClientSupportRequest row : rows) {
                    if (row != null && user.getId().equals(row.getUserId()) && user.getRole() == row.getUserRole())
                        tickets.add(SupportTicket.from(row));
                }
                tickets.sort(Comparator.comparingLong((SupportTicket value) -> value.createdAt).reversed());
                callback.onSuccess(tickets);
            }
            @Override public void onError(String message) { callback.onError(message); }
        };
    }

    private RepositoryCallback<List<SupportInquiry>> managerCallback(User user, RepositoryCallback<List<SupportTicket>> callback) {
        return new RepositoryCallback<List<SupportInquiry>>() {
            @Override public void onSuccess(List<SupportInquiry> rows) {
                List<SupportTicket> tickets = new ArrayList<>();
                if (rows != null) for (SupportInquiry row : rows) {
                    if (row != null && user.getId().equals(row.getManagerUserId())) tickets.add(SupportTicket.from(row));
                }
                tickets.sort(Comparator.comparingLong((SupportTicket value) -> value.createdAt).reversed());
                callback.onSuccess(tickets);
            }
            @Override public void onError(String message) { callback.onError(message); }
        };
    }
}
