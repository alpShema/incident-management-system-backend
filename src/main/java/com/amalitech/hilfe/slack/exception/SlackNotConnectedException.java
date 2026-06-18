package com.amalitech.hilfe.slack.exception;

public class SlackNotConnectedException extends RuntimeException {

    public SlackNotConnectedException() {
        super("Slack account is not connected. Please connect your account first.");
    }

    public SlackNotConnectedException(String message) {
        super(message);
    }
}
