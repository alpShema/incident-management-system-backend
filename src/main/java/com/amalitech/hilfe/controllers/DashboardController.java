package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.dashboard.DashboardCharts;
import com.amalitech.hilfe.dto.dashboard.DashboardStats;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.DashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

@Tag(name = "Dashboard", description = "Role-aware dashboard endpoints (admin and agent)")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "Dashboard stats",
            description = "Admin: total/open/closed/resolved across all incidents. Agent: same counts scoped to assigned incidents."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Stats retrieved",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "message": "Stats retrieved successfully",
                      "data": {
                        "totalIncidents": 120,
                        "openCount": 45,
                        "closedCount": 60,
                        "resolvedCount": 15
                      }
                    }""")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
    })
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<DashboardStats>> stats(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Stats retrieved successfully",
                dashboardService.getStats(principal.userId(), principal.roleCode())
        ));
    }

    @Operation(
            summary = "Dashboard charts",
            description = "Admin: status donut + single 'All Incidents' trend series. Agent: status donut + two trend series (My Incidents, Assigned Incidents). period: 7d | 30d | 90d (omit for all time)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Chart data retrieved",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "message": "Chart data retrieved successfully",
                      "data": {
                        "byStatus": [
                          { "label": "Open",     "count": 45 },
                          { "label": "Closed",   "count": 60 },
                          { "label": "Resolved", "count": 15 }
                        ],
                        "trends": [
                          {
                            "label": "All Incidents",
                            "data": [
                              { "month": "2025-12", "count": 18 },
                              { "month": "2026-01", "count": 24 },
                              { "month": "2026-02", "count": 20 }
                            ]
                          }
                        ]
                      }
                    }""")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
    })
    @GetMapping("/charts")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<DashboardCharts>> charts(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Time period filter: 7d, 30d, or 90d. Omit for all time.")
            @RequestParam(required = false) String period
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Chart data retrieved successfully",
                dashboardService.getCharts(principal.userId(), principal.roleCode(), period)
        ));
    }

    @Operation(
            summary = "Dashboard incidents",
            description = "Admin: all incidents (paginated). Agent: incidents assigned to the authenticated agent (paginated)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Incidents retrieved",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "message": "Incidents retrieved successfully",
                      "data": {
                        "content": [],
                        "totalElements": 0,
                        "totalPages": 0,
                        "number": 0,
                        "size": 10
                      }
                    }""")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
    })
    @GetMapping("/incidents")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> incidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @RequestParam(required = false) String statusId,
            @RequestParam(required = false) String severityId,
            @RequestParam(required = false) String incidentTypeId,
            @RequestParam(required = false) String locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<IncidentResponse> result = dashboardService.getIncidents(
                principal.userId(), principal.roleCode(),
                statusId, severityId, incidentTypeId, locationId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", result));
    }

    @Operation(
            summary = "My incidents",
            description = "Incidents created by the authenticated user (admin or agent), paginated."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Incidents retrieved",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(value = """
                    {
                      "message": "Incidents retrieved successfully",
                      "data": {
                        "content": [],
                        "totalElements": 0,
                        "totalPages": 0,
                        "number": 0,
                        "size": 10
                      }
                    }""")
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied", content = @Content)
    })
    @GetMapping("/my-incidents")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<Page<IncidentResponse>>> myIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @RequestParam(required = false) String statusId,
            @RequestParam(required = false) String severityId,
            @RequestParam(required = false) String incidentTypeId,
            @RequestParam(required = false) String locationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<IncidentResponse> result = dashboardService.getMyIncidents(
                principal.userId(),
                statusId, severityId, incidentTypeId, locationId,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", result));
    }
}
