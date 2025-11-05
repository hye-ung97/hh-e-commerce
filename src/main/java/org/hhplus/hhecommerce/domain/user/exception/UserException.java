package org.hhplus.hhecommerce.domain.user.exception;

import org.hhplus.hhecommerce.api.exception.CustomException;
import org.hhplus.hhecommerce.api.exception.ErrorCode;

public class UserException extends CustomException {
    public UserException(ErrorCode errorCode) {
        super(errorCode);
    }

    public UserException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }
}
