package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.dashboard.AgentDashboardSummary;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentDashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AgentDashboardController {

    private final AgentDashboardService dashboardService;

    @QueryMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_AGENT + "')")
    public AgentDashboardSummary agentDashboard(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return dashboardService.getSummary(principal.userId());
    }
}
