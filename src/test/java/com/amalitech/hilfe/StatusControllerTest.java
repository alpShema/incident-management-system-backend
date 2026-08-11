package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.StatusController;
import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.StatusService;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class StatusControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean StatusService statusService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal clientPrincipal() {
        return new JwtTokenService.AuthPrincipal("user-1", "user@test.com", RoleCode.CLIENT);
    }

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("agent-user-1", "agent@test.com", RoleCode.AGENT);
    }

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    @Test
    void listStatuses_returns200WithData() throws Exception {
        when(statusService.listStatuses(RoleCode.CLIENT)).thenReturn(List.of(
                new StatusLookupResponse("status-1", "Open", "Open status"),
                new StatusLookupResponse("status-2", "Pending", "Pending status"),
                new StatusLookupResponse("status-3", "Resolved", "Resolved status"),
                new StatusLookupResponse("status-4", "Closed", "Closed status")
        ));

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                clientPrincipal(), null, List.of(() -> "ROLE_CLIENT")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Statuses retrieved successfully"))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("Open"))
                .andExpect(jsonPath("$.data[0].description").value("Open status"));

        verify(statusService).listStatuses(RoleCode.CLIENT);
    }

    @Test
    void listStatuses_agentPrincipal_passesAgentRoleToService() throws Exception {
        when(statusService.listStatuses(RoleCode.AGENT)).thenReturn(List.of(
                new StatusLookupResponse("status-open", "Open", "Open status")
        ));

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                agentPrincipal(), null, List.of(() -> "ROLE_AGENT")))))
                .andExpect(status().isOk());

        verify(statusService).listStatuses(RoleCode.AGENT);
    }

    @Test
    void listStatuses_adminPrincipal_passesAdminRoleToService() throws Exception {
        when(statusService.listStatuses(RoleCode.ADMIN)).thenReturn(List.of(
                new StatusLookupResponse("status-unassigned", "Unassigned", "Unassigned status")
        ));

        mvc.perform(get("/statuses")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk());

        verify(statusService).listStatuses(RoleCode.ADMIN);
    }
}
