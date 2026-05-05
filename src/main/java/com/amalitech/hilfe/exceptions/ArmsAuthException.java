package com.amalitech.hilfe.exceptions;

public class ArmsAuthException extends RuntimeException {
    private final int httpStatus;

    public ArmsAuthException(String message) {
        super(message);
        this.httpStatus = 502;
    }

    public ArmsAuthException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public ArmsAuthException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public ArmsAuthException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 502;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
