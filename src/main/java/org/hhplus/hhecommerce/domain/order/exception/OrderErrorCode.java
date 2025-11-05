package org.hhplus.hhecommerce.domain.order.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OrderErrorCode implements ErrorCode {
    ORDER_NOT_FOUND("주문을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_ORDER_ITEM("주문 항목이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    EMPTY_ORDER_ITEMS("주문 항목이 비어있습니다.", HttpStatus.BAD_REQUEST),
    EMPTY_CART("장바구니가 비어있습니다.", HttpStatus.BAD_REQUEST);

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
