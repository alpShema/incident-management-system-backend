package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.DepartmentService;
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

    private static final String UPDATE_MUTATION = """
            mutation($id: ID!, $description: String) {
              updateDepartment(id: $id, input: { description: $description }) {
                id
                description
              }
            }
            """;

    private void setAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setAuthorityWithPrincipal(String authority) {
        var principal = new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(authority)));
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

    // HV-1575: the GraphQL schema — not just the DTO — now allows `name` to be omitted from
    // the updateDepartment input entirely, so callers can target description/headUserId alone.
    @Test
    void updateDepartment_omittedNameVariable_succeeds() {
        setAuthorityWithPrincipal("department.update");
        when(departmentService.updateDepartment(eq("admin-1"), eq("dept-1"), any()))
                .thenReturn(new DepartmentResponse("dept-1", "Facilities", "New description", true, 0, null));

        graphQlTester.document(UPDATE_MUTATION)
                .variable("id", "dept-1")
                .variable("description", "New description")
                .execute()
                .errors().verify()
                .path("updateDepartment.description").entity(String.class).isEqualTo("New description");

        verify(departmentService).updateDepartment(eq("admin-1"), eq("dept-1"), any());
    }

    @Test
    void updateDepartment_blankName_isRejectedByValidation() {
        setAuthorityWithPrincipal("department.update");

        String mutation = """
                mutation($id: ID!, $name: String) {
                  updateDepartment(id: $id, input: { name: $name }) {
                    id
                  }
                }
                """;

        graphQlTester.document(mutation)
                .variable("id", "dept-1")
                .variable("name", "   ")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(departmentService, never()).updateDepartment(any(), any(), any());
    }

    @Test
    void updateDepartment_emptyInput_isRejectedByValidation() {
        setAuthorityWithPrincipal("department.update");

        String mutation = """
                mutation($id: ID!) {
                  updateDepartment(id: $id, input: {}) {
                    id
                  }
                }
                """;

        graphQlTester.document(mutation)
                .variable("id", "dept-1")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(departmentService, never()).updateDepartment(any(), any(), any());
    }

    // HV-1586: a user may be HOD of more than one department at once.
    @Test
    void departmentsHeadedBy_returnsAllDepartmentsForUser() {
        setAuthority("department.read");
        when(departmentService.listDepartmentsHeadedBy("admin-1")).thenReturn(List.of(
                new DepartmentResponse("dept-1", "Facilities", null, true, 0, "admin-1"),
                new DepartmentResponse("dept-2", "Support", null, true, 0, "admin-1")));

        String query = """
                query($userId: ID!) {
                  departmentsHeadedBy(userId: $userId) {
                    id
                  }
                }
                """;

        graphQlTester.document(query)
                .variable("userId", "admin-1")
                .execute()
                .errors().verify()
                .path("departmentsHeadedBy[*].id")
                .entityList(String.class)
                .containsExactly("dept-1", "dept-2");

        verify(departmentService).listDepartmentsHeadedBy("admin-1");
    }
}
