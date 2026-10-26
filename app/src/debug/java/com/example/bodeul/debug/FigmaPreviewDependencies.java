package com.example.bodeul.debug;

import android.content.Context;

import com.example.bodeul.R;
import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.data.GuardianReportRepository;
import com.example.bodeul.data.MockBodeulRepository;
import com.example.bodeul.data.RepositoryCallback;
import com.example.bodeul.data.mock.MockAuthRepository;
import com.example.bodeul.data.mock.MockBookingRepository;
import com.example.bodeul.data.mock.MockGuardianReportRepository;
import com.example.bodeul.domain.model.MedicationComparisonDecision;
import com.example.bodeul.domain.model.User;
import com.example.bodeul.domain.model.UserRole;

/** 피그마 화면 미리보기에 실제 서버 대신 로컬 데이터를 주입한다. */
final class FigmaPreviewDependencies {
    final AuthRepository authRepository;
    final BookingRepository bookingRepository;
    final GuardianReportRepository guardianReportRepository;

    private FigmaPreviewDependencies(
            AuthRepository authRepository,
            BookingRepository bookingRepository,
            GuardianReportRepository guardianReportRepository
    ) {
        this.authRepository = authRepository;
        this.bookingRepository = bookingRepository;
        this.guardianReportRepository = guardianReportRepository;
    }

    static FigmaPreviewDependencies forBooking(Context context) {
        MockBodeulRepository dataRepository = new MockBodeulRepository();
        MockAuthRepository authRepository = signedInAuth(
                context,
                dataRepository,
                R.string.demo_account_patient_email,
                UserRole.PATIENT
        );
        return new FigmaPreviewDependencies(
                authRepository,
                new MockBookingRepository(dataRepository),
                new MockGuardianReportRepository(dataRepository)
        );
    }

    static FigmaPreviewDependencies forGuardianReport(Context context) {
        MockBodeulRepository dataRepository = new MockBodeulRepository();
        dataRepository.updateFieldPhotoNote(
                "manager-1",
                "혈압은 안정적으로 유지됐고 진료실에서 기존 혈압약 성분을 소폭 조정했습니다."
        );
        dataRepository.updateMedicationNote(
                "manager-1",
                "총 28일분 약을 수령했습니다. 아침 식사 후 30분에 복용하도록 안내했습니다."
        );
        dataRepository.updatePharmacySummary(
                "manager-1",
                "병원 인근 약국에서 처방약 수령과 복약 안내를 마쳤습니다."
        );
        dataRepository.saveSessionReport(
                "manager-1",
                "오늘 동행을 안전하게 마쳤습니다.",
                "진료 결과와 다음 방문 일정을 보호자에게 전달했습니다.",
                "처방약 수령 및 복약 안내를 확인했습니다.",
                "혈압약",
                "기존 처방 성분과 복용 시간을 조정했습니다.",
                "아침 식사 후 30분에 복용",
                MedicationComparisonDecision.CHANGED,
                "변경된 복용법을 환자와 보호자에게 함께 안내했습니다.",
                "2026-12-22 10:30"
        );

        MockAuthRepository authRepository = signedInAuth(
                context,
                dataRepository,
                R.string.demo_account_guardian_email,
                UserRole.GUARDIAN
        );
        return new FigmaPreviewDependencies(
                authRepository,
                new MockBookingRepository(dataRepository),
                new MockGuardianReportRepository(dataRepository)
        );
    }

    private static MockAuthRepository signedInAuth(
            Context context,
            MockBodeulRepository dataRepository,
            int emailResId,
            UserRole role
    ) {
        MockAuthRepository authRepository = new MockAuthRepository(dataRepository);
        authRepository.signIn(
                context.getString(emailResId),
                context.getString(R.string.demo_account_password),
                role,
                new RepositoryCallback<User>() {
                    @Override
                    public void onSuccess(User result) {
                        // 동기식 로컬 저장소에 미리보기 사용자를 캐시한다.
                    }

                    @Override
                    public void onError(String message) {
                        throw new IllegalStateException(message);
                    }
                }
        );
        return authRepository;
    }
}
