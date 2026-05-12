package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.AssignIncidentRequest;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateIncidentSeverityRequest;
import com.amalitech.hilfe.dto.UpdateIncidentStatusRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Incidents", description = "Incident lifecycle management — create, retrieve, filter, update status/severity, and assign")
@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    @Operation(
        summary = "Create a new incident",
        description = "Creates an incident on behalf of the authenticated user. Requires `incident.create` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Incident created"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident type or location not found")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> createIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident created successfully",
                        incidentService.createIncident(principal.userId(), request)));
    }

    @Operation(
        summary = "List incidents",
        description = "Returns a paginated list of incidents. Results are automatically scoped by role: "
                    + "CLIENTs see only their own, AGENTs see only assigned incidents, ADMINs see all. "
                    + "Supports filtering by statusId, severityId, incidentTypeId, and locationId. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=createdAt,desc`)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.listIncidents(
                principal.userId(), principal.roleCode(),
                statusId, severityId, incidentTypeId, locationId,
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", PageResponse.from(page)));
    }

    @Operation(summary = "Get a single incident", description = "Returns full detail of an incident by its ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(
            @Parameter(description = "Incident ID") @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident retrieved successfully",
                incidentService.getIncident(id)));
    }

    @Operation(
        summary = "Update incident status",
        description = "Changes the incident status following the allowed lifecycle: "
                    + "Open → Pending → Resolved → Closed. "
                    + "Invalid transitions are rejected with HTTP 422. Requires `incident.status.change` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or status not found"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid status transition")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_STATUS_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident status updated successfully",
                incidentService.updateStatus(principal.userId(), id, request)));
    }

    @Operation(
        summary = "Update incident severity",
        description = "Sets the priority/severity level of an incident. Requires `incident.severity.change` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or severity not found")
    })
    @PatchMapping("/{id}/severity")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_SEVERITY_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateSeverity(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentSeverityRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident severity updated successfully",
                incidentService.updateSeverity(principal.userId(), id, request)));
    }

    @Operation(
        summary = "Assign incident to an agent",
        description = "Assigns the incident to a specific agent by their agent ID. Requires `incident.assign` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident assigned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_ASSIGN + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody AssignIncidentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident assigned successfully",
                incidentService.assignIncident(principal.userId(), id, request)));
    }
}
