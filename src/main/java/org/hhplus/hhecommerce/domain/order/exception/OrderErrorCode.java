package org.hhplus.hhecommerce.domain.order.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_ORDER_ITEM("주문 항목이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    EMPTY_ORDER_ITEMS("주문 항목이 비어있습니다.", HttpStatus.BAD_REQUEST),
    EMPTY_CART("장바구니가 비어있습니다.", HttpStatus.BAD_REQUEST),
    ORDER_IN_PROGRESS("이미 진행 중인 주문이 있습니다.", HttpStatus.CONFLICT),
    ORDER_CONFLICT("동시 주문 처리 중 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    ORDER_FAILED("주문 처리에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String message;
    private final HttpStatus httpStatus;

    OrderErrorCode(String message, HttpStatus httpStatus) {
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
