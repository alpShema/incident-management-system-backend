package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Agent Groups", description = "Manage agent groups, membership, and primary routing agents")
@RestController
@RequestMapping("/agent-groups")
@RequiredArgsConstructor
public class AgentGroupController {
    private final AgentGroupService agentGroupService;

    @Operation(summary = "List agent groups", description = "Returns active agent groups as a paginated response. Requires `agent-group.read` permission.")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<AgentGroupResponse>>> listAgentGroups(Pageable pageable) {
        Page<AgentGroupResponse> page = agentGroupService.listAgentGroups(pageable);
        return ResponseEntity.ok(ApiResponse.success("Agent groups retrieved successfully", PageResponse.from(page)));
    }

    @Operation(summary = "Get an agent group", description = "Returns an agent group by ID. Requires `agent-group.read` permission.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> getAgentGroup(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Agent group retrieved successfully", agentGroupService.getAgentGroup(id)));
    }

    @Operation(summary = "Create an agent group", description = "Creates an agent group. Requires `agent-group.create` permission.")
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_CREATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> createAgentGroup(@Valid @RequestBody AgentGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Agent group created successfully", agentGroupService.createAgentGroup(request)));
    }

    @Operation(summary = "Update an agent group", description = "Updates an agent group name, description, and optional primary agent. Requires `agent-group.update` permission.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> updateAgentGroup(
            @PathVariable String id,
            @Valid @RequestBody AgentGroupRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Agent group updated successfully", agentGroupService.updateAgentGroup(id, request)));
    }

    @Operation(summary = "Deactivate an agent group", description = "Soft-deactivates an agent group if no agents are assigned to it. Requires `agent-group.delete` permission.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_DELETE + "')")
    public ResponseEntity<Void> deleteAgentGroup(@PathVariable String id) {
        agentGroupService.deleteAgentGroup(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List agent group members", description = "Returns agents assigned to an agent group. Requires `agent-group.read` permission.")
    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<List<AgentGroupMemberResponse>>> listMembers(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Agent group members retrieved successfully", agentGroupService.listMembers(id)));
    }

    @Operation(summary = "Add agent group member", description = "Assigns an agent to an agent group. Requires `agent-group.update` permission.")
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupMemberResponse>> addMember(
            @PathVariable String id,
            @Valid @RequestBody AddAgentGroupMemberRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Agent group member added successfully", agentGroupService.addMember(id, request.agentId())));
    }

    @Operation(summary = "Remove agent group member", description = "Removes an agent from an agent group. Requires `agent-group.update` permission.")
    @DeleteMapping("/{id}/members/{agentId}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupMemberResponse>> removeMember(
            @PathVariable String id,
            @PathVariable String agentId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Agent group member removed successfully", agentGroupService.removeMember(id, agentId)));
    }
}
