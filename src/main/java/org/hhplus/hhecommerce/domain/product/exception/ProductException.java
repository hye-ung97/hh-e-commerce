package org.hhplus.hhecommerce.domain.product.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class ProductException extends CustomException {

    public ProductException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ProductException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
