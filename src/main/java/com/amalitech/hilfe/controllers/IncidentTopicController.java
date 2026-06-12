package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateIncidentTypeStatusRequest;
import com.amalitech.hilfe.dto.UpdateTopicRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Incident Topics", description = "Global incident topic listing across categories and agent groups")
@RestController
@RequestMapping("/incident-topics")
@RequiredArgsConstructor
public class IncidentTopicController {

    private final IncidentCategoryService incidentCategoryService;

    @Operation(
            summary = "List incident topics",
            description = "Returns a paginated list of incident topics globally. "
                    + "Supports filtering by category, department, agent group, topic status (active/inactive/all), "
                    + "and text query across topic/category/agent-group names. "
                    + "Defaults to returning all topics regardless of status when no status filter is provided."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topics retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status filter")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentTopicListResponse>>> listTopics(
            @Parameter(description = "Filter by category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by department ID (from topic's category)") @RequestParam(required = false) String departmentId,
            @Parameter(description = "Filter by assigned agent group ID") @RequestParam(required = false) String agentGroupId,
            @Parameter(description = "Filter by topic status: active, inactive, or all (default — returns both)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Search text over topic name/description, category name, and agent-group name")
            @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Incident topics retrieved successfully",
                PageResponse.from(incidentCategoryService.listTopics(
                        categoryId, departmentId, agentGroupId, status, query, pageable))));
    }

    @Operation(
            summary = "Update an incident topic",
            description = "Updates a topic's name, description, assigned agent group, or visibility. "
                    + "If `agentGroupId` is changed, the new group must have a primary agent and must "
                    + "belong to the same department as the topic's category. Requires `incident-type.update` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topic updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Topic not found")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentTopicResponse>> updateTopic(
            @Parameter(description = "Stable topic ID", example = "type-account-issues") @PathVariable String id,
            @Valid @RequestBody UpdateTopicRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident topic updated successfully",
                incidentCategoryService.updateTopicById(id, request)));
    }

    @Operation(
            summary = "Update an incident topic status",
            description = "Activates or deactivates an incident topic. `status=true` activates and "
                    + "`status=false` deactivates. Requires `incident-type.delete` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topic status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Topic not found")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Topic already in target status")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_DELETE + "')")
    public ResponseEntity<ApiResponse<IncidentTopicResponse>> updateTopicStatus(
            @Parameter(description = "Stable topic ID", example = "type-account-issues") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentTypeStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident topic status updated successfully",
                incidentCategoryService.updateTopicStatus(id, request.status())));
    }
}
