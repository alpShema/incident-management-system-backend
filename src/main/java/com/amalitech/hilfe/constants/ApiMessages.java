package com.amalitech.hilfe.constants;

public final class ApiMessages {

    public static final String SEVERITY_NOT_FOUND = "The requested severity level was not found.";
    public static final String ROLE_REQUIRED = "A role must be selected.";
    public static final String ROLE_NAME_MAX_LENGTH = "Role name must not exceed 255 characters.";
    public static final String QUESTION_MAX_LENGTH = "Question must not exceed 500 characters.";
    public static final String PERMISSION_CODE_INVALID = "One or more selected permissions are invalid.";
    public static final String SESSION_EXPIRED = "Your session has expired. Please log in again.";
    public static final String SESSION_UNVERIFIABLE = "Your session could not be verified. Please log in again.";
    public static final String AUTHENTICATION_REQUIRED = "Authentication required. Please log in to access this resource.";
    public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Please wait before sending another query.";

    private ApiMessages() {
    }
}
