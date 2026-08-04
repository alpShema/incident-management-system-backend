package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.services.DepartmentService;
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
// GraphQL validation-bypass gap on a second, independent resolver (DepartmentRequest.name
// is @NotBlank).
@GraphQlTest(DepartmentResolver.class)
@Import({DepartmentResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class DepartmentResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean DepartmentService departmentService;

    private static final String MUTATION = """
            mutation($name: String!, $headUserId: ID!) {
              createDepartment(input: { name: $name, headUserId: $headUserId }) {
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
    void createDepartment_blankName_isRejectedByValidation() {
        setAuthority("department.create");

        graphQlTester.document(MUTATION)
                .variable("name", "")
                .variable("headUserId", "admin-1")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(departmentService, never()).createDepartment(any(), any());
    }
}
