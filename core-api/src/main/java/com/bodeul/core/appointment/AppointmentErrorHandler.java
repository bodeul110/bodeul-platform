package com.bodeul.core.appointment;

import com.bodeul.core.consent.GuardianBookingException;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
    AppointmentController.class,
    AppointmentPaymentController.class,
    GuardianBookingController.class
})
@Profile({"database", "appointment-test", "guardian-booking-test"})
class AppointmentErrorHandler {

    @ExceptionHandler(GuardianBookingException.class)
    ResponseEntity<ApiError> handleGuardianBookingException(GuardianBookingException exception) {
        return ResponseEntity.status(exception.status()).cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.error(), exception.getMessage()));
    }

    @ExceptionHandler(AppointmentException.class)
    ResponseEntity<ApiError> handleAppointmentException(AppointmentException exception) {
        return ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.error(), exception.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        "invalid_appointment_request",
                        "예약 요청 형식을 확인해 주세요."));
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ApiError> handleDatabaseFailure(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError(
                        "appointment_database_failure",
                        "예약 정보를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
    }

    private record ApiError(String error, String message) {
    }
}
