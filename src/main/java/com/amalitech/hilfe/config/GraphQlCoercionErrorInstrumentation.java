package com.amalitech.hilfe.config;

import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.validation.ValidationError;
import graphql.validation.ValidationErrorType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitizes GraphQL scalar-coercion failures (e.g. a malformed {@code LocalTime}/{@code Instant}
 * literal or variable) before they reach the client.
 *
 * <p>These failures never reach {@link GraphQlExceptionResolver}: a bad inline literal is rejected
 * during query validation (as a {@link ValidationError}) and a bad variable value is rejected
 * during variable coercion (as a raw {@link CoercingParseValueException}/
 * {@link CoercingParseLiteralException}) -- both happen before any {@code DataFetcher} runs, so
 * {@code DataFetcherExceptionResolverAdapter}-based handling can never see them. An
 * {@link graphql.execution.instrumentation.Instrumentation} is the one hook that wraps the whole
 * execution, including validation, so it's the only place these can be intercepted. Spring Boot's
 * GraphQL autoconfiguration picks up every {@code Instrumentation} bean automatically.
 *
 * <p>Left untouched, the raw message exposes the internal Java/GraphQL type name, the
 * {@code WrongType} exception classification, the exact argument path, and the underlying
 * date-parsing library's error text (HV-1648).
 */
@Slf4j
@Component
public class GraphQlCoercionErrorInstrumentation extends SimplePerformantInstrumentation {

    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("argument '([^']+)'");
    private static final Pattern SCALAR_TYPE_PATTERN = Pattern.compile("is not a valid '([^']+)'");
    private static final String GENERIC_MESSAGE = "One or more input values are invalid or incorrectly formatted";

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        List<GraphQLError> errors = executionResult.getErrors();
        if (errors.isEmpty()) {
            return CompletableFuture.completedFuture(executionResult);
        }

        List<GraphQLError> sanitized = new ArrayList<>(errors.size());
        boolean changed = false;
        for (GraphQLError error : errors) {
            GraphQLError replacement = sanitizeCoercionError(error);
            sanitized.add(replacement != null ? replacement : error);
            changed |= replacement != null;
        }
        if (!changed) {
            return CompletableFuture.completedFuture(executionResult);
        }
        return CompletableFuture.completedFuture(executionResult.transform(builder -> builder.errors(sanitized)));
    }

    private GraphQLError sanitizeCoercionError(GraphQLError error) {
        boolean isWrongType = error instanceof ValidationError ve && ve.getValidationErrorType() == ValidationErrorType.WrongType;
        boolean isRawCoercionError = error instanceof CoercingParseValueException || error instanceof CoercingParseLiteralException;
        if (!isWrongType && !isRawCoercionError) {
            return null;
        }
        log.warn("Sanitized GraphQL argument coercion error: {}", error.getMessage());
        String message = buildCleanMessage(error.getMessage());
        return GraphqlErrorBuilder.newError()
                .message(message)
                .errorType(ErrorType.BAD_REQUEST)
                .locations(error.getLocations())
                .path(error.getPath())
                .extensions(extensions(message))
                .build();
    }

    private String buildCleanMessage(String rawMessage) {
        if (rawMessage == null) {
            return GENERIC_MESSAGE;
        }
        Matcher argumentMatcher = ARGUMENT_PATTERN.matcher(rawMessage);
        if (!argumentMatcher.find()) {
            return GENERIC_MESSAGE;
        }
        String argumentPath = argumentMatcher.group(1);
        String fieldName = argumentPath.contains(".")
                ? argumentPath.substring(argumentPath.lastIndexOf('.') + 1)
                : argumentPath;

        Matcher typeMatcher = SCALAR_TYPE_PATTERN.matcher(rawMessage);
        return typeMatcher.find()
                ? fieldName + " must be a valid " + typeMatcher.group(1) + " value"
                : fieldName + " has an invalid or incorrectly formatted value";
    }

    private Map<String, Object> extensions(String message) {
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("timestamp", Instant.now().toString());
        ext.put("status", 400);
        ext.put("error", "Bad Request");
        ext.put("message", message);
        return ext;
    }
}
