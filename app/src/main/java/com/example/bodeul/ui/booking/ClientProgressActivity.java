package com.example.bodeul.ui.booking;

import android.content.Context;
import android.content.Intent;
import com.example.bodeul.R;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.AppointmentRequestDetail;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.common.DetailViews;
import com.example.bodeul.ui.profile.SettingsDetailActivity;
import com.example.bodeul.ui.report.FinalReportActivity;

public class ClientProgressActivity extends SettingsDetailActivity {
    private static final String EXTRA = "requestId";
    private Runnable detach;
    public static Intent createIntent(Context c,String id) { return new Intent(c,ClientProgressActivity.class).putExtra(EXTRA,id); }
    protected BookingRepository bookings() { return ServiceLocator.provideBookingRepository(this); }
    @Override protected int titleResource() { return R.string.batch_progress; }
    @Override protected void onPrivateStateCleared() {
        if (detach != null) { Runnable close = detach; detach = null; close.run(); }
    }
    @Override protected void showUser(User current, int attempt) {
        String id = getIntent().getStringExtra(EXTRA);
        if (current.getRole() == UserRole.MANAGER || id == null || id.trim().isEmpty()) { empty(); return; }
        // 권한 거절 뒤 대기 중이던 이전 스냅샷이 도착해도 다시 표시하지 않는다.
        boolean[] closed = {false};
        Runnable subscription = bookings().observeAppointmentRequestDetail(current,id,new RepositoryCallback<AppointmentRequestDetail>() {
            public void onSuccess(AppointmentRequestDetail d) {
                if (!isCurrent(attempt) || closed[0]) return;
                if (!ClientProgressBinder.hasSession(d) || !id.equals(d.getAppointmentRequest().getId())) { empty(); return; }
                ready();
                ClientProgressBinder.render(content,d,() -> {
                    if (isCurrent(attempt)) startActivity(FinalReportActivity.createIntent(ClientProgressActivity.this,id));
                });
            }
            public void onError(String ignored) {
                if (!isCurrent(attempt) || closed[0]) return;
                closed[0] = true;
                onPrivateStateCleared();
                ready();
                content.removeAllViews();
                DetailViews.text(content,getString(R.string.batch_data_error),R.style.FinalProfileBody);
                DetailViews.action(content,R.string.final_profile_retry,ClientProgressActivity.this::reload);
            }
        });
        if (isCurrent(attempt) && !closed[0]) detach = subscription; else if (subscription != null) subscription.run();
    }
    private void empty() {
        ready();
        content.removeAllViews();
        DetailViews.text(content,getString(R.string.batch_progress_missing),R.style.FinalProfileBody);
    }
}
