package org.hhplus.hhecommerce.domain.point.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class PointException extends CustomException {

    public PointException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PointException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
