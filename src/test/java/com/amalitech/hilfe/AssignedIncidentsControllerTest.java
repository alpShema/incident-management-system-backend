package com.amalitech.hilfe;

import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.controllers.AssignedIncidentsController;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.services.DashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AssignedIncidentsController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AssignedIncidentsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("agent-1", "agent@test.com", RoleCode.AGENT);
    }

    @Test
    void listAssignedIncidents_adminAuth_returns200() throws Exception {
        when(dashboardService.getIncidents(anyString(), any(RoleCode.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "dashboard.admin"));

        mvc.perform(get("/assigned-incidents").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Assigned incidents retrieved successfully"));
    }

    @Test
    void listAssignedIncidents_agentAuth_returns200() throws Exception {
        when(dashboardService.getIncidents(anyString(), any(RoleCode.class), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "dashboard.agent"));

        mvc.perform(get("/assigned-incidents").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Assigned incidents retrieved successfully"));
    }

    @Test
    void listAssignedIncidents_noAuth_returns401() throws Exception {
        mvc.perform(get("/assigned-incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listAssignedIncidents_noPermission_returns403() throws Exception {
        var clientPrincipal = new JwtTokenService.AuthPrincipal("client-1", "client@test.com", RoleCode.CLIENT);
        var auth = new UsernamePasswordAuthenticationToken(
                clientPrincipal, null, List.of(() -> "incident.create"));

        mvc.perform(get("/assigned-incidents").with(authentication(auth)))
                .andExpect(status().isForbidden());
    }
}
