package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.services.AgentGroupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(AgentGroupResolver.class)
@Import({AgentGroupResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class})
class AgentGroupResolverTest {

    // @PreAuthorize needs @EnableMethodSecurity, but importing the app's full SecurityConfig
    // drags in @EnableWebSecurity's filter chain (RequestIdFilter etc.), which this non-web
    // GraphQlTest slice doesn't have. This scopes method security to just what's needed here.
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @MockitoBean AgentGroupService agentGroupService;

    private static final String QUERY = """
            query($deptId: String!) {
              allAgentGroups(departmentId: $deptId) {
                items { id name status }
                totalElements
              }
            }
            """;

    @BeforeEach
    void bindRequestContext() {
        // createAgentGroup/updateAgentGroup call GraphQlResponseMessage.set(), which needs a
        // thread-bound request; the query-only tests above don't exercise that path.
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

    private AgentGroupResponse stubGroup() {
        return new AgentGroupResponse(
                "group-1", "IT Support", "Handles IT incidents",
                LookupResponse.from("dept-1", "Facilities"), true, 2, null, null);
    }

    @Test
    void allAgentGroups_withDepartmentReadAuthority_returnsFilteredGroups() {
        setAuthority("agent-group.read");
        when(agentGroupService.listAllAgentGroups(any(), any(), eq("dept-1"), any()))
                .thenReturn(new PageImpl<>(List.of(stubGroup()), PageRequest.of(0, 20), 1));

        graphQlTester.document(QUERY)
                .variable("deptId", "dept-1")
                .execute()
                .path("allAgentGroups.items[0].id").entity(String.class).isEqualTo("group-1")
                .path("allAgentGroups.totalElements").entity(Long.class).isEqualTo(1L);

        verify(agentGroupService).listAllAgentGroups(isNull(), isNull(), eq("dept-1"), any());
    }

    @Test
    void allAgentGroups_departmentNotFound_returnsGraphQlError() {
        setAuthority("agent-group.read");
        when(agentGroupService.listAllAgentGroups(any(), any(), eq("missing"), any()))
                .thenThrow(new ArmsAuthException("Department not found", 404));

        graphQlTester.document(QUERY)
                .variable("deptId", "missing")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();
    }

    @Test
    void allAgentGroups_withoutAgentGroupReadAuthority_isDenied() {
        setAuthority("ROLE_AGENT");

        graphQlTester.document(QUERY)
                .variable("deptId", "dept-1")
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(agentGroupService, never()).listAllAgentGroups(any(), any(), any(), any());
    }

    // HV-1575: AgentGroupInput.name is now nullable at the schema level, matching Department's
    // fix — updateAgentGroup can be called with name omitted entirely.
    @Test
    void updateAgentGroup_omittedNameVariable_succeeds() {
        setAuthority("agent-group.update");
        when(agentGroupService.updateAgentGroup(eq("group-1"), any())).thenReturn(
                new AgentGroupResponse("group-1", "IT Support", "New description",
                        LookupResponse.from("dept-1", "Facilities"), true, 2, null, null));

        String mutation = """
                mutation($id: ID!, $description: String) {
                  updateAgentGroup(id: $id, input: { description: $description }) {
                    id
                    description
                  }
                }
                """;

        graphQlTester.document(mutation)
                .variable("id", "group-1")
                .variable("description", "New description")
                .execute()
                .errors().verify()
                .path("updateAgentGroup.description").entity(String.class).isEqualTo("New description");

        verify(agentGroupService).updateAgentGroup(eq("group-1"), any());
    }

    // createAgentGroup still requires a name in practice — now enforced by AgentGroupService's
    // existing runtime check rather than the GraphQL schema, since AgentGroupInput is shared
    // between create and update and update must allow name to be omitted.
    @Test
    void createAgentGroup_omittedName_isRejectedByService() {
        setAuthority("agent-group.create");
        when(agentGroupService.createAgentGroup(any()))
                .thenThrow(new ArmsAuthException("Agent group name is required", 400));

        String mutation = """
                mutation($departmentId: String!, $agentIds: [String!]!) {
                  createAgentGroup(input: { departmentId: $departmentId, agentIds: $agentIds }) {
                    id
                  }
                }
                """;

        graphQlTester.document(mutation)
                .variable("departmentId", "dept-1")
                .variable("agentIds", List.of("agent-1"))
                .execute()
                .errors()
                .expect(error -> error.getMessage() != null)
                .verify();

        verify(agentGroupService).createAgentGroup(any());
    }
}
