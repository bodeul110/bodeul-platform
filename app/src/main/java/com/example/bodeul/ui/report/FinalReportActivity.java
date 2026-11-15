package com.example.bodeul.ui.report;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.FinalReportRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.RoleScopedFinalReportRepository;
import com.example.bodeul.data.ServiceLocator;
import com.example.bodeul.domain.model.GuardianReportEntry;
import com.example.bodeul.domain.model.AppointmentStatus;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;
import com.example.bodeul.ui.auth.AuthFlowRouter;
import com.example.bodeul.util.StatePanelHelper;

/** 화면 복귀마다 인증과 조회를 다시 수행하고 의료 내용을 Intent나 저장 상태에 담지 않는다. */
public class FinalReportActivity extends AppCompatActivity {
    private static final String EXTRA_REQUEST_ID = "requestId";
    private AuthRepository auth;
    private FinalReportRepository reports;
    private GuardianReportCoordinator coordinator;
    private FinalReportBinder binder;
    private int generation;
    private boolean started;

    public static Intent createIntent(Context context, String requestId) {
        return new Intent(context, FinalReportActivity.class).putExtra(EXTRA_REQUEST_ID, requestId);
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_final_report);
        View root = findViewById(R.id.finalReportRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
        auth = provideAuthRepository();
        reports = provideFinalReportRepository();
        coordinator = new GuardianReportCoordinator(this, new GuardianReportPresentationFormatter(this));
        binder = new FinalReportBinder(findViewById(R.id.finalReportContent));
        findViewById(R.id.buttonFinalReportBack).setOnClickListener(view -> finish());
        findViewById(R.id.buttonFinalReportRefresh).setOnClickListener(view -> {
            PopupMenu menu = new PopupMenu(this, view);
            menu.getMenu().add(R.string.final_report_refresh).setOnMenuItemClickListener(item -> {
                reload();
                return true;
            });
            menu.show();
        });
    }

    protected AuthRepository provideAuthRepository() { return ServiceLocator.provideAuthRepository(this); }

    protected FinalReportRepository provideFinalReportRepository() {
        return new RoleScopedFinalReportRepository(ServiceLocator.provideBookingRepository(this),
                ServiceLocator.provideGuardianReportRepository(this), ServiceLocator.provideManagerRepository(this));
    }

    @Override protected void onStart() {
        super.onStart();
        started = true;
        reload();
    }

    @Override protected void onStop() {
        started = false;
        generation++;
        findViewById(R.id.finalReportScroll).setVisibility(View.GONE);
        super.onStop();
    }

    private void reload() {
        int requestGeneration = ++generation;
        findViewById(R.id.finalReportScroll).setVisibility(View.GONE);
        findViewById(R.id.finalReportStateScroll).setVisibility(View.GONE);
        loading(true);
        String requestId = getIntent().getStringExtra(EXTRA_REQUEST_ID);
        if (requestId == null || requestId.trim().isEmpty()) {
            fail(getString(R.string.final_report_missing));
            return;
        }
        auth.getCurrentUser(new RepositoryCallback<User>() {
            @Override public void onSuccess(User user) {
                if (!active(requestGeneration)) return;
                if (user == null || AuthFlowRouter.requiresProfileCompletion(user)
                        || (user.getRole() != UserRole.PATIENT && user.getRole() != UserRole.GUARDIAN
                        && user.getRole() != UserRole.MANAGER)) {
                    fail(getString(R.string.state_permission_body));
                    return;
                }
                reports.getReport(user, requestId, new RepositoryCallback<GuardianReportEntry>() {
                    @Override public void onSuccess(GuardianReportEntry entry) {
                        if (!active(requestGeneration)) return;
                        if (entry == null || !requestId.equals(entry.getAppointmentRequest().getId())
                                || entry.getAppointmentRequest().getStatus()
                                != AppointmentStatus.COMPLETED) {
                            fail(getString(R.string.final_report_missing));
                            return;
                        }
                        boolean managerView = user.getRole() == UserRole.MANAGER;
                        binder.bind(coordinator.createEntryCardModel(entry, managerView), managerView);
                        loading(false);
                        findViewById(R.id.finalReportScroll).setVisibility(View.VISIBLE);
                    }
                    @Override public void onError(String message) {
                        if (active(requestGeneration)) fail(getString(R.string.final_report_missing));
                    }
                });
            }
            @Override public void onError(String message) {
                if (active(requestGeneration)) fail(getString(R.string.state_auth_body));
            }
        });
    }

    private boolean active(int requestGeneration) {
        return started && requestGeneration == generation && !isFinishing() && !isDestroyed();
    }

    private void loading(boolean loading) {
        findViewById(R.id.progressFinalReport).setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonFinalReportRefresh).setEnabled(!loading);
    }

    private void fail(String message) {
        loading(false);
        findViewById(R.id.finalReportScroll).setVisibility(View.GONE);
        findViewById(R.id.finalReportStateScroll).setVisibility(View.VISIBLE);
        StatePanelHelper.show(findViewById(R.id.finalReportStatePanel), StatePanelHelper.Tone.WARNING,
                getString(R.string.state_badge_notice), getString(R.string.final_report_title), message,
                getString(R.string.state_action_retry), view -> reload(), null, null);
    }
}
