package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.CurrentUserAuthority;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
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
    private static final String MSG_INCIDENTS_RETRIEVED = "Incidents retrieved successfully";

    private final IncidentService incidentService;
    private final ActivityLogService activityLogService;

    @Operation(
        summary = "Create a new incident",
        description = "Creates an incident on behalf of the authenticated user. Use the stable IDs returned by the lookup endpoints for incidentTypeId, locationId, and severityId. "
                    + "For attachments, first request a presigned upload URL from `POST /media/presigned-url`, upload the file to S3, then include the returned fileKey here. "
                    + "Requires `incident.create` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Incident created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident type or location not found")
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
        summary = "List all incidents",
        description = "Returns a paginated list of all incidents in the system. "
                    + "Restricted to admins and super admins — agents and clients receive 403. "
                    + "Accepts an optional `query` keyword that searches across title, description, topic name, and category name. "
                    + "Accepts optional filter parameters (statusId, severityId, incidentTypeId, categoryId, locationId). "
                    + "Accepts optional date range filters (fromDate, toDate) to filter by creation date. "
                    + "Both `query` and filters can be supplied together to narrow results simultaneously. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=createdAt,desc`). "
                    + "Requires `dashboard.admin` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listAllIncidents(
            @Parameter(description = "Keyword search across title, description, topic name, and category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            @Parameter(description = "Filter by SLA status. Admin-only.") @RequestParam(required = false) SlaStatus slaStatus,
            @Parameter(description = "Filter from date (inclusive). Returns incidents created on or after this timestamp.", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant fromDate,
            @Parameter(description = "Filter to date (exclusive). Returns incidents created before this timestamp.", example = "2026-02-01T00:00:00Z") @RequestParam(required = false) Instant toDate,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.queryAllIncidents(
                query,
                new IncidentFilterParams(statusId, severityId, incidentTypeId, categoryId, locationId, slaStatus),
                new IncidentDateFilter(fromDate, toDate),
                pageable);
        return ResponseEntity.ok(ApiResponse.success(MSG_INCIDENTS_RETRIEVED, PageResponse.from(page)));
    }

    @Operation(
        summary = "List my incidents",
        description = "Returns a paginated list of incidents raised by the authenticated user. "
                    + "Accessible by all roles (CLIENT, AGENT, ADMIN). "
                    + "Accepts an optional `query` keyword that searches across title, description, topic name, and category name. "
                    + "Accepts optional filter parameters (statusId, severityId, incidentTypeId, categoryId, locationId). "
                    + "Accepts optional date range filters (fromDate, toDate) to filter by creation date. "
                    + "Both `query` and filters can be supplied together to narrow results simultaneously. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=createdAt,desc`)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/my-incidents")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listMyIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Keyword search across title, description, topic name, and category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            @Parameter(description = "Filter by SLA status. Admin-only.") @RequestParam(required = false) SlaStatus slaStatus,
            @Parameter(description = "Filter from date (inclusive). Returns incidents created on or after this timestamp.", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant fromDate,
            @Parameter(description = "Filter to date (exclusive). Returns incidents created before this timestamp.", example = "2026-02-01T00:00:00Z") @RequestParam(required = false) Instant toDate,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.queryIncidents(
                principal.userId(), query,
                new IncidentFilterParams(statusId, severityId, incidentTypeId, categoryId, locationId, slaStatus),
                new IncidentDateFilter(fromDate, toDate),
                pageable);
        return ResponseEntity.ok(ApiResponse.success(MSG_INCIDENTS_RETRIEVED, PageResponse.from(page)));
    }

    @Operation(
        summary = "List department incidents",
        description = "Returns a paginated list of incidents within the authenticated user's agent group(s). "
                    + "Accessible by admins and agents. "
                    + "Returns an empty list if the caller has no agent group memberships. "
                    + "Accepts an optional `query` keyword that searches across title, description, topic name, and category name. "
                    + "Accepts optional filter parameters (statusId, severityId, incidentTypeId, categoryId, locationId). "
                    + "Accepts optional date range filters (fromDate, toDate) to filter by creation date. "
                    + "Both `query` and filters can be supplied together to narrow results simultaneously. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=createdAt,desc`). "
                    + "Requires `dashboard.admin` or `dashboard.agent` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping("/dept-incidents")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listDeptIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Keyword search across title, description, topic name, and category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            @Parameter(description = "Filter by SLA status. Admin-only.") @RequestParam(required = false) SlaStatus slaStatus,
            @Parameter(description = "Filter from date (inclusive). Returns incidents created on or after this timestamp.", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant fromDate,
            @Parameter(description = "Filter to date (exclusive). Returns incidents created before this timestamp.", example = "2026-02-01T00:00:00Z") @RequestParam(required = false) Instant toDate,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.queryDeptIncidents(
                principal.userId(), query,
                new IncidentFilterParams(statusId, severityId, incidentTypeId, categoryId, locationId, slaStatus),
                new IncidentDateFilter(fromDate, toDate),
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Department incidents retrieved successfully", PageResponse.from(page)));
    }

    @Operation(
        summary = "List assigned incidents",
        description = "Returns a paginated list of incidents directly assigned to the authenticated user. "
                    + "Accessible by admins and agents. "
                    + "Returns an empty list if the caller has no agent record. "
                    + "Accepts an optional `query` keyword that searches across title, description, topic name, and category name. "
                    + "Accepts optional filter parameters (statusId, severityId, incidentTypeId, categoryId, locationId). "
                    + "Accepts optional date range filters (fromDate, toDate) to filter by creation date. "
                    + "Both `query` and filters can be supplied together to narrow results simultaneously. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=createdAt,desc`). "
                    + "Requires `dashboard.admin` or `dashboard.agent` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incidents retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping("/assigned-incidents")
    @PreAuthorize("hasAnyAuthority('" + RbacPermissions.DASHBOARD_ADMIN + "', '" + RbacPermissions.DASHBOARD_AGENT + "')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> listAssignedIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Keyword search across title, description, topic name, and category name") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by status ID") @RequestParam(required = false) String statusId,
            @Parameter(description = "Filter by severity ID") @RequestParam(required = false) String severityId,
            @Parameter(description = "Filter by incident type (topic) ID") @RequestParam(required = false) String incidentTypeId,
            @Parameter(description = "Filter by incident category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by location ID") @RequestParam(required = false) String locationId,
            @Parameter(description = "Filter by SLA status. Admin-only.") @RequestParam(required = false) SlaStatus slaStatus,
            @Parameter(description = "Filter from date (inclusive). Returns incidents created on or after this timestamp.", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant fromDate,
            @Parameter(description = "Filter to date (exclusive). Returns incidents created before this timestamp.", example = "2026-02-01T00:00:00Z") @RequestParam(required = false) Instant toDate,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.queryAssignedIncidents(
                principal.userId(), query,
                new IncidentFilterParams(statusId, severityId, incidentTypeId, categoryId, locationId, slaStatus),
                new IncidentDateFilter(fromDate, toDate),
                pageable);
        return ResponseEntity.ok(ApiResponse.success("Assigned incidents retrieved successfully", PageResponse.from(page)));
    }

    @Operation(
        summary = "Search incidents by keyword",
        description = "Full-text search across incident title, description, topic name, and category name. "
                    + "Returns only incidents created by the authenticated user. "
                    + "Accepts optional date range filters (fromDate, toDate) to filter by creation date. "
                    + "Supports pagination and sorting via Pageable (e.g. sort=createdAt,desc)."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Search results returned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or blank search query")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<IncidentResponse>>> searchIncidents(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Free-text keyword to search in title, description, topic name, and category name", required = true)
            @RequestParam String query,
            @Parameter(description = "Filter from date (inclusive). Returns incidents created on or after this timestamp.", example = "2026-01-01T00:00:00Z") @RequestParam(required = false) Instant fromDate,
            @Parameter(description = "Filter to date (exclusive). Returns incidents created before this timestamp.", example = "2026-02-01T00:00:00Z") @RequestParam(required = false) Instant toDate,
            Pageable pageable
    ) {
        Page<IncidentResponse> page = incidentService.searchIncidents(principal.userId(), query, fromDate, toDate, pageable);
        return ResponseEntity.ok(ApiResponse.success(MSG_INCIDENTS_RETRIEVED, PageResponse.from(page)));
    }

    @Operation(summary = "Get a single incident", description = "Returns full detail of an incident by its ID.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IncidentResponse>> getIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident retrieved successfully",
                incidentService.getIncident(principal.userId(), parseRoleCode(principal.roleCode()), id)));
    }

    @Operation(
        summary = "Update incident status",
        description = "Changes the incident status following the role-based lifecycle. "
                    + "Agents: In Progress → Pending/Resolved, Pending → In Progress. "
                    + "Clients: Resolved → Closed/Reopened. "
                    + "Holders of `incident.forceclose`: any → Closed (override). "
                    + "Invalid transitions are rejected with HTTP 422. Requires `incident.status.change` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Your role is not permitted to move an incident to the requested status")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Transition path does not exist for the incident's current status")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or status not found")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_STATUS_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentStatusRequest request
    ) {
        boolean hasForceClose = CurrentUserAuthority.has(RbacPermissions.INCIDENT_FORCECLOSE);
        return ResponseEntity.ok(ApiResponse.success("Incident status updated successfully",
                incidentService.updateStatus(principal.userId(), parseRoleCode(principal.roleCode()), hasForceClose, id, request)));
    }

    @Operation(
        summary = "Update incident severity",
        description = "Sets the priority/severity level of an incident. Requires `incident.severity.change` permission. "
                    + "Callers without `incident.update.any` may only update incidents assigned to them."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severity updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident or severity not found")
    @PatchMapping("/{id}/severity")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_SEVERITY_CHANGE + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> updateSeverity(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentSeverityRequest request
    ) {
        boolean hasUpdateAny = CurrentUserAuthority.has(RbacPermissions.INCIDENT_UPDATE_ANY);
        return ResponseEntity.ok(ApiResponse.success("Incident severity updated successfully",
                incidentService.updateSeverity(principal.userId(), hasUpdateAny, id, request)));
    }

    @Operation(
        summary = "Assign incident to an agent",
        description = "Assigns the incident to a specific agent by their agent ID. Requires `incident.assign` permission. "
                    + "Callers without `incident.update.any` may only reassign incidents already assigned to them."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident assigned")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_ASSIGN + "')")
    public ResponseEntity<ApiResponse<IncidentResponse>> assignIncident(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            @Valid @RequestBody AssignIncidentRequest request
    ) {
        boolean hasUpdateAny = CurrentUserAuthority.has(RbacPermissions.INCIDENT_UPDATE_ANY);
        return ResponseEntity.ok(ApiResponse.success("Incident assigned successfully",
                incidentService.assignIncident(principal.userId(), hasUpdateAny, id, request)));
    }

    @Operation(
        summary = "Get incident history",
        description = "Returns a paginated, reverse-chronological audit log for a specific incident. "
                    + "Accessible to the incident reporter, assigned agent, or any admin."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Incident history retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Incident not found")
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<PageResponse<ActivityLogResponse>>> getIncidentHistory(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Incident ID") @PathVariable String id,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                "Incident history retrieved successfully",
                PageResponse.from(activityLogService.getActivityLogs(id, pageable, principal.userId(), principal.roleCode()))
        ));
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
