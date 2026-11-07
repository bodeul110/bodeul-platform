package com.example.bodeul.debug;

import android.content.Intent;
import com.example.bodeul.MainActivity;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.domain.model.*;
import com.example.bodeul.ui.common.AppointmentProgressComposer;
import com.example.bodeul.ui.home.ClientHomeDashboard;
import com.example.bodeul.ui.home.ClientHomeNoticeProvider;
import com.example.bodeul.ui.support.ClientSupportAttentionBannerFactory;
import java.util.Collections;

/** 실제 계정·서버 없이 운영 화면의 표시와 화면 이동 요청만 검증한다. */
public class ClientHomeFigmaPreviewActivity extends MainActivity {
    public Intent lastNavigation;
    public boolean signedOut;
    public int loadCalls;
    public boolean retrySucceeds;
    public RepositoryCallback<ClientHomeDashboard> pending;
    public User previewUser;

    @Override protected AuthRepository provideAuthRepository() {
        String mode = getIntent().getStringExtra("scenario");
        UserRole role = "guardian".equals(mode) ? UserRole.GUARDIAN : UserRole.PATIENT;
        previewUser = new User("synthetic-client", role, "합성 사용자", "home@example.invalid", "01000000000");
        return new MockAuthRepository(new MockBodeulRepository()) {
            @Override public void getCurrentUser(RepositoryCallback<User> callback) {
                if ("auth-error".equals(mode)) callback.onError("합성 인증 오류");
                else callback.onSuccess(previewUser);
            }
            @Override public void signOut() { signedOut = true; }
        };
    }

    @Override protected void loadHomeDashboard(User user, RepositoryCallback<ClientHomeDashboard> callback) {
        loadCalls++;
        String mode = getIntent().getStringExtra("scenario");
        if ("loading".equals(mode)) { pending = callback; return; }
        if ("error".equals(mode) && !retrySucceeds) { callback.onError("합성 조회 오류"); return; }
        callback.onSuccess(dashboard(mode));
    }

    public ClientHomeDashboard dashboard(String mode) {
        boolean guardian = "guardian".equals(mode);
        AppointmentStatus status = null;
        if (guardian) status = AppointmentStatus.IN_PROGRESS;
        else try { status = AppointmentStatus.valueOf(mode == null ? "empty" : mode); }
        catch (IllegalArgumentException ignored) { /* 빈 홈이나 실패 재시도는 예약 없이 표시한다. */ }
        AppointmentRequest request = status == null ? null : new AppointmentRequest("synthetic-request", previewUser.getId(),
                "synthetic-guardian", "합성 병원", "내과", "2026-11-07 10:30", "병원 정문", "", status, "synthetic-manager");
        CompanionSession session = guardian ? new MockBodeulRepository().getAppointmentRequestDetail("request-1").getSession() : null;
        if (session != null) session.setGuardianUpdate("합성 보호자 전달 내용");
        GuardianReportEntry entry = guardian ? new GuardianReportEntry(request, null, null, session, null, null) : null;
        return new ClientHomeDashboard(previewUser, request == null ? Collections.emptyList() : Collections.singletonList(request),
                entry, request, request == null ? null : new AppointmentProgressComposer(this).create(previewUser.getRole(), request, null, session, null, null),
                status == AppointmentStatus.COMPLETED ? AppointmentFollowUpRecord.empty(request.getId()) : null,
                request == null ? null : ClientSupportAttentionBannerFactory.create(this, 7, 2),
                request == null ? 0 : 7, request == null ? 0 : 2,
                new ClientHomeNoticeProvider().createNotices(previewUser.getRole()));
    }

    @Override public void startActivity(Intent intent) { lastNavigation = intent; }
}
