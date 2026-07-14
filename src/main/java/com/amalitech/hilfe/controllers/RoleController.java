package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Roles", description = "Role definitions and assignments")
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService roleService;

    @Operation(summary = "Create role", description = "Creates a custom role by linking existing permission codes.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('" + RbacPermissions.RBAC_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", roleService.createRole(request)));
    }

    @Operation(summary = "List roles", description = "Returns paginated roles with their permissions.")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_ROLE_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> listRoles(
            @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Roles retrieved successfully",
                PageResponse.from(roleService.listRoles(query, pageable))
        ));
    }

    @Operation(summary = "Update role", description = "Updates a custom role's name, description, and/or permission set. System-defined roles cannot be modified.")
    @PatchMapping("/{roleCode}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('" + RbacPermissions.RBAC_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable String roleCode,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", roleService.updateRole(roleCode, request)));
    }

    @Operation(summary = "Remove users from role", description = "Clears role assignment for specified users. Users not in this role are silently skipped.")
    @DeleteMapping("/{roleCode}/users")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<BulkAssignRoleResponse>> removeUsers(
            @PathVariable String roleCode,
            @Valid @RequestBody BulkAssignRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Users removed from role successfully", roleService.removeUsersFromRole(roleCode, request)));
    }

    @Operation(summary = "Bulk assign role to users", description = "Assigns one role code to multiple users in a single transaction.")
    @PatchMapping("/{roleCode}/users")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<BulkAssignRoleResponse>> bulkAssign(
            @PathVariable String roleCode,
            @Valid @RequestBody BulkAssignRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Users assigned to role successfully",
                roleService.bulkAssignRole(roleCode, request)
        ));
    }
}
