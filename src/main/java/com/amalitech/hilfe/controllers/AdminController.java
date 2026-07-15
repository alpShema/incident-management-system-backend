package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admins", description = "Admin agent access management")
@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;

    @Operation(
            summary = "Grant agent access to an admin",
            description = "Creates an Agent record for the given admin user (status=true), enabling them to be "
                    + "assigned incidents. If the admin already has an active agent record, returns it unchanged "
                    + "(idempotent). If a deactivated record exists, it is reactivated. "
                    + "The admin's ADMIN role and permissions are not affected. "
                    + "Requires `agent.create` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agent access granted")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User is not an admin")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @PostMapping("/{userId}/agent-access")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_CREATE + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> grantAgentAccess(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success("Agent access granted successfully", adminService.grantAgentAccess(userId)));
    }

    @Operation(
            summary = "Revoke agent access from an admin",
            description = "Sets the admin's Agent record to inactive (status=false), removing them from the "
                    + "assignable agent pool. The record is preserved — all incidents previously assigned to them "
                    + "remain intact. If already revoked, returns the current state unchanged (idempotent). "
                    + "Requires `agent.create` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agent access revoked")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User is not an admin")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found or has no agent access")
    @DeleteMapping("/{userId}/agent-access")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_CREATE + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> revokeAgentAccess(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success("Agent access revoked successfully", adminService.revokeAgentAccess(userId)));
    }

    @Operation(
            summary = "Get agent access status for an admin",
            description = "Returns the Agent record for the given admin user, if one exists. "
                    + "The `status` field indicates whether agent access is currently active. "
                    + "Requires `agent.read` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Agent access retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "User is not an admin")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found or has no agent access")
    @GetMapping("/{userId}/agent-access")
    @PreAuthorize("hasAuthority('" + RbacPermissions.AGENT_READ + "')")
    public ResponseEntity<ApiResponse<AgentResponse>> getAgentAccess(@PathVariable String userId) {
        return ResponseEntity.ok(ApiResponse.success("Agent access retrieved successfully", adminService.getAgentAccess(userId)));
    }
}
