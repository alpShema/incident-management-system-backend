package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "Incidents", description = "Incident lifecycle management — create, retrieve, filter, update status/severity, and assign")
@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class IncidentController {
    private final IncidentService incidentService;

    @Operation(
        summary = "Create a new incident",
        description = "Creates an incident on behalf of the authenticated user. Use the stable IDs returned by the lookup endpoints for incidentTypeId, locationId, and severityId. "
                    + "For attachments, first request a presigned upload URL from `POST /media/presigned-url`, upload the file to S3, then include the returned fileKey here. "
                    + "Requires `incident.create` permission."
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
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Incident creation payload. Attachments are optional and must reference files already uploaded through the presigned URL flow.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateIncidentRequest.class),
                            examples = @ExampleObject(
                                    name = "Incident with attachment",
                                    value = """
                                            {
                                              "title": "Projector not working in Room 3B",
                                              "description": "The ceiling projector in Room 3B fails to power on after pressing the remote button.",
                                              "incidentTypeId": "type-account-issues",
                                              "locationId": "loc-accra",
                                              "severityId": "sev-low",
                                              "attachments": [
                                                {
                                                  "fileKey": "media/933631a6-75bf-4d5a-b237-aa986ad2dbe6/screenshot.png",
                                                  "originalName": "screenshot.png",
                                                  "contentType": "image/png",
                                                  "fileSize": 2048576
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateIncidentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident created successfully",
                        incidentService.createIncident(principal.userId(), request)));
    }

    @Operation(
        summary = "List incidents",
        description = "Returns a paginated list of incidents created by the authenticated user. "
                    + "All roles (CLIENT, AGENT, ADMIN) see only incidents they raised. "
                    + "Supports filtering by statusId, severityId, incidentTypeId, categoryId, and locationId. "
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
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.listIncidents(
                principal.userId(),
                statusId, severityId, incidentTypeId, categoryId, locationId,
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", PageResponse.from(page)));
    }

    @Operation(
        summary = "Search incidents by keyword",
        description = "Full-text search across incident title, description, topic name, and category name. "
                    + "Returns only incidents created by the authenticated user. "
                    + "Supports pagination and sorting via Pageable (e.g. sort=createdAt,desc)."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or blank search query"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> searchIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Free-text keyword to search in title, description, topic name, and category name", required = true)
            @RequestParam String query,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.searchIncidents(principal.userId(), query, pageable);
        return ResponseEntity.ok(ApiResponse.success("Incidents retrieved successfully", PageResponse.from(page)));
    }

    @Operation(summary = "Get a single incident", description = "Returns full detail of an incident by its ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident retrieved successfully",
                incidentService.getIncident(principal.userId(), principal.roleCode(), id)));
    }

    @Operation(
        summary = "Update incident status",
        description = "Changes the incident status following the role-based lifecycle. "
                    + "Agents: In Progress → Pending/Resolved, Pending → In Progress. "
                    + "Clients: Resolved → Closed/Reopened. "
                    + "Admins: any → Closed (override). "
                    + "Invalid transitions are rejected with HTTP 422. Requires `incident.status.change` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Your role is not permitted to move an incident to the requested status"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Transition path does not exist for the incident's current status"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or status not found")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_STATUS_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident status updated successfully",
                incidentService.updateStatus(principal.userId(), principal.roleCode(), id, request)));
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
