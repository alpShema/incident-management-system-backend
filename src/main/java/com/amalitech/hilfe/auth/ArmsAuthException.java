package com.amalitech.hilfe.auth;

import lombok.Getter;

@Getter
public class ArmsAuthException extends RuntimeException {

    private final int httpStatus;

    public ArmsAuthException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }
}
