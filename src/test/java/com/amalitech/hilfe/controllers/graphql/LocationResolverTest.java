package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.services.LocationService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Spot-checks the same @Valid fix applied to IncidentResolver: confirms it also closes the
// GraphQL validation-bypass gap on a third, independent resolver (CreateLocationRequest.name
// is @NotBlank).
@GraphQlTest(LocationResolver.class)
@Import({LocationResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class LocationResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean LocationService locationService;

    private static final String MUTATION = """
            mutation($name: String!) {
              createLocation(input: { name: $name }) {
                id
              }
            }
            """;

    @BeforeEach
    void bindRequestContext() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void setAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createLocation_blankName_isRejectedByValidation() {
        setAuthority("location.create");

        graphQlTester.document(MUTATION)
                .variable("name", "")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(locationService, never()).createLocation(any());
    }
}
