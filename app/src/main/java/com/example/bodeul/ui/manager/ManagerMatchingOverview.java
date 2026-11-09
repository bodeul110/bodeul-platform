package com.example.bodeul.ui.manager;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 저장소에서 이미 인가한 배정 목록의 집계와 필터만 담당한다. */
public final class ManagerMatchingOverview {
    public enum Filter { ALL, ONGOING, COMPLETED, CANCELED }
    private final List<AppointmentRequest> requests;

    public ManagerMatchingOverview(List<AppointmentRequest> source) {
        Map<String, AppointmentRequest> unique = new LinkedHashMap<>();
        if (source != null) for (AppointmentRequest request : source) {
            if (request != null && request.getId() != null && !request.getId().isEmpty()) {
                unique.putIfAbsent(request.getId(), request);
            }
        }
        List<AppointmentRequest> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing((AppointmentRequest r) -> safe(r.getAppointmentAt())).reversed());
        requests = Collections.unmodifiableList(new ArrayList<>(sorted.subList(0, Math.min(100, sorted.size()))));
    }

    public int count(Filter filter) { return select(filter, null).size(); }

    public List<AppointmentRequest> select(Filter filter, String date) {
        List<AppointmentRequest> result = new ArrayList<>();
        for (AppointmentRequest request : requests) {
            if (matches(filter, request.getStatus())
                    && (date == null || date.equals(dateOf(request)))) result.add(request);
        }
        return Collections.unmodifiableList(result);
    }

    public boolean hasUndatedRequests() {
        for (AppointmentRequest request : requests) if (dateOf(request).isEmpty()) return true;
        return false;
    }

    private static boolean matches(Filter filter, AppointmentStatus status) {
        if (filter == Filter.ALL) return true;
        if (filter == Filter.ONGOING) return status == AppointmentStatus.MATCHED || status == AppointmentStatus.IN_PROGRESS;
        if (filter == Filter.COMPLETED) return status == AppointmentStatus.COMPLETED;
        return status == AppointmentStatus.CANCELED;
    }

    private static String dateOf(AppointmentRequest request) {
        // Core API와 기존 앱의 서울 로컬 일정 문자열(yyyy-MM-dd HH:mm)을 날짜로 비교한다.
        String value = safe(request.getAppointmentAt());
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?")) return "";
        java.text.SimpleDateFormat parser = new java.text.SimpleDateFormat(
                value.length() == 19 ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd HH:mm", java.util.Locale.ROOT);
        parser.setLenient(false);
        parser.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Seoul"));
        String local = value.replace('T', ' ');
        try { parser.parse(local); return value.substring(0, 10); }
        catch (java.text.ParseException ignored) { return ""; }
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
