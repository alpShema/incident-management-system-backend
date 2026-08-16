package com.amalitech.hilfe.config;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.CoercingParseValueException;
import graphql.schema.DataFetchingEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphQlExceptionResolverTest {

    private final GraphQlExceptionResolver resolver = new GraphQlExceptionResolver();

    @Mock DataFetchingEnvironment env;
    @Mock ExecutionStepInfo executionStepInfo;
    @Mock Field field;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void stubEnvironmentPath(ResultPath path) {
        lenient().when(env.getExecutionStepInfo()).thenReturn(executionStepInfo);
        lenient().when(executionStepInfo.getPath()).thenReturn(path);
        lenient().when(env.getField()).thenReturn(field);
        lenient().when(field.getSourceLocation()).thenReturn(null);
    }

    private void stubEnvironmentPath(String... segments) {
        ResultPath path = ResultPath.rootPath();
        for (String segment : segments) {
            path = path.segment(segment);
        }
        stubEnvironmentPath(path);
    }

    private void authenticateAsNonAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_ADMIN"));
    }

    @Test
    void resolveException_armsAuthException_populatesPathFromEnvironment() {
        stubEnvironmentPath("createIncident");

        GraphQLError error = resolver.resolveException(
                new ArmsAuthException("Location with the provided ID could not be found.", 404), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("createIncident"));
        assertThat(error.getMessage()).isEqualTo("Location with the provided ID could not be found.");
    }

    @Test
    void resolveException_illegalArgumentException_populatesPathFromEnvironment() {
        stubEnvironmentPath("updateAgentGroup");

        GraphQLError error = resolver.resolveException(
                new IllegalArgumentException("Bad input"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("updateAgentGroup"));
        assertThat(error.getMessage()).isEqualTo("Bad input");
    }

    @Test
    void resolveException_unhandledException_populatesPathFromEnvironment() {
        stubEnvironmentPath("createIncident");

        GraphQLError error = resolver.resolveException(new RuntimeException("boom"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("createIncident"));
        assertThat(error.getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void resolveException_accessDeniedException_authenticatedUser_populatesNestedPath() {
        authenticateAsNonAnonymous();
        stubEnvironmentPath(ResultPath.rootPath().segment("incident").segment("assignedAgent"));

        GraphQLError error = resolver.resolveException(new AccessDeniedException("denied"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("incident", "assignedAgent"));
        assertThat(error.getMessage()).isEqualTo("Access denied");
    }

    @Test
    void resolveException_accessDeniedException_anonymousUser_populatesPath() {
        stubEnvironmentPath("incidents");

        GraphQLError error = resolver.resolveException(new AccessDeniedException("denied"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("incidents"));
        assertThat(error.getMessage()).isEqualTo("Authentication required. Please log in to access this resource.");
    }

    @Test
    void resolveException_nullPointerException_authenticatedUser_populatesPath() {
        authenticateAsNonAnonymous();
        stubEnvironmentPath("myIncidents");

        GraphQLError error = resolver.resolveException(new NullPointerException("npe"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("myIncidents"));
        assertThat(error.getMessage()).isEqualTo("An unexpected error occurred");
    }

    @Test
    void resolveException_coercingParseValueException_populatesPathWithListIndex() {
        stubEnvironmentPath(ResultPath.rootPath().segment("incidents").segment(2).segment("severity"));

        GraphQLError error = resolver.resolveException(
                new CoercingParseValueException("bad enum value"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("incidents", 2, "severity"));
        assertThat(error.getMessage()).isEqualTo("Invalid argument value: bad enum value");
    }

    @Test
    void resolveException_dataIntegrityViolationException_duplicateKey_populatesPath() {
        stubEnvironmentPath("createAgentGroup");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "constraint violated", new RuntimeException("duplicate key value violates unique constraint"));

        GraphQLError error = resolver.resolveException(dive, env).block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("createAgentGroup"));
        assertThat(error.getMessage()).isEqualTo("A record with this value already exists");
    }

    @Test
    void resolveException_serviceUnavailableException_populatesPath() {
        stubEnvironmentPath("chatbotReply");

        GraphQLError error = resolver.resolveException(
                new ServiceUnavailableException("LLM service is temporarily unavailable"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("chatbotReply"));
        assertThat(error.getMessage()).isEqualTo("LLM service is temporarily unavailable");
    }

    @Test
    void resolveException_unsupportedOperationException_populatesPath() {
        stubEnvironmentPath("archiveIncident");

        GraphQLError error = resolver.resolveException(
                new UnsupportedOperationException("Not yet implemented"), env)
                .block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("archiveIncident"));
        assertThat(error.getMessage()).isEqualTo("Not yet implemented");
    }

    @Test
    void resolveException_propertyReferenceException_populatesPathAndFieldName() {
        stubEnvironmentPath("departments");
        PropertyReferenceException pre = mock(PropertyReferenceException.class);
        when(pre.getPropertyName()).thenReturn("bogusField");

        GraphQLError error = resolver.resolveException(pre, env).block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("departments"));
        assertThat(error.getMessage()).isEqualTo("Invalid sort field: bogusField");
    }

    @Test
    void resolveException_invalidDataAccessApiUsageException_extractsUnknownSortField() {
        stubEnvironmentPath("departments");
        InvalidDataAccessApiUsageException idaue = new InvalidDataAccessApiUsageException(
                "translated",
                new RuntimeException(
                        "Could not resolve attribute 'bogusField' of 'com.amalitech.hilfe.models.Department'"));

        GraphQLError error = resolver.resolveException(idaue, env).block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("departments"));
        assertThat(error.getMessage()).isEqualTo("Invalid sort field: bogusField");
    }

    @Test
    void resolveException_invalidDataAccessApiUsageException_withoutUnknownAttribute_fallsBackToGenericMessage() {
        stubEnvironmentPath("departments");
        InvalidDataAccessApiUsageException idaue = new InvalidDataAccessApiUsageException(
                "translated", new RuntimeException("some other JPA failure"));

        GraphQLError error = resolver.resolveException(idaue, env).block().get(0);

        assertThat(error.getPath()).isEqualTo(List.of("departments"));
        assertThat(error.getMessage()).isEqualTo("Invalid query argument");
    }
}
