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
    private static final String MSG_CATEGORIES_RETRIEVED = "Incident categories retrieved successfully";

    private final IncidentCategoryService categoryService;

    @Operation(summary = "List incident categories", description = "Returns categories filtered by status, optional active-topic filter, and optional search query. Defaults to active categories when `status` is omitted. If only `hasActiveTopics` is supplied, status is not restricted. Pass `status=false` to get inactive ones. Pass `hasActiveTopics=true` to require at least one active linked topic. Pass `query` to search by category name, description, or department name. Requires authentication, but no role-specific permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories retrieved")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentCategoryResponse>>> listCategories(
            @Parameter(description = "Filter by category status: true for active, false for inactive") @RequestParam(required = false) Boolean status,
            @Parameter(description = "Filter by active linked topics: true requires at least one active topic, false requires none") @RequestParam(required = false) Boolean hasActiveTopics,
            @Parameter(description = "Optional search keyword for category name, description, or department name") @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(MSG_CATEGORIES_RETRIEVED,
                PageResponse.from(categoryService.listCategories(status, hasActiveTopics, query, pageable))));
    }

    @Operation(
            summary = "List all incident categories regardless of state",
            description = "Returns all incident categories — both active and inactive — visible to admin users. "
                    + "Supports optional `status` and `hasActiveTopics` filters that can be applied independently or combined. "
                    + "`status=true` returns only active categories, `status=false` returns only inactive categories, omitting `status` returns both. "
                    + "`hasActiveTopics=true` returns categories with at least one active linked topic, `hasActiveTopics=false` returns categories with no active topics. "
                    + "Supports an optional `query` parameter to search by category name, description, or department name. "
                    + "Results are paginated. Requires `ADMIN`, `ADMIN_AGENT`, or `SUPER_ADMIN` role.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid filter value")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_AGENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentCategoryResponse>>> listAllCategories(
            @Parameter(description = "Filter by category status: true for active, false for inactive, omit for all") @RequestParam(required = false) Boolean status,
            @Parameter(description = "Filter by active linked topics: true requires at least one active topic, false requires none") @RequestParam(required = false) Boolean hasActiveTopics,
            @Parameter(description = "Optional search keyword for category name, description, or department name") @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(MSG_CATEGORIES_RETRIEVED,
                PageResponse.from(categoryService.listAllCategories(status, hasActiveTopics, query, pageable))));
    }

    @Operation(
            summary = "List incident categories by department",
            description = "Returns categories linked to the given department, of every status by default (no active-only "
                    + "restriction) — pass `status` to narrow to active or inactive only. Supports an optional `query` "
                    + "search across category name, description, or department name. `departmentId` is required; omitting "
                    + "it returns a validation error. Requires `department.read` permission.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Categories retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing departmentId")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Department not found")
    @GetMapping("/by-department")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<IncidentCategoryResponse>>> listCategoriesByDepartment(
            @Parameter(description = "Department ID to scope categories to. Required.") @RequestParam String departmentId,
            @Parameter(description = "Filter by category status: true for active, false for inactive, omit for all") @RequestParam(required = false) Boolean status,
            @Parameter(description = "Optional search keyword for category name, description, or department name") @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(MSG_CATEGORIES_RETRIEVED,
                PageResponse.from(categoryService.listCategoriesByDepartment(departmentId, status, query, pageable))));
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
    public ResponseEntity<ApiResponse<List<IncidentTopicResponse>>> listTopics(
            @Parameter(description = "Stable category ID", example = "cat-it") @PathVariable String id,
            @Parameter(description = "Filter by topic status: true for active, false for inactive, omit for all") @RequestParam(required = false) Boolean status
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident topics retrieved successfully", categoryService.listTopicsByCategory(id, status)));
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
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_CREATE + "')")
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
