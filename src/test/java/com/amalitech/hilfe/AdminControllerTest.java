package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.AdminController;
import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.AdminService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AdminControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AdminService adminService;
    @MockitoBean TokenService tokenService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-1", "admin@test.com", RoleCode.ADMIN);
    }

    private AgentResponse stubAgentResponse(boolean status) {
        return new AgentResponse("agent-1", "user-1", "Admin User", "admin@test.com", null, null, status, null);
    }

    // ── POST /admins/{userId}/agent-access ────────────────────────────────────

    @Test
    void grantAgentAccess_withPermission_returns200() throws Exception {
        when(adminService.grantAgentAccess("user-1")).thenReturn(stubAgentResponse(true));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.create"));

        mvc.perform(post("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent access granted"))
                .andExpect(jsonPath("$.data.agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void grantAgentAccess_missingPermission_returns403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(post("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantAgentAccess_unauthenticated_returns401() throws Exception {
        mvc.perform(post("/admins/user-1/agent-access")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void grantAgentAccess_userNotFound_returns404() throws Exception {
        when(adminService.grantAgentAccess("user-999"))
                .thenThrow(new ArmsAuthException("User not found", 404));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.create"));

        mvc.perform(post("/admins/user-999/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void grantAgentAccess_userNotAdmin_returns400() throws Exception {
        when(adminService.grantAgentAccess("user-1"))
                .thenThrow(new ArmsAuthException("User is not an admin", 400));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.create"));

        mvc.perform(post("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("User is not an admin"));
    }

    // ── DELETE /admins/{userId}/agent-access ──────────────────────────────────

    @Test
    void revokeAgentAccess_withPermission_returns200() throws Exception {
        when(adminService.revokeAgentAccess("user-1")).thenReturn(stubAgentResponse(false));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.create"));

        mvc.perform(delete("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent access revoked"))
                .andExpect(jsonPath("$.data.agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void revokeAgentAccess_missingPermission_returns403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(delete("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeAgentAccess_unauthenticated_returns401() throws Exception {
        mvc.perform(delete("/admins/user-1/agent-access")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeAgentAccess_noAgentAccess_returns404() throws Exception {
        when(adminService.revokeAgentAccess("user-1"))
                .thenThrow(new ArmsAuthException("Admin has no agent access", 404));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.create"));

        mvc.perform(delete("/admins/user-1/agent-access")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Admin has no agent access"));
    }

    // ── GET /admins/{userId}/agent-access ─────────────────────────────────────

    @Test
    void getAgentAccess_withPermission_returns200() throws Exception {
        when(adminService.getAgentAccess("user-1")).thenReturn(stubAgentResponse(true));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/admins/user-1/agent-access")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agent access retrieved"))
                .andExpect(jsonPath("$.data.agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.userId").value("user-1"))
                .andExpect(jsonPath("$.data.status").value(true));
    }

    @Test
    void getAgentAccess_missingPermission_returns403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident.create"));

        mvc.perform(get("/admins/user-1/agent-access")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAgentAccess_unauthenticated_returns401() throws Exception {
        mvc.perform(get("/admins/user-1/agent-access"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAgentAccess_notFound_returns404() throws Exception {
        when(adminService.getAgentAccess("user-1"))
                .thenThrow(new ArmsAuthException("Admin has no agent access", 404));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/admins/user-1/agent-access")
                        .with(authentication(auth)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Admin has no agent access"));
    }
}
