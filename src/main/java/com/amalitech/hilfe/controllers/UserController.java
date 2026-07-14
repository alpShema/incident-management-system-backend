package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateUserRoleRequest;
import com.amalitech.hilfe.dto.UpdateUserStatusRequest;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "User management — search, filter, and list users; update role assignments")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
        summary = "List users",
        description = "Returns a paginated list of all users showing their ID, name, email, role, status, and office location. "
                    + "Accepts an optional `query` keyword that searches across full name and email. "
                    + "Optionally filter by role code (`roleCode`) — accepts any role code from the database (e.g., CLIENT, AGENT, ADMIN, or custom roles). Also filter by office location (`locationId`) or account status (`status`). Both `locationId` and `status` filters are case-insensitive. "
                    + "All filters are independent and can be combined with each other or with `query` to narrow results. "
                    + "Supports sorting via `sort=field,direction` (e.g. `sort=fullName,asc`). "
                    + "Requires `rbac.role.read` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Users retrieved")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_ROLE_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<UserRoleSummaryResponse>>> listUsers(
            @Parameter(description = "Keyword search across full name and email") @RequestParam(required = false) String query,
            @Parameter(description = "Filter by role code (e.g., CLIENT, AGENT, ADMIN, or custom roles)") @RequestParam(required = false) String roleCode,
            @Parameter(description = "Filter by office location ID (case-insensitive)") @RequestParam(required = false) String locationId,
            @Parameter(description = "Filter by account status. Pass `true` for active users, `false` for inactive users, or omit for all.") @RequestParam(required = false) Boolean status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully",
                PageResponse.from(userService.getUsers(query, roleCode, locationId, status, pageable))));
    }

    @Operation(
        summary = "Update a user's role",
        description = "Assigns a new role to the specified user. Valid roles: CLIENT, AGENT, ADMIN, ADMIN_AGENT, SUPER_ADMIN. Requires `rbac.user.role.update` permission."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Role updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid role value")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public ResponseEntity<ApiResponse<UserRoleSummaryResponse>> assignUserRole(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Target user ID") @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("User role updated successfully", userService.assignUserRole(principal.userId(), userId, request.roleCode())));
    }

    @Operation(
        summary = "Update user status",
        description = "Updates a user's account status (active/inactive). Users can update their own status. Admins can update any user's status."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Status updated")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Cannot update another user's status")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    @PatchMapping("/{userId}/status")
    public ResponseEntity<ApiResponse<UserRoleSummaryResponse>> updateUserStatus(
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Parameter(description = "Target user ID") @PathVariable String userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "User status updated",
                userService.updateUserStatus(principal.userId(), parseRoleCode(principal.roleCode()), userId, request.status())
        ));
    }

    private RoleCode parseRoleCode(String roleCode) {
        if (roleCode == null) return null;
        try {
            return RoleCode.valueOf(roleCode.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
