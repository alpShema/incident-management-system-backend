package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.services.IncidentCategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(IncidentCategoryResolver.class)
@Import({IncidentCategoryResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class IncidentCategoryResolverTest {

    // @PreAuthorize needs @EnableMethodSecurity, but importing the app's full SecurityConfig
    // drags in @EnableWebSecurity's filter chain (RequestIdFilter etc.), which this non-web
    // GraphQlTest slice doesn't have. This scopes method security to just what's needed here.
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean IncidentCategoryService categoryService;

    private static final String QUERY = """
            query($deptId: String!) {
              incidentCategoriesByDepartment(departmentId: $deptId) {
                items { id name status }
                totalElements
              }
            }
            """;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthority(String authority) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private IncidentCategoryResponse stubCategory() {
        return new IncidentCategoryResponse("cat-1", "Facility", "Facility incidents", null, true, null);
    }

    @Test
    void incidentCategoriesByDepartment_withDepartmentReadAuthority_returnsCategories() {
        setAuthority("department.read");
        when(categoryService.listCategoriesByDepartment(eq("dept-1"), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubCategory()), PageRequest.of(0, 20), 1));

        graphQlTester.document(QUERY)
                .variable("deptId", "dept-1")
                .execute()
                .path("incidentCategoriesByDepartment.items[0].id").entity(String.class).isEqualTo("cat-1")
                .path("incidentCategoriesByDepartment.totalElements").entity(Long.class).isEqualTo(1L);

        verify(categoryService).listCategoriesByDepartment(eq("dept-1"), isNull(), isNull(), any());
    }

    @Test
    void incidentCategoriesByDepartment_missingDepartmentId_isRejectedByValidation() {
        setAuthority("department.read");
        String queryWithoutVariable = """
                query {
                  incidentCategoriesByDepartment {
                    items { id }
                  }
                }
                """;

        graphQlTester.document(queryWithoutVariable)
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(categoryService, never())
                .listCategoriesByDepartment(any(), any(), any(), any());
    }

    @Test
    void incidentCategoriesByDepartment_withoutDepartmentReadAuthority_isDenied() {
        setAuthority("ROLE_AGENT");

        graphQlTester.document(QUERY)
                .variable("deptId", "dept-1")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(categoryService, never())
                .listCategoriesByDepartment(any(), any(), any(), any());
    }
}
