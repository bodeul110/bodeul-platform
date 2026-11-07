package com.example.bodeul.debug;

import android.os.Bundle;
import android.view.WindowManager;

import com.example.bodeul.data.GuardianBookingAccountSession;
import com.example.bodeul.data.GuardianBookingDraftRepository;
import com.example.bodeul.data.GuardianBookingRepository.CreateResult;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.domain.model.BookingPaymentMethod;
import com.example.bodeul.domain.model.BookingPriceSummary;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.domain.model.GuardianBookingApproval;
import com.example.bodeul.domain.model.GuardianBookingDraftReference;
import com.example.bodeul.domain.model.GuardianBookingRequest;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Participant;
import com.example.bodeul.domain.model.GuardianBookingStoredDraft.Summary;
import com.example.bodeul.ui.booking.GuardianBookingInboxActivity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 합성 메모리 저장소만 사용한다. 실제 로그인·원문·네트워크에는 접근하지 않는다. */
public final class GuardianBookingInboxPreviewActivity extends GuardianBookingInboxActivity {
    private final LocalSession session = new LocalSession();
    private final LocalRepository repository = new LocalRepository();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        // 실제 수신함은 캡처를 막으며 이 합성 전용 화면만 시각 검증에 사용한다.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    @Override protected boolean isInboxEnabled() { return true; }
    @Override protected GuardianBookingAccountSession createAccountSession() { return session; }
    @Override protected GuardianBookingDraftRepository createRepository() {
        repository.scenario = getIntent().getStringExtra("scenario");
        return repository;
    }

    public void simulateAccountChange() {
        session.id = "different-synthetic-account";
        if (session.changed != null) session.changed.run();
    }

    private static final class LocalSession implements GuardianBookingAccountSession {
        String id = "synthetic-account";
        Runnable changed;
        @Override public String currentAccountId() { return id; }
        @Override public void observe(Runnable changed) { this.changed = changed; }
        @Override public void stopObserving() { changed = null; }
    }

    private static final class LocalRepository implements GuardianBookingDraftRepository {
        final Instant now = Instant.now();
        final GuardianBookingRequest request = new GuardianBookingRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000002"),
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                BookingRequestDraft.builder().linkedParticipantName("합성 환자")
                        .linkedParticipantPhone("01000000000").linkedParticipantEmail("patient@example.com")
                        .hospitalName("합성 검증 병원").departmentName("내과")
                        .hospitalLatitude(37.5).hospitalLongitude(127).appointmentAt("2099-01-01 10:00")
                        .meetingPlace("1층 안내 데스크").patientConditionSummary("합성 상태 기록\n추가 확인사항")
                        .medicationSummary("합성 복약 기록").specialNotes("합성 화면 검증용 요청")
                        .paymentMethod(BookingPaymentMethod.BANK_TRANSFER)
                        .priceSummary(new BookingPriceSummary(40_000, 0, 0, 40_000)).build());
        final GuardianBookingDraftReference reference = GuardianBookingDraftReference.from(request);
        final Summary summary = new Summary(reference, now, now.plusSeconds(3600));
        GuardianBookingApproval approval = new GuardianBookingApproval(null, 0, null, null, null, false);
        String scenario;

        @Override public void listDrafts(RepositoryCallback<List<Summary>> callback) {
            if ("error".equals(scenario)) { callback.onError("합성 조회 실패"); return; }
            callback.onSuccess("empty".equals(scenario) ? List.of() : List.of(summary));
        }
        @Override public void loadDraft(GuardianBookingDraftReference reference, RepositoryCallback<GuardianBookingStoredDraft> callback) {
            if ("detail-error".equals(scenario)) { callback.onError("합성 상세 실패"); return; }
            callback.onSuccess(new GuardianBookingStoredDraft(summary, request,
                    new Participant("합성 환자", "***-****-0000"), new Participant("합성 보호자", "***-****-1234")));
        }
        @Override public void loadApproval(GuardianBookingDraftReference reference, RepositoryCallback<ApprovalStatus> callback) {
            boolean revocationOnly = "revoke-only".equals(scenario);
            if (revocationOnly && approval.getGrantId() == null) {
                approval = new GuardianBookingApproval(UUID.randomUUID(), 1, now, now.plusSeconds(3600), null, true);
            }
            callback.onSuccess(new ApprovalStatus(approval, !"guardian".equals(scenario), revocationOnly));
        }
        @Override public void grantDraft(GuardianBookingDraftReference reference, long version, boolean confirmed,
                RepositoryCallback<GuardianBookingApproval> callback) {
            if (!confirmed || version != approval.getVersion() || "guardian".equals(scenario)) {
                callback.onError("합성 승인 조건 불일치"); return;
            }
            approval = new GuardianBookingApproval(UUID.randomUUID(), version + 1, now, now.plusSeconds(3600), null, true);
            callback.onSuccess(approval);
        }
        @Override public void revokeDraft(GuardianBookingDraftReference reference, long version,
                RepositoryCallback<GuardianBookingApproval> callback) {
            if (!approval.canRevoke() || version != approval.getVersion()) { callback.onError("합성 철회 조건 불일치"); return; }
            approval = new GuardianBookingApproval(approval.getGrantId(), version + 1, approval.getGrantedAt(),
                    approval.getExpiresAt(), Instant.now(), false);
            callback.onSuccess(approval);
        }
        @Override public void submitDraft(GuardianBookingRequest request, RepositoryCallback<Summary> callback) {
            callback.onError("수신함에서는 원문을 만들지 않습니다.");
        }
        @Override public void createDraft(GuardianBookingDraftReference reference, GuardianBookingApproval approval,
                RepositoryCallback<CreateResult> callback) {
            callback.onError("수신함에서는 예약을 접수하지 않습니다.");
        }
    }
}
