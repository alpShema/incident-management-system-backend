package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.RoleResponse;
import com.amalitech.hilfe.services.RoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(RoleResolver.class)
@Import({RoleResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class RoleResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean RoleService roleService;

    private static final String QUERY = """
            query($page: PageInput) {
              roles(page: $page) {
                items { id name description }
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

    private RoleResponse stubRole(String id, String name) {
        return new RoleResponse(id, id.toUpperCase(), name, "desc", false, List.of());
    }

    @Test
    void roles_sortByNameAscending_passesCorrectlyTranslatedPageableToService() {
        setAuthority("rbac.role.read");
        when(roleService.listRoles(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubRole("r1", "Admin"))));

        graphQlTester.document(QUERY)
                .variable("page", java.util.Map.of("sortBy", "name", "sortDirection", "ASC"))
                .execute()
                .path("roles.items[0].id").entity(String.class).isEqualTo("r1");

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(roleService).listRoles(isNull(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void roles_sortByDescriptionDescending_passesCorrectlyTranslatedPageableToService() {
        setAuthority("rbac.role.read");
        when(roleService.listRoles(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubRole("r1", "Admin"))));

        graphQlTester.document(QUERY)
                .variable("page", java.util.Map.of("sortBy", "description", "sortDirection", "DESC"))
                .execute()
                .path("roles.items[0].id").entity(String.class).isEqualTo("r1");

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(roleService).listRoles(isNull(), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("description");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void roles_noPageArgument_passesUnsortedPageableToService() {
        setAuthority("rbac.role.read");
        when(roleService.listRoles(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubRole("r1", "Admin")), PageRequest.of(0, 20), 1));

        graphQlTester.document("""
                query {
                  roles {
                    items { id name description }
                  }
                }
                """)
                .execute()
                .path("roles.items[0].id").entity(String.class).isEqualTo("r1");

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(roleService).listRoles(isNull(), captor.capture());
        assertThat(captor.getValue().getSort().isUnsorted()).isTrue();
    }
}
