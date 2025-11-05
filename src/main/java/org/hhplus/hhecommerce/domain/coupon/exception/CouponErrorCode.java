package org.hhplus.hhecommerce.domain.coupon.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CouponErrorCode implements ErrorCode {
    COUPON_NOT_FOUND("쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_COUPON_NOT_FOUND("사용자 쿠폰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    COUPON_OUT_OF_STOCK("쿠폰 재고가 부족합니다.", HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_ISSUED("이미 발급받은 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    COUPON_NOT_AVAILABLE("사용 가능한 쿠폰이 아닙니다.", HttpStatus.BAD_REQUEST),
    COUPON_EXPIRED("만료된 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    COUPON_ALREADY_USED("이미 사용된 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    COUPON_UNAVAILABLE("사용 불가능한 쿠폰입니다.", HttpStatus.BAD_REQUEST),
    MIN_ORDER_AMOUNT_NOT_MET("최소 주문 금액을 충족하지 않습니다.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;

    CouponErrorCode(String message, HttpStatus httpStatus) {
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
