package com.example.bodeul.data;

import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.GuardianReportDashboard;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

import java.util.List;

/** 상세 ID만 전달받고 역할별 저장소에서 다시 인가한다. 다른 역할 경로로 재시도하지 않는다. */
public final class RoleScopedFinalReportRepository implements FinalReportRepository {
    private final BookingRepository booking;
    private final GuardianReportRepository guardian;
    private final ManagerRepository manager;

    public RoleScopedFinalReportRepository(BookingRepository booking,
            GuardianReportRepository guardian, ManagerRepository manager) {
        this.booking = booking;
        this.guardian = guardian;
        this.manager = manager;
    }

    @Override
    public void getReport(User user, String requestId, RepositoryCallback<GuardianReportEntry> callback) {
        if (user == null || requestId == null || requestId.trim().isEmpty()) {
            unavailable(callback);
        } else if (user.getRole() == UserRole.PATIENT) {
            booking.getAppointmentRequestDetail(user, requestId, new RepositoryCallback<AppointmentRequestDetail>() {
                @Override public void onSuccess(AppointmentRequestDetail detail) {
                    deliver(requestId, detail == null ? null : fromDetail(detail, false), callback);
                }
                @Override public void onError(String message) { callback.onError(message); }
            });
        } else if (user.getRole() == UserRole.GUARDIAN) {
            guardian.getGuardianDashboard(user, new RepositoryCallback<GuardianReportDashboard>() {
                @Override public void onSuccess(GuardianReportDashboard dashboard) {
                    if (dashboard != null) {
                        for (GuardianReportEntry entry : dashboard.getEntries()) {
                            if (entry != null && requestId.equals(entry.getAppointmentRequest().getId())) {
                                deliver(requestId, entry, callback);
                                return;
                            }
                        }
                    }
                    unavailable(callback);
                }
                @Override public void onError(String message) { callback.onError(message); }
            });
        } else if (user.getRole() == UserRole.MANAGER) {
            manager.getManagerHistoryDetails(user.getId(), new RepositoryCallback<List<AppointmentRequestDetail>>() {
                @Override public void onSuccess(List<AppointmentRequestDetail> history) {
                    if (history != null) {
                        for (AppointmentRequestDetail detail : history) {
                            if (detail != null && requestId.equals(detail.getAppointmentRequest().getId())) {
                                deliver(requestId, fromDetail(detail, true), callback);
                                return;
                            }
                        }
                    }
                    unavailable(callback);
                }
                @Override public void onError(String message) { callback.onError(message); }
            });
        } else {
            unavailable(callback);
        }
    }

    private static GuardianReportEntry fromDetail(AppointmentRequestDetail detail, boolean managerView) {
        // 구형/Mock 저장소가 의료 보고서를 반환해도 종료 후 매니저 화면에 전달하지 않는다.
        return new GuardianReportEntry(detail.getAppointmentRequest(), detail.getPatient(),
                detail.getManager(), detail.getSession(), managerView ? null : detail.getSessionReport(),
                detail.getHospitalGuide());
    }

    private static void deliver(String requestId, GuardianReportEntry entry,
            RepositoryCallback<GuardianReportEntry> callback) {
        if (entry == null || !requestId.equals(entry.getAppointmentRequest().getId())
                || entry.getAppointmentRequest().getStatus() != AppointmentStatus.COMPLETED) {
            unavailable(callback);
            return;
        }
        callback.onSuccess(entry);
    }

    private static void unavailable(RepositoryCallback<GuardianReportEntry> callback) {
        callback.onError("이 동행의 최종 리포트를 조회할 수 없습니다.");
    }
}
