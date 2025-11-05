package org.hhplus.hhecommerce.domain.order.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class OrderException extends CustomException {
    public OrderException(ErrorCode errorCode) {
        super(errorCode);
    }

    public OrderException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
