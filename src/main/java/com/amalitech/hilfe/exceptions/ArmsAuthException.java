package com.amalitech.hilfe.exceptions;

public class ArmsAuthException extends RuntimeException {
    public ArmsAuthException(String message) {
        super(message);
    }
    public ArmsAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}

