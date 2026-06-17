package com.amalitech.hilfe.slack.exception;

import java.util.List;
import java.util.Map;

public class SlackValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public SlackValidationException(List<String> errors) {
        super("Validation failed: " + String.join(", ", errors));
        this.errors = Map.of("general", String.join(", ", errors));
    }

    public SlackValidationException(Map<String, String> errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
