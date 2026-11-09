package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.ManagerRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockManagerRepository;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.manager.ManagerActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** 운영 레이아웃과 이동 요청을 합성 데이터만으로 검사한다. 실제 인증·DB에는 연결하지 않는다. */
public class ManagerHomeFigmaPreviewActivity extends ManagerActivity {
    public Intent lastNavigation;
    public boolean signedOut;
    public boolean retrySucceeds;
    public boolean authFailure;
    public boolean patientRole;
    public boolean profileFailure;
    public int appointmentLoads;
    public int saves;
    public String savedNote;
    public RepositoryCallback<List<AppointmentRequest>> pendingAppointments;
    public RepositoryCallback<ManagerHomeProfile> pendingSave;
    public User user;

    private String mode() { return getIntent().getStringExtra("scenario"); }

    @Override protected AuthRepository provideAuthRepository() {
        return new MockAuthRepository(new MockBodeulRepository()) {
            @Override public void getCurrentUser(RepositoryCallback<User> callback) {
                if (authFailure || "auth-error".equals(mode())) { callback.onError("합성 인증 오류"); return; }
                String name = "long-name".equals(mode()) ? "아주긴이름을사용하는합성검증매니저" : "합성";
                user = new User("synthetic-manager", patientRole || "wrong-role".equals(mode()) ? UserRole.PATIENT : UserRole.MANAGER,
                        name, "manager@example.invalid", "01000000000");
                callback.onSuccess(user);
            }
            @Override public void signOut() { signedOut = true; }
        };
    }

    @Override protected ManagerRepository provideManagerRepository() {
        return new MockManagerRepository(new MockBodeulRepository()) {
            @Override public void getManagerAppointments(String uid, RepositoryCallback<List<AppointmentRequest>> callback) {
                appointmentLoads++;
                if ("loading".equals(mode())) { pendingAppointments = callback; return; }
                if (("error".equals(mode()) || "partial-error".equals(mode())) && !retrySucceeds) {
                    callback.onError("합성 매칭 오류"); return;
                }
                callback.onSuccess("empty".equals(mode()) || "long-name".equals(mode()) || retrySucceeds
                        ? Collections.emptyList() : requests());
            }
            @Override public void getManagerDashboard(String uid, RepositoryCallback<ManagerDashboard> callback) {
                if ("session-error".equals(mode()) && !retrySucceeds) { callback.onError("합성 세션 오류"); return; }
                if ("active".equals(mode()) || "partial-error".equals(mode())) callback.onSuccess(activeDashboard());
                else callback.onError(ManagerRepository.MESSAGE_NO_ACTIVE_SESSION);
            }
            @Override public void getManagerHomeProfile(String uid, RepositoryCallback<ManagerHomeProfile> callback) {
                if (profileFailure || "profile-error".equals(mode())) { callback.onError("합성 프로필 오류"); return; }
                callback.onSuccess(new ManagerHomeProfile("합성 서류", savedNote == null ? "평일 오전 가능" : savedNote));
            }
            @Override public void saveManagerAvailabilitySummary(String uid, String value, RepositoryCallback<ManagerHomeProfile> callback) {
                saves++;
                savedNote = value;
                pendingSave = callback;
                if (!"saving".equals(mode())) callback.onSuccess(new ManagerHomeProfile("합성 서류", value));
            }
        };
    }

    public static ManagerDashboard activeDashboard() {
        MockBodeulRepository source = new MockBodeulRepository();
        for (User manager : source.getUsersByRole(UserRole.MANAGER)) {
            ManagerDashboard result = source.getManagerDashboard(manager.getId());
            if (result != null) return result;
        }
        throw new IllegalStateException("합성 활성 동행이 없습니다.");
    }

    public static List<AppointmentRequest> requests() {
        List<AppointmentRequest> result = new ArrayList<>();
        result.add(request(activeDashboard().getAppointmentRequest().getId(), AppointmentStatus.MATCHED, today() + " 10:30"));
        result.add(request("ongoing", AppointmentStatus.IN_PROGRESS, today() + " 14:30"));
        result.add(request("completed", AppointmentStatus.COMPLETED, "2026-01-01 10:30"));
        result.add(request("canceled", AppointmentStatus.CANCELED, today() + " 16:30"));
        result.add(request("undated", AppointmentStatus.REQUESTED, ""));
        return result;
    }

    private static AppointmentRequest request(String id, AppointmentStatus status, String date) {
        return new AppointmentRequest(id, "synthetic-patient", "", "합성 병원 " + id, "내과", date, "병원 정문", "", status, "synthetic-manager");
    }

    public static String today() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return formatter.format(new Date());
    }

    @Override public void startActivity(Intent intent) { lastNavigation = intent; }
}
