package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentGroupService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Agent Groups", description = "Manage department-linked agent groups and membership")
@RestController
@RequestMapping("/agent-groups")
@RequiredArgsConstructor
public class AgentGroupController {
    private final AgentGroupService agentGroupService;
    private static final String MSG_AGENT_GROUPS_RETRIEVED = "Agent groups retrieved successfully";

    @Operation(
            summary = "List agent groups by category",
            description = "Returns active agent groups that belong to the same department as the given category. "
                    + "Intended for populating the agent group dropdown during topic creation or editing. "
                    + "Returns an empty list if the category's department has no active agent groups. "
                    + "Requires `agent-group.read` permission."
    )
    @GetMapping("/by-category/{categoryId}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<List<LookupResponse>>> listAgentGroupsByCategory(@PathVariable String categoryId) {
        return ResponseEntity.ok(ApiResponse.success(
                MSG_AGENT_GROUPS_RETRIEVED,
                agentGroupService.listAgentGroupsByCategory(categoryId)));
    }

    @Operation(
            summary = "List agent groups",
            description = "Returns active agent groups as a paginated response. Each agent group belongs to one internal department. "
                    + "Accepts an optional `query` keyword that searches across name, description, and department name. "
                    + "Accepts an optional `departmentId` filter. "
                    + "Requires `agent-group.read` permission."
    )
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentGroupResponse>>> listAgentGroups(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String departmentId,
            Pageable pageable
    ) {
        Page<AgentGroupResponse> page = agentGroupService.listAgentGroups(query, departmentId, pageable);
        return ResponseEntity.ok(ApiResponse.success(MSG_AGENT_GROUPS_RETRIEVED, PageResponse.from(page)));
    }

    @Operation(
            summary = "List all agent groups",
            description = "Returns a paginated list of agent groups regardless of status. "
                    + "Accepts an optional `status` filter: `active` returns only active groups, `deactivated` returns only deactivated groups, "
                    + "`all` (default when omitted) returns both. "
                    + "Also accepts optional `query` and `departmentId` filters. "
                    + "Requires `agent-group.read` permission."
    )
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentGroupResponse>>> listAllAgentGroups(
            @Parameter(description = "Filter by status. Pass `true` for active, `false` for inactive, or omit for all.") @RequestParam(required = false) Boolean status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String departmentId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                MSG_AGENT_GROUPS_RETRIEVED,
                PageResponse.from(agentGroupService.listAllAgentGroups(status, query, departmentId, pageable))));
    }

    @Operation(summary = "Get an agent group", description = "Returns an agent group by ID, including its linked department. Inactive groups remain retrievable for admin management. Requires `agent-group.read` permission.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> getAgentGroup(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Agent group retrieved successfully", agentGroupService.getAgentGroup(id)));
    }

    @Operation(
            summary = "Create an agent group",
            description = "Creates an agent group under an internal department. `departmentId` is required on create. "
                    + "Optionally pass `agentIds` to bulk-add members immediately. "
                    + "Optionally pass `topicIds` to assign incident topics to this group immediately. "
                    + "Requires `agent-group.create` permission.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = AgentGroupRequest.class),
                            examples = @ExampleObject(
                                    name = "Create agent group",
                                    value = """
                                            {
                                              "name": "Facilities Support",
                                              "description": "Handles facilities-related incident routing",
                                              "departmentId": "dept-facilities",
                                              "agentIds": ["agent-seed-001", "agent-seed-002"],
                                              "topicIds": ["topic-id-001"]
                                            }
                                            """
                            )
                    )
            )
    )
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_CREATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> createAgentGroup(@Valid @RequestBody AgentGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Agent group created successfully", agentGroupService.createAgentGroup(request)));
    }

    @Operation(
            summary = "Update an agent group",
            description = "Updates an agent group's name, description, or department. Requires `agent-group.update` permission."
    )
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> updateAgentGroup(
            @PathVariable String id,
            @Valid @RequestBody AgentGroupRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Agent group updated successfully", agentGroupService.updateAgentGroup(id, request)));
    }

    @Operation(
            summary = "Update agent group status",
            description = "Activates or deactivates an agent group. `status=true` activates and `status=false` deactivates. "
                    + "Existing member associations are preserved across deactivation and reactivation. "
                    + "Requires `agent-group.delete` permission."
    )
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_DELETE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> updateAgentGroupStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @PathVariable String id,
            @Valid @RequestBody UpdateAgentGroupStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Agent group status updated successfully",
                agentGroupService.updateAgentGroupStatus(principal.userId(), id, request.status())));
    }

    @Operation(summary = "List agent group members", description = "Returns agents linked to an agent group. Agents can belong to multiple agent groups. Requires `agent-group.read` permission.")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<List<AgentGroupMemberResponse>>> listMembers(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Agent group members retrieved successfully", agentGroupService.listMembers(id)));
    }

    @Operation(summary = "Add agent group member", description = "Links an agent to an agent group. This does not remove the agent from other groups. Requires `agent-group.update` permission.")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupMemberResponse>> addMember(
            @PathVariable String id,
            @Valid @RequestBody AddAgentGroupMemberRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Agent group member added successfully", agentGroupService.addMember(id, request.agentId())));
    }

}
