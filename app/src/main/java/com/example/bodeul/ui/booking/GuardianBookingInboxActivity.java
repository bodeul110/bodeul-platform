package com.example.bodeul.ui.booking;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingAccountSession;
import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.coreapi.CoreApiGuardianBookingDraftRepository;
import com.example.bodeul.data.firebase.FirebaseGuardianBookingAccountSession;

import java.time.Clock;
import java.util.Objects;

/** Intent·Bundle에는 원문·참여자·승인 정보를 넣지 않고 재진입마다 서버에서 읽는다. */
public class GuardianBookingInboxActivity extends AppCompatActivity {
    private GuardianBookingAccountSession session;
    private GuardianBookingInboxCoordinator coordinator;
    private GuardianBookingInboxBinder binder;
    private String initialAccount;

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (!isInboxEnabled()) {
            Toast.makeText(this, R.string.guardian_booking_inbox_disabled, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_guardian_booking_inbox);
        View root = findViewById(R.id.guardianBookingInboxRoot);
        // 자식 뷰의 텍스트와 체크 상태도 Activity의 자동 상태 저장에서 제외한다.
        root.setSaveFromParentEnabled(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        findViewById(R.id.buttonGuardianBookingInboxBack).setOnClickListener(view -> finish());
        binder = new GuardianBookingInboxBinder(root);
        session = createAccountSession();
        initialAccount = session.currentAccountId();
    }

    @Override protected void onStart() {
        super.onStart();
        if (session == null || isFinishing()) return;
        if (initialAccount == null || !Objects.equals(initialAccount, session.currentAccountId())) {
            closeForAccountChange();
            return;
        }
        coordinator = new GuardianBookingInboxCoordinator(createRepository(), session::currentAccountId,
                Clock.systemUTC(), state -> {
                    binder.render(state);
                    if (state.phase == GuardianBookingInboxCoordinator.Phase.CLOSED && state.message != 0) {
                        closeForAccountChange();
                    }
                });
        binder.bindActions(coordinator);
        session.observe(() -> { if (coordinator != null) coordinator.accountChanged(); });
        coordinator.refreshList();
    }

    @Override protected void onStop() {
        if (session != null) session.stopObserving();
        if (coordinator != null) coordinator.dispose();
        coordinator = null;
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (session != null) session.stopObserving();
        if (coordinator != null) coordinator.dispose();
        super.onDestroy();
    }

    protected boolean isInboxEnabled() { return getResources().getBoolean(R.bool.bodeul_guardian_booking_inbox_enabled); }
    protected GuardianBookingDraftRepository createRepository() { return new CoreApiGuardianBookingDraftRepository(this); }
    protected GuardianBookingAccountSession createAccountSession() { return new FirebaseGuardianBookingAccountSession(this); }

    private void closeForAccountChange() {
        Toast.makeText(this, R.string.guardian_booking_account_changed, Toast.LENGTH_LONG).show();
        finish();
    }
}
