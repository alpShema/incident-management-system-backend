package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PermissionCatalogResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Permissions", description = "Permission catalog and grouping")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {
    private final RoleService roleService;

    @Operation(summary = "Get grouped permission catalog", description = "Returns permissions grouped into five categories: incident, agent-and-group, settings, user, and report.")
    @GetMapping("/catalog")
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_PERMISSION_READ + "')")
    public ResponseEntity<ApiResponse<PermissionCatalogResponse>> catalog() {
        return ResponseEntity.ok(ApiResponse.success("Permission catalog retrieved successfully", roleService.permissionCatalog()));
    }

    @Operation(summary = "Get all permissions (public)", description = "Returns the full permission catalog. Does not require authentication.")
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<PermissionCatalogResponse>> all() {
        return ResponseEntity.ok(ApiResponse.success("Permission catalog retrieved successfully", roleService.permissionCatalog()));
    }
}
