package org.hhplus.hhecommerce.domain.point.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PointErrorCode implements ErrorCode {
    INVALID_AMOUNT("금액은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    POINT_NOT_FOUND("포인트 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_CHARGE_UNIT("충전 금액은 100원 단위로만 가능합니다.", HttpStatus.BAD_REQUEST),
    EXCEED_MAX_BALANCE("최대 보유 포인트는 100,000원입니다.", HttpStatus.BAD_REQUEST),
    INVALID_USE_UNIT("사용 금액은 100원 단위로만 가능합니다.", HttpStatus.BAD_REQUEST),
    BELOW_MIN_USE_AMOUNT("최소 사용 금액은 1,000원입니다.", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE("포인트 잔액이 부족합니다.", HttpStatus.BAD_REQUEST),
    POINT_UPDATE_FAILED("포인트 업데이트에 실패했습니다. 다시 시도해주세요.", HttpStatus.CONFLICT);


    private final String message;
    private final HttpStatus httpStatus;

    PointErrorCode(String message, HttpStatus httpStatus) {
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return this.name();
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
