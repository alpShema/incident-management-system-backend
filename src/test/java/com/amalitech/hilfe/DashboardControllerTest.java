package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.DashboardController;
import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.dto.dashboard.SlaReportResponse;
import com.amalitech.hilfe.dto.dashboard.SlaSeverityBreakdown;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.DashboardService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DashboardService dashboardService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("agent-1", "agent@test.com", RoleCode.AGENT);
    }

    private DashboardStats stubStats() {
        return new DashboardStats(10L, 5L, 2L, 1L, 3L, 2L, 0L);
    }

    private DashboardCharts stubCharts() {
        return new DashboardCharts(List.of(), List.of());
    }

    // ── GET /dashboard/stats ──────────────────────────────────────────────────

    @Test
    void getStats_agentAuth_returns200() throws Exception {
        when(dashboardService.getStats(anyString(), any())).thenReturn(stubStats());

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "dashboard.agent"));

        mvc.perform(get("/dashboard/stats").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Stats retrieved successfully"));
    }

    // ── GET /dashboard/charts ─────────────────────────────────────────────────

    @Test
    void getCharts_adminAuth_withPeriod_returns200() throws Exception {
        when(dashboardService.getCharts(anyString(), any(), any())).thenReturn(stubCharts());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "dashboard.admin"));

        mvc.perform(get("/dashboard/charts")
                        .param("period", "7d")
                        .with(authentication(auth)))
                .andExpect(status().isOk());
    }

    @Test
    void getCharts_unsupportedPeriod_returns400() throws Exception {
        when(dashboardService.getCharts(any(), any(), any()))
                .thenThrow(new ArmsAuthException(
                        "Unsupported period '60d'. Accepted values: 7d, 30d, 90d.", 400));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "dashboard.admin"));

        mvc.perform(get("/dashboard/charts")
                        .param("period", "60d")
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported period '60d'. Accepted values: 7d, 30d, 90d."));
    }

    @Test
    void getSlaReport_adminAuth_returns200() throws Exception {
        when(dashboardService.getSlaReport(any(), any(), any())).thenReturn(
                new SlaReportResponse(1, 1, 0, 30.0, 90.0, List.of(
                        new SlaSeverityBreakdown("sev-low", "Low", 1, 1, 0, 30.0, 90.0)
                ))
        );

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "dashboard.admin"));

        mvc.perform(get("/dashboard/sla-report").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("SLA report retrieved successfully"))
                .andExpect(jsonPath("$.data.trackedIncidents").value(1));
    }
}
