package org.hhplus.hhecommerce.domain.cart.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class CartException extends CustomException {
    public CartException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CartException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
