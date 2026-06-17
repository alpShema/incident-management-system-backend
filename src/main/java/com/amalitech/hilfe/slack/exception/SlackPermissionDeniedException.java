package com.amalitech.hilfe.slack.exception;

public class SlackPermissionDeniedException extends RuntimeException {

    public SlackPermissionDeniedException(String message) {
        super(message);
    }
}
