package org.hhplus.hhecommerce.infrastructure.external;

import lombok.Getter;

@Getter
public class ExternalApiException extends RuntimeException {

    private final String errorCode;
    private final Integer statusCode;

    public ExternalApiException(String message) {
        super(message);
        this.errorCode = "EXTERNAL_API_ERROR";
        this.statusCode = null;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "EXTERNAL_API_ERROR";
        this.statusCode = null;
    }

    public ExternalApiException(String message, String errorCode, Integer statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    public ExternalApiException(String message, Throwable cause, String errorCode, Integer statusCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.statusCode = statusCode;
    }

    @Override
    public String toString() {
        return String.format("ExternalApiException{errorCode='%s', statusCode=%s, message='%s'}",
                errorCode, statusCode, getMessage());
    }
}
