package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentCategoryService;
import com.amalitech.hilfe.services.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Tag(name = "Incident Categories & Topics", description = "Manage incident categories (groups) and their topics (incident types used when reporting)")
@RestController
@RequestMapping("/incident-categories")
@RequiredArgsConstructor
public class IncidentCategoryController {
    private final IncidentCategoryService categoryService;

    @Operation(summary = "List incident categories", description = "Returns categories filtered by status and optional search query. Defaults to active categories. Pass `status=inactive` to get inactive ones. Pass `query` to search by category name, description, or department name. Requires authentication, but no role-specific permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories retrieved")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentCategoryResponse>>> listCategories(
            @Parameter(description = "Filter by status: active (default) or inactive") @RequestParam(required = false, defaultValue = "active") String status,
            @Parameter(description = "Optional search keyword for category name, description, or department name") @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident categories retrieved successfully",
                PageResponse.from(categoryService.listCategories(status, query, pageable))));
    }

    @Operation(
            summary = "List all incident categories regardless of state",
            description = "Returns all incident categories — both active and inactive — visible to admin users. "
                    + "Supports an optional `state` filter: `active` returns only active categories, `inactive` returns only inactive categories, "
                    + "`all` (default when omitted) returns both. "
                    + "Supports an optional `query` parameter to search by category name, description, or department name. "
                    + "Results are paginated. Requires `ADMIN` or `SUPER_ADMIN` role.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid state filter value")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentCategoryResponse>>> listAllCategories(
            @Parameter(description = "Filter by state: active, inactive, or all (default)") @RequestParam(required = false) String state,
            @Parameter(description = "Optional search keyword for category name, description, or department name") @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident categories retrieved successfully",
                PageResponse.from(categoryService.listAllCategories(state, query, pageable))));
    }

    @Operation(summary = "Create an incident category", description = "Creates a new category under an internal department. `departmentId` is required. Requires `incident-category.create` permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Category created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Category name already exists")
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> createCategory(
            @Valid @RequestBody IncidentCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident category created successfully", categoryService.createCategory(request)));
    }

    @Operation(summary = "Update an incident category", description = "Updates the name, description, or linked department of an existing category. Requires `incident-category.update` permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> updateCategory(
            @Parameter(description = "Stable category ID", example = "cat-facilities") @PathVariable String id,
            @RequestBody UpdateIncidentCategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident category updated successfully", categoryService.updateCategory(id, request)));
    }

    @Operation(summary = "Update incident category status", description = "Activates or deactivates an incident category. `status=true` activates and `status=false` deactivates. Requires `incident-category.delete` permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Category status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_DELETE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> updateCategoryStatus(
            @Parameter(description = "Stable category ID", example = "cat-facilities") @PathVariable String id,
            @Valid @RequestBody UpdateIncidentCategoryStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident category status updated successfully", categoryService.updateCategoryStatus(id, request.status())));
    }

    @Operation(summary = "List topics for a category", description = "Returns all incident topics (types) belonging to the given stable category ID. Requires authentication, but no role-specific permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topics retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @GetMapping("/{id}/topics")
    public ResponseEntity<ApiResponse<List<IncidentTopicResponse>>> listTopics(@Parameter(description = "Stable category ID", example = "cat-it") @PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Incident topics retrieved successfully", categoryService.listTopicsByCategory(id)));
    }

    @Operation(
        summary = "Create a topic under a category",
        description = "Adds a new incident topic (type) to the specified category. The path `id` is the stable category ID returned by the category list endpoint, for example `cat-it`. "
                    + "Admins must provide the responsible `agentGroupId`. The agent group must have a primary agent and must belong to the same department as the category. "
                    + "Requires `incident-type.create` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Topic created")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Category not found")
    @PostMapping("/{id}/topics")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN') and hasAuthority('" + RbacPermissions.INCIDENT_TYPE_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentTopicResponse>> createTopic(
            @Parameter(description = "Stable category ID", example = "cat-it") @PathVariable String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Topic creation payload. The response ID is the generated topic ID clients should later send as incidentTypeId when creating incidents.",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateTopicRequest.class),
                            examples = @ExampleObject(
                                    name = "Create topic",
                                    value = """
                                            {
                                              "name": "Projector",
                                              "description": "Issues with projection equipment in meeting rooms",
                                              "agentGroupId": "agent-group-facilities",
                                              "visibleToGroup": true
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody CreateTopicRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident topic created successfully", categoryService.createTopic(id, principal.userId(), request)));
    }

    @PatchMapping("/{categoryId}/topics/{topicId}")
    @Operation(summary = "Update a topic", description = "Updates a topic name, description, assigned agent group, or group visibility. If `agentGroupId` is changed, the new group must have a primary agent and must belong to the same department as the category. Requires `incident-type.update` permission.")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentTopicResponse>> updateTopic(
            @PathVariable String categoryId,
            @PathVariable String topicId,
            @Valid @RequestBody UpdateTopicRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident topic updated successfully",
                categoryService.updateTopic(categoryId, topicId, request)));
    }

    @DeleteMapping("/{categoryId}/topics/{topicId}")
    @Operation(summary = "Delete a topic", description = "Deletes a topic if no incidents reference it. Requires `incident-type.delete` permission.")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_DELETE + "')")
    public ResponseEntity<Void> deleteTopic(
            @PathVariable String categoryId,
            @PathVariable String topicId
    ) {
        categoryService.deleteTopic(categoryId, topicId);
        return ResponseEntity.noContent().build();
    }
}
