package com.example.bodeul.debug;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.example.bodeul.data.AuthRepository;
import com.example.bodeul.data.BookingRepository;
import com.example.bodeul.domain.model.AppointmentRequest;
import com.example.bodeul.domain.model.BookingRequestDraft;
import com.example.bodeul.ui.booking.BookingActivity;

/** 선택기 결과와 저장 응답을 지연시키는 계측 테스트 전용 진입점이다. */
public final class BookingLifecyclePreviewActivity extends BookingActivity {
    static AuthRepository testAuth;
    static BookingRepository testBooking;
    static int selectionRequestCode;
    int paymentLaunchCount;
    int completionCount;
    boolean completedUpdate;

    @Override protected AuthRepository provideAuthRepository() {
        if (testAuth == null) throw new IllegalStateException("테스트 인증 저장소가 필요합니다.");
        return testAuth;
    }

    @Override protected BookingRepository provideBookingRepository() {
        if (testBooking == null) throw new IllegalStateException("테스트 예약 저장소가 필요합니다.");
        return testBooking;
    }

    @Override public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        // 실제 ActivityResultRegistry의 실행·복원 경로만 사용하고 외부 선택기는 열지 않는다.
        if (intent.getComponent() != null && intent.getComponent().getClassName().startsWith(
                "com.example.bodeul.ui.booking.Booking")) {
            selectionRequestCode = requestCode;
            return;
        }
        throw new IllegalStateException("테스트에서 외부 화면을 열 수 없습니다.");
    }

    @Override protected void openPaymentApproval(BookingRequestDraft draft) { paymentLaunchCount++; }

    void approvePayment() {
        handlePaymentApprovalResult(RESULT_OK, new Intent()
                .putExtra("paymentStatus", "SIMULATED").putExtra("paymentProvider", "합성 결제 확인"));
    }

    @Override protected void openBookingCompletion(AppointmentRequest request, boolean isUpdated) {
        completionCount++;
        completedUpdate = isUpdated;
    }
}
