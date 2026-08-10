package com.amalitech.hilfe.config;

import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Pattern UNKNOWN_ATTRIBUTE_PATTERN =
            Pattern.compile("Could not resolve attribute '(.+?)' of");
    private static final String BAD_REQUEST = "Bad Request";

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ArmsAuthException ae) {
            return handleArmsAuthError(ae, env);
        }

        if (ex instanceof ConstraintViolationException cve) {
            return handleConstraintViolationError(cve, env);
        }

        if (ex instanceof AuthorizationDeniedException || ex instanceof AccessDeniedException) {
            return handleAuthorizationError(ex, env);
        }

        if (ex instanceof NullPointerException npe) {
            return handleNullPointerError(npe, env);
        }

        if (ex instanceof CoercingParseValueException || ex instanceof CoercingParseLiteralException) {
            return handleCoercionError(ex, env);
        }

        if (ex instanceof IllegalArgumentException iae) {
            return handleIllegalArgumentError(iae, env);
        }

        if (ex instanceof PropertyReferenceException pre) {
            return handlePropertyReferenceError(pre, env);
        }

        if (ex instanceof InvalidDataAccessApiUsageException idaue) {
            return handleInvalidDataAccessError(idaue, env);
        }

        if (ex instanceof DataIntegrityViolationException dive) {
            return handleDataIntegrityError(dive, env);
        }

        if (ex instanceof ServiceUnavailableException sue) {
            return handleServiceUnavailableError(sue, env);
        }

        if (ex instanceof UnsupportedOperationException uoe) {
            return handleUnsupportedOperationError(uoe, env);
        }

        log.error("GraphQL unhandled exception", ex);
        return buildError(env, "An unexpected error occurred", 500, "Internal Server Error");
    }

    private GraphQLError handleArmsAuthError(ArmsAuthException ae, DataFetchingEnvironment env) {
        log.warn("GraphQL ARMS auth error: {}", ae.getMessage());
        HttpStatus status = HttpStatus.resolve(ae.getHttpStatus());
        String error = status != null ? status.getReasonPhrase() : "Bad Gateway";
        return buildError(env, ae.getMessage(), ae.getHttpStatus(), error);
    }

    private GraphQLError handleConstraintViolationError(ConstraintViolationException cve, DataFetchingEnvironment env) {
        String message = cve.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(java.util.stream.Collectors.joining(" "));
        log.warn("GraphQL constraint violation: {}", message);
        return buildError(env, message, 400, BAD_REQUEST);
    }

    private GraphQLError handleAuthorizationError(Throwable ex, DataFetchingEnvironment env) {
        if (isAnonymous()) {
            log.warn("GraphQL unauthenticated access attempt");
            return buildError(env, ApiMessages.AUTHENTICATION_REQUIRED, 401, "Unauthorized");
        }
        log.warn("GraphQL access denied: {}", ex.getMessage());
        return buildError(env, "Access denied", 403, "Forbidden");
    }

    private GraphQLError handleNullPointerError(NullPointerException npe, DataFetchingEnvironment env) {
        if (isAnonymous()) {
            log.warn("GraphQL unauthenticated request — @AuthenticationPrincipal was null");
            return buildError(env, ApiMessages.AUTHENTICATION_REQUIRED, 401, "Unauthorized");
        }
        log.warn("GraphQL null pointer: {}", npe.getMessage());
        return buildError(env, "An unexpected error occurred", 500, "Internal Server Error");
    }

    private GraphQLError handleCoercionError(Throwable ex, DataFetchingEnvironment env) {
        log.warn("GraphQL argument coercion error: {}", ex.getMessage());
        return buildError(env, "Invalid argument value: " + ex.getMessage(), 400, BAD_REQUEST);
    }

    private GraphQLError handleIllegalArgumentError(IllegalArgumentException iae, DataFetchingEnvironment env) {
        log.warn("GraphQL illegal argument: {}", iae.getMessage());
        return buildError(env, iae.getMessage(), 400, BAD_REQUEST);
    }

    private GraphQLError handlePropertyReferenceError(PropertyReferenceException pre, DataFetchingEnvironment env) {
        log.warn("GraphQL invalid sort field: {}", pre.getMessage());
        return buildError(env, "Invalid sort field: " + pre.getPropertyName(), 400, BAD_REQUEST);
    }

    private GraphQLError handleInvalidDataAccessError(InvalidDataAccessApiUsageException idaue, DataFetchingEnvironment env) {
        log.warn("GraphQL invalid data access usage: {}", idaue.getMessage());
        String rootMessage = idaue.getMostSpecificCause().getMessage();
        java.util.regex.Matcher matcher = UNKNOWN_ATTRIBUTE_PATTERN.matcher(rootMessage != null ? rootMessage : "");
        String message = matcher.find()
                ? "Invalid sort field: " + matcher.group(1)
                : "Invalid query argument";
        return buildError(env, message, 400, BAD_REQUEST);
    }

    private GraphQLError handleDataIntegrityError(DataIntegrityViolationException dive, DataFetchingEnvironment env) {
        log.warn("GraphQL data integrity violation: {}",
                dive.getCause() != null ? dive.getCause().getMessage() : dive.getMessage());
        String message = isDuplicateKeyViolation(dive)
                ? "A record with this value already exists"
                : "Data integrity constraint violated";
        return buildError(env, message, 409, "Conflict");
    }

    private GraphQLError handleServiceUnavailableError(ServiceUnavailableException sue, DataFetchingEnvironment env) {
        log.error("GraphQL service unavailable: {}", sue.getMessage());
        return buildError(env, sue.getMessage(), 503, "Service Unavailable");
    }

    private GraphQLError handleUnsupportedOperationError(UnsupportedOperationException uoe, DataFetchingEnvironment env) {
        log.warn("GraphQL not implemented: {}", uoe.getMessage());
        return buildError(env, uoe.getMessage(), 501, "Not Implemented");
    }

    private boolean isAnonymous() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null
                || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken;
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException dive) {
        return dive.getCause() != null
                && dive.getCause().getMessage() != null
                && dive.getCause().getMessage().contains("duplicate key");
    }

    private GraphQLError buildError(DataFetchingEnvironment env, String message, int status, String error) {
        return GraphqlErrorBuilder.newError(env)
                .message(message)
                .errorType(statusToErrorType(status))
                .extensions(extensions(message, status, error))
                .build();
    }

    private java.util.Map<String, Object> extensions(String message, int status, String error) {
        java.util.Map<String, Object> ext = new java.util.LinkedHashMap<>();
        ext.put("timestamp", Instant.now().toString());
        ext.put("status", status);
        ext.put("error", error);
        ext.put("message", message != null ? message : error);
        return ext;
    }

    private static ErrorType statusToErrorType(int status) {
        return switch (status) {
            case 400 -> ErrorType.BAD_REQUEST;
            case 401 -> ErrorType.UNAUTHORIZED;
            case 403 -> ErrorType.FORBIDDEN;
            case 404 -> ErrorType.NOT_FOUND;
            case 409 -> ErrorType.BAD_REQUEST;
            case 422 -> ErrorType.BAD_REQUEST;
            case 429 -> ErrorType.BAD_REQUEST;
            case 500 -> ErrorType.INTERNAL_ERROR;
            case 501 -> ErrorType.INTERNAL_ERROR;
            case 502 -> ErrorType.INTERNAL_ERROR;
            case 503 -> ErrorType.INTERNAL_ERROR;
            default -> ErrorType.INTERNAL_ERROR;
        };
    }
}
