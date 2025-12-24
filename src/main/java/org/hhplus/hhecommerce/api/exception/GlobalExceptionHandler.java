package org.hhplus.hhecommerce.api.exception;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            "ORDER_CONFLICT",           // 동시 주문 처리 충돌
            "ORDER_IN_PROGRESS",        // 이미 진행 중인 주문
            "LOCK_ACQUISITION_FAILED",  // 분산락 획득 실패
            "POINT_UPDATE_FAILED"       // 포인트 업데이트 실패 (낙관적 락)
    );

    private static final String DEFAULT_RETRY_AFTER_SECONDS = "2";

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = new ErrorResponse(errorCode.getCode(), e.getMessage());

        if (RETRYABLE_ERROR_CODES.contains(errorCode.getCode())) {
            log.info("재시도 가능한 에러 발생: code={}, message={}", errorCode.getCode(), e.getMessage());
            return ResponseEntity.status(errorCode.getHttpStatus())
                    .header("Retry-After", DEFAULT_RETRY_AFTER_SECONDS)
                    .header("X-Retry-Reason", errorCode.getCode())
                    .body(response);
        }

        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    @ExceptionHandler(ExhaustedRetryException.class)
    public ResponseEntity<ErrorResponse> handleExhaustedRetryException(ExhaustedRetryException e) {
        log.warn("재시도 중 예외 발생: {}", e.getMessage());

        Throwable cause = e.getCause();
        if (cause instanceof CustomException) {
            return handleBusinessException((CustomException) cause);
        }

        log.error("ExhaustedRetryException - cause: {}", cause != null ? cause.getClass().getName() : "null", e);
        ErrorResponse response = new ErrorResponse("RETRY_EXHAUSTED", "요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockException(OptimisticLockException e) {
        log.warn("낙관적 락 충돌 발생: {}", e.getMessage());
        ErrorResponse response = new ErrorResponse("OPTIMISTIC_LOCK_CONFLICT", "동시 요청으로 인한 충돌이 발생했습니다. 잠시 후 다시 시도해주세요.");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .header("Retry-After", DEFAULT_RETRY_AFTER_SECONDS)
                .header("X-Retry-Reason", "OPTIMISTIC_LOCK_CONFLICT")
                .body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약조건 위반: {}", e.getMessage());
        if (e.getMessage() != null && e.getMessage().contains("uk_user_coupon")) {
            ErrorResponse response = new ErrorResponse("COUPON_ALREADY_ISSUED", "이미 발급받은 쿠폰입니다.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        ErrorResponse response = new ErrorResponse("DATA_INTEGRITY_VIOLATION", "데이터 제약조건 위반");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorResponse response = new ErrorResponse("잘못된 요청 파라미터 형식입니다.");
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        ErrorResponse response = new ErrorResponse(e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        ErrorResponse response = new ErrorResponse(e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
        ErrorResponse response = new ErrorResponse(e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorResponse response = new ErrorResponse("서버 오류가 발생했습니다: " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
