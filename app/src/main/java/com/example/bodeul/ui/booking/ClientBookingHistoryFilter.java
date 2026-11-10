package com.example.bodeul.ui.booking;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 이용내역 탭과 서버 예약 상태의 대응 규칙을 한곳에서 관리한다. */
public enum ClientBookingHistoryFilter {
    MATCHING,
    PROGRESS,
    COMPLETED,
    CANCELED;

    public boolean matches(AppointmentStatus status) {
        if (status == null) {
            return false;
        }
        switch (this) {
            case MATCHING:
                return status == AppointmentStatus.REQUESTED;
            case PROGRESS:
                return status == AppointmentStatus.MATCHED
                        || status == AppointmentStatus.IN_PROGRESS;
            case COMPLETED:
                return status == AppointmentStatus.COMPLETED;
            case CANCELED:
                return status == AppointmentStatus.CANCELED;
            default:
                return false;
        }
    }

    public List<AppointmentRequest> select(List<AppointmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<AppointmentRequest> selected = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            if (request != null && matches(request.getStatus())) {
                selected.add(request);
            }
        }
        return selected;
    }
}
