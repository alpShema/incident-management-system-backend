package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.DashboardService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

@Tag(name = "Assigned Incidents", description = "Incidents assigned to the authenticated user's workload")
@RestController
@RequestMapping("/assigned-incidents")
@RequiredArgsConstructor
public class AssignedIncidentsController {

    private final DashboardService dashboardService;

    @Operation(
            summary = "List assigned incidents",
            description = "Returns incidents assigned to the authenticated user's workload. "
                    + "Agents see incidents assigned to their agent group(s). "
                    + "Admins see all incidents in the system. "
                    + "Accepts an optional `query` keyword that searches across title, description, topic name, and category name. "
                    + "Accepts optional filter parameters (statusId, severityId, incidentTypeId, categoryId, locationId). "
                    + "Both `query` and filters can be supplied together to narrow results simultaneously. "
                    + "Results are always sorted by creation date descending (newest first); sort order is not configurable. "
                    + "Requires `dashboard.admin` or `dashboard.agent` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listAssignedIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Keyword search across title, description, topic name, and category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size
    ) {
        Page<IncidentResponse> result = dashboardService.getIncidents(
                principal.userId(), parseRoleCode(principal.roleCode()),
                query,
                new IncidentFilterParams(statusId, severityId, incidentTypeId, categoryId, locationId),
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );
        return ResponseEntity.ok(ApiResponse.success("Assigned incidents retrieved successfully", PageResponse.from(result)));
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) return null;
        try {
            return RoleCode.valueOf(roleCode.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
