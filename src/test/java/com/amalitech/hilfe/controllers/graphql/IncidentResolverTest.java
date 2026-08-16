package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Guards against a reported gap: GraphQL's schema-level `String!` (non-null) only rejects
// null, not blank — @Valid on the resolver argument is what wires up @NotBlank/@Size from
// CreateIncidentRequest, mirroring the REST endpoint's @Valid @RequestBody.
@GraphQlTest(IncidentResolver.class)
@Import({IncidentResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class IncidentResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean IncidentService incidentService;
    @MockitoBean ActivityLogService activityLogService;

    private static final String MUTATION = """
            mutation($title: String!, $description: String!) {
              createIncident(input: {
                title: $title,
                description: $description,
                incidentTypeId: "type-1",
                locationId: "loc-1"
              }) {
                id
              }
            }
            """;

    @BeforeEach
    void bindRequestContext() {
        // IncidentResolver.createIncident calls GraphQlResponseMessage.set(...), which needs a
        // thread-bound request attribute holder that this non-web GraphQlTest slice doesn't
        // provide by default.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setAuthority(String authority) {
        var principal = new JwtTokenService.AuthPrincipal("user-1", "user@test.com", RoleCode.CLIENT);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private IncidentResponse stubResponse() {
        return new IncidentResponse("incident-1", 1, "", "", null, null, null, null,
                null, null, false, null, null, null, null, null, null, null);
    }

    @Test
    void createIncident_blankTitleAndDescription_isRejectedByValidation() {
        setAuthority("incident.create");

        graphQlTester.document(MUTATION)
                .variable("title", "")
                .variable("description", "   ")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(incidentService, never()).createIncident(any(), any());
    }

    @Test
    void createIncident_validTitleAndDescription_succeeds() {
        setAuthority("incident.create");
        when(incidentService.createIncident(eq("user-1"), any(CreateIncidentRequest.class)))
                .thenReturn(stubResponse());

        graphQlTester.document(MUTATION)
                .variable("title", "Projector not working")
                .variable("description", "The projector in Room 3B won't power on.")
                .execute()
                .path("createIncident.id").entity(String.class).isEqualTo("incident-1");

        verify(incidentService).createIncident(eq("user-1"), any(CreateIncidentRequest.class));
    }
}
