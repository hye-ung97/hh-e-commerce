package org.hhplus.hhecommerce.domain.cart.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CartErrorCode implements ErrorCode {
    CART_ITEM_NOT_FOUND("장바구니 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String message;
    private final HttpStatus httpStatus;

    CartErrorCode(String message, HttpStatus httpStatus) {
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
