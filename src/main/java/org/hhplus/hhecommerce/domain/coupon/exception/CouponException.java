package org.hhplus.hhecommerce.domain.coupon.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class CouponException extends CustomException {
    public CouponException(ErrorCode errorCode) {
        super(errorCode);
    }

    public CouponException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
