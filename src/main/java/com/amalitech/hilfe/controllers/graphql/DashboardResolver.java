package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.dto.dashboard.SlaReportResponse;
import com.amalitech.hilfe.services.DashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.models.RoleCode;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class DashboardResolver {

    private final DashboardService dashboardService;

    @QueryMapping
    @PreAuthorize("hasAnyAuthority('dashboard.admin', 'dashboard.agent')")
    public DashboardStats dashboardStats(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return dashboardService.getStats(principal.userId(), RoleCode.valueOf(principal.roleCode()));
    }

    @QueryMapping
    @PreAuthorize("hasAnyAuthority('dashboard.admin', 'dashboard.agent')")
    public DashboardCharts dashboardCharts(
            @Argument String period,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return dashboardService.getCharts(principal.userId(), RoleCode.valueOf(principal.roleCode()), period);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('dashboard.admin')")
    public SlaReportResponse slaReport(
            @Argument Instant from,
            @Argument Instant to,
            @Argument String severityId) {
        return dashboardService.getSlaReport(from, to, severityId);
    }
}
