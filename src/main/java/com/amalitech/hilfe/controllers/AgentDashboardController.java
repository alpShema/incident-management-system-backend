package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardSummary;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentDashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "Dashboard summary endpoints")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class AgentDashboardController {

    private final AgentDashboardService dashboardService;

    @Operation(summary = "Agent dashboard summary", description = "Returns incident statistics for the authenticated agent.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dashboard data retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/agent")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<AgentDashboardSummary>> agentDashboard(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Dashboard retrieved successfully", dashboardService.getSummary(principal.userId())));
    }
}
