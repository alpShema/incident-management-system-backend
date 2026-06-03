package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.AgentController;
import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.AgentService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AgentService agentService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-1", "agent@test.com", RoleCode.AGENT);
    }

    private JwtTokenService.AuthPrincipal clientPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-2", "client@test.com", RoleCode.CLIENT);
    }

    private AgentResponse stubAgentResponse(boolean status) {
        return new AgentResponse("agent-1", "user-1", "Test Agent", "agent@test.com", null, status);
    }

    // ── GET /agents ───────────────────────────────────────────────────────────

    @Test
    void listAgents_withoutDepartment_returns200() throws Exception {
        when(agentService.listAgents(isNull(), any())).thenReturn(
                new PageImpl<>(List.of(stubAgentResponse(true)), PageRequest.of(0, 20), 1));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/agents")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agents retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].agentId").value("agent-1"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listAgents_withDepartment_returns200() throws Exception {
        when(agentService.listAgents(anyString(), any())).thenReturn(
                new PageImpl<>(List.of(stubAgentResponse(false)), PageRequest.of(0, 20), 1));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/agents")
                        .param("departmentId", "dept-1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value(false));
    }

    @Test
    void listAllAgents_returns200() throws Exception {
        when(agentService.listAllAgents(isNull(), any())).thenReturn(
                new PageImpl<>(List.of(stubAgentResponse(true), stubAgentResponse(false)), PageRequest.of(0, 20), 2));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/agents/all")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Agents retrieved successfully"))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listAllAgents_withDepartment_returns200() throws Exception {
        when(agentService.listAllAgents(anyString(), any())).thenReturn(
                new PageImpl<>(List.of(stubAgentResponse(false)), PageRequest.of(0, 20), 1));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.read"));

        mvc.perform(get("/agents/all")
                        .param("departmentId", "dept-1")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value(false));
    }

    // ── PATCH /agents/status ──────────────────────────────────────────────────

    @Test
    void updateStatus_agentAuth_returns200() throws Exception {
        when(agentService.updateAvailability(anyString(), anyBoolean())).thenReturn(stubAgentResponse(false));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.availability.update"));

        mvc.perform(patch("/agents/status")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Status updated"))
                .andExpect(jsonPath("$.data.status").value(false));
    }

    @Test
    void updateStatus_missingAvailableField_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.availability.update"));

        mvc.perform(patch("/agents/status")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_clientRole_returns403() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                clientPrincipal(), null, List.of(() -> "incident.create"));

        mvc.perform(patch("/agents/status")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_agentNotFound_returns404() throws Exception {
        when(agentService.updateAvailability(anyString(), anyBoolean()))
                .thenThrow(new ArmsAuthException("Agent not found", 404));

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "agent.availability.update"));

        mvc.perform(patch("/agents/status")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\": true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Agent not found"));
    }
}
