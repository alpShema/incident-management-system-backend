package com.amalitech.hilfe.slack.exception;

public class SlackClientException extends RuntimeException {

    public SlackClientException(String message) {
        super(message);
    }

    public SlackClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
