package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateUserRoleRequest;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UserResolver {

    private final UserService userService;

    @QueryMapping
    @PreAuthorize("hasAuthority('rbac.role.read')")
    public PageResponse<UserRoleSummaryResponse> users(
            @Argument String query,
            @Argument String roleCode,
            @Argument String locationId,
            @Argument Boolean status,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                userService.getUsers(query, roleCode, locationId, status, PageInput.toPageable(page))
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public UserRoleSummaryResponse updateUserRole(
            @Argument String userId,
            @Argument UpdateUserRoleRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("User role updated successfully");
        return userService.assignUserRole(principal.userId(), userId, input.roleCode());
    }

    @MutationMapping
    public UserRoleSummaryResponse updateUserStatus(
            @Argument String userId,
            @Argument UpdateUserStatusInput input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("User status updated successfully");
        return userService.updateUserStatus(principal.userId(), RoleCode.valueOf(principal.roleCode()), userId, input.status());
    }

    public record UpdateUserStatusInput(Boolean status) {}
}
