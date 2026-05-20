package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.AddDepartmentMemberRequest;
import com.amalitech.hilfe.dto.AgentGroupMemberResponse;
import com.amalitech.hilfe.dto.AgentGroupRequest;
import com.amalitech.hilfe.dto.AgentGroupResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AgentGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Departments", description = "Manage departments and department membership")
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class AgentGroupController {
    private final AgentGroupService agentGroupService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<List<AgentGroupResponse>>> listDepartments() {
        return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", agentGroupService.listDepartments()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> getDepartment(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Department retrieved successfully", agentGroupService.getDepartment(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_CREATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> createDepartment(@Valid @RequestBody AgentGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", agentGroupService.createDepartment(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupResponse>> updateDepartment(
            @PathVariable String id,
            @Valid @RequestBody AgentGroupRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", agentGroupService.updateDepartment(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_DELETE + "')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable String id) {
        agentGroupService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_READ + "')")
    public ResponseEntity<ApiResponse<List<AgentGroupMemberResponse>>> listMembers(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Department members retrieved successfully", agentGroupService.listMembers(id)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupMemberResponse>> addMember(
            @PathVariable String id,
            @Valid @RequestBody AddDepartmentMemberRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department member added successfully", agentGroupService.addMember(id, request.agentId())));
    }

    @DeleteMapping("/{id}/members/{agentId}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_GROUP_UPDATE + "')")
    public ResponseEntity<ApiResponse<AgentGroupMemberResponse>> removeMember(
            @PathVariable String id,
            @PathVariable String agentId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department member removed successfully", agentGroupService.removeMember(id, agentId)));
    }
}
