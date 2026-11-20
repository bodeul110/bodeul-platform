package com.example.bodeul.ui.chat;

import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.ManagerDashboard;
import com.example.bodeul.domain.model.SessionStatus;
import com.example.bodeul.domain.model.UserRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 인증된 예약 요약만 투영한다. 목록에서는 대화 본문·첨부·읽음 처리를 요청하지 않는다. */
public final class ChatRoomDirectory {
    public enum Filter { ALL, UNREAD, ACTIVE, COMPLETE }
    public static final class Room {
        public final String id, counterpart, hospital;
        public final AppointmentStatus status;
        private Room(AppointmentRequest r, UserRole role) {
            id = r.getId();
            hospital = clean(r.getHospitalName());
            counterpart = role == UserRole.GUARDIAN ? "" :
                    clean(role == UserRole.MANAGER ? r.getPatientName() : r.getManagerName());
            status = r.getStatus();
        }
    }
    private ChatRoomDirectory() {}
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    public static List<Room> client(UserRole role, List<AppointmentRequest> rows) {
        Map<String, Room> result = new LinkedHashMap<>();
        if ((role == UserRole.PATIENT || role == UserRole.GUARDIAN) && rows != null)
            for (AppointmentRequest r : rows) {
                if (r == null || clean(r.getId()).isEmpty()) continue;
                if (r.getStatus() == AppointmentStatus.IN_PROGRESS || r.getStatus() == AppointmentStatus.MATCHED
                        || (role == UserRole.PATIENT && r.getStatus() == AppointmentStatus.COMPLETED))
                    result.putIfAbsent(r.getId(), new Room(r, role));
            }
        return new ArrayList<>(result.values());
    }
    public static List<Room> manager(ManagerDashboard d) {
        if (d == null || d.getSession() == null || d.getAppointmentRequest() == null) return Collections.emptyList();
        AppointmentRequest r = d.getAppointmentRequest();
        SessionStatus s = d.getSession().getStatus();
        if (clean(r.getId()).isEmpty() || !r.getId().equals(d.getSession().getAppointmentRequestId())
                || d.getSession().getCareEndedAtMillis() > 0 || s == null
                || s == SessionStatus.CANCELED || s == SessionStatus.CARE_ENDED || s == SessionStatus.COMPLETED
                || (r.getStatus() != AppointmentStatus.MATCHED && r.getStatus() != AppointmentStatus.IN_PROGRESS))
            return Collections.emptyList();
        return Collections.singletonList(new Room(r, UserRole.MANAGER));
    }
    public static List<Room> filter(List<Room> rooms, Filter filter, String search) {
        String term = clean(search).toLowerCase(Locale.ROOT);
        List<Room> result = new ArrayList<>();
        for (Room r : rooms) {
            if (filter == Filter.UNREAD || (filter == Filter.COMPLETE && r.status != AppointmentStatus.COMPLETED)
                    || (filter == Filter.ACTIVE && r.status != AppointmentStatus.IN_PROGRESS)) continue;
            if (r.hospital.toLowerCase(Locale.ROOT).contains(term) || r.counterpart.toLowerCase(Locale.ROOT).contains(term))
                result.add(r);
        }
        return result;
    }
}
