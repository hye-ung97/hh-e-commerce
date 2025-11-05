package org.hhplus.hhecommerce.domain.product.exception;

import org.hhplus.hhecommerce.api.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {
    PRODUCT_NOT_FOUND("상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PRODUCT_OPTION_NOT_FOUND("상품 옵션을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INSUFFICIENT_STOCK("재고가 부족합니다.", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY("수량은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_DEDUCT_QUANTITY("차감 수량은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST),
    INVALID_RESTORE_QUANTITY("복원 수량은 0보다 커야 합니다.", HttpStatus.BAD_REQUEST);

    private final String message;
    private final HttpStatus httpStatus;

    ProductErrorCode(String message, HttpStatus httpStatus) {
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
