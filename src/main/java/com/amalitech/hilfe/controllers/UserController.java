package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateUserRoleRequest;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "User role management — list users with their roles and update role assignments")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
        summary = "List users with roles",
        description = "Returns a paginated list of all users showing their ID, name, email, and current role. Requires `rbac.role.read` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_ROLE_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<UserRoleSummaryResponse>>> userRoles(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("User roles retrieved successfully", PageResponse.from(userService.getUserRoles(pageable))));
    }

    @Operation(
        summary = "Update a user's role",
        description = "Assigns a new role to the specified user. Valid roles: CLIENT, AGENT, ADMIN, SUPER_ADMIN. Requires `rbac.user.role.update` permission."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role value"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<UserRoleSummaryResponse>> assignUserRole(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Target user ID") @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", userService.assignUserRole(principal.userId(), userId, request.roleCode())));
    }
}
