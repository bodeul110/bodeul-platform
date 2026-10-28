package com.example.bodeul.debug;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.bodeul.R;
import com.example.bodeul.data.GuardianBookingRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingReceipt;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.ui.booking.GuardianBookingCoordinator;
import com.example.bodeul.ui.booking.GuardianBookingReviewBinder;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 외부 입력·로그인·네트워크를 사용하지 않는 합성 승인 흐름이다. */
public final class GuardianBookingPreviewActivity extends AppCompatActivity {
    private final LocalRepository repository = new LocalRepository();
    private GuardianBookingCoordinator coordinator;
    private GuardianBookingReviewBinder binder;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guardian_booking_preview);
        View root = findViewById(R.id.guardianBookingPreviewRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        binder = new GuardianBookingReviewBinder(findViewById(R.id.guardianBookingReviewRoot));
        MaterialButtonToggleGroup roles = findViewById(R.id.groupGuardianBookingPreviewRole);
        roles.setSaveEnabled(false);
        findViewById(R.id.buttonGuardianBookingPreviewPatient).setSaveEnabled(false);
        findViewById(R.id.buttonGuardianBookingPreviewGuardian).setSaveEnabled(false);
        roles.check(R.id.buttonGuardianBookingPreviewPatient);
        roles.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (checked) selectRole(checkedId == R.id.buttonGuardianBookingPreviewPatient);
        });
        selectRole(true);
    }

    private void selectRole(boolean patient) {
        if (coordinator != null) coordinator.dispose();
        repository.patient = patient;
        coordinator = new GuardianBookingCoordinator(repository, fixture(), Clock.systemUTC(), binder::render);
        binder.bindActions(coordinator);
        coordinator.refresh();
    }

    @Override
    protected void onDestroy() {
        if (coordinator != null) coordinator.dispose();
        super.onDestroy();
    }

    private static GuardianBookingRequest fixture() {
        return new GuardianBookingRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                BookingRequestDraft.builder().linkedParticipantName("합성 환자")
                        .linkedParticipantPhone("01000000000").linkedParticipantEmail("patient@example.com")
                        .hospitalName("합성 검증 병원").departmentName("내과")
                        .hospitalLatitude(37.5).hospitalLongitude(127.0)
                        .appointmentAt("2099-01-01 10:00").meetingPlace("1층 안내 데스크")
                        .patientConditionSummary("합성 상태 기록\n추가 확인사항")
                        .medicationSummary("합성 복약 기록").specialNotes("실제 예약이 아닌 화면 검증용 요청")
                        .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                        .priceSummary(new BookingPriceSummary(40_000, 0, 0, 40_000)).build());
    }

    private static final class LocalRepository implements GuardianBookingRepository {
        boolean patient = true;
        GuardianBookingApproval approval = new GuardianBookingApproval(null, 0, null, null, null, false);
        GuardianBookingReceipt receipt;

        @Override
        public void load(GuardianBookingRequest request, RepositoryCallback<Review> callback) {
            callback.onSuccess(new Review(request, approval, patient));
        }

        @Override
        public void grant(GuardianBookingRequest request, long version, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            if (!patient || !confirmed || version != approval.getVersion() || receipt != null) {
                callback.onError("합성 승인 조건이 맞지 않습니다.");
                return;
            }
            Instant now = Instant.now();
            approval = new GuardianBookingApproval(UUID.randomUUID(), version + 1,
                    now, now.plusSeconds(3600), null, true);
            callback.onSuccess(approval);
        }

        @Override
        public void revoke(GuardianBookingRequest request, long version,
                RepositoryCallback<GuardianBookingApproval> callback) {
            if (!patient || version != approval.getVersion() || !approval.canRevoke()) {
                callback.onError("합성 철회 조건이 맞지 않습니다.");
                return;
            }
            approval = new GuardianBookingApproval(approval.getGrantId(), version + 1,
                    approval.getGrantedAt(), approval.getExpiresAt(), Instant.now(), false);
            callback.onSuccess(approval);
        }

        @Override
        public void create(GuardianBookingRequest request, GuardianBookingApproval candidate,
                RepositoryCallback<CreateResult> callback) {
            if (patient || (receipt == null && (!approval.canCreateAt(Instant.now())
                    || candidate.getVersion() != approval.getVersion()
                    || !candidate.getGrantId().equals(approval.getGrantId())))) {
                callback.onSuccess(CreateResult.failed(CreateFailure.REJECTED));
                return;
            }
            if (receipt == null) receipt = new GuardianBookingReceipt(
                    UUID.fromString("40000000-0000-0000-0000-000000000004"), "TEST-0001");
            callback.onSuccess(CreateResult.accepted(receipt));
        }
    }
}
