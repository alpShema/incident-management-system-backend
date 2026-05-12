package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardCharts;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardStats;
import com.amalitech.hilfe.dto.dashboard.AgentDashboardSummary;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentDashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @Operation(summary = "Agent dashboard stats", description = "Returns top-level counts of assigned incidents: total, open, closed, resolved.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stats retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/agent/stats")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<AgentDashboardStats>> agentStats(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved successfully", dashboardService.getStats(principal.userId())));
    }

    @Operation(summary = "Agent dashboard charts", description = "Returns donut (assigned by status) and two monthly trend series (my incidents vs assigned). period: 7d | 30d | 90d.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chart data retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/agent/charts")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<AgentDashboardCharts>> agentCharts(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Time period filter: 7d, 30d, or 90d. Omit for all time.")
            @RequestParam(required = false) String period
    ) {
        return ResponseEntity.ok(ApiResponse.success("Chart data retrieved successfully", dashboardService.getCharts(principal.userId(), period)));
    }

    @Operation(summary = "Agent assigned incidents", description = "Returns paginated incidents assigned to the authenticated agent.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied")
    })
    @GetMapping("/agent/incidents")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> agentIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @RequestParam(required = false) String statusId,
            @RequestParam(required = false) String severityId,
            @RequestParam(required = false) String incidentTypeId,
            @RequestParam(required = false) String locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<IncidentResponse> result = dashboardService.getAssignedIncidents(
                principal.userId(), statusId, severityId, incidentTypeId, locationId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.success("Assigned incidents retrieved successfully", result));
    }
}
