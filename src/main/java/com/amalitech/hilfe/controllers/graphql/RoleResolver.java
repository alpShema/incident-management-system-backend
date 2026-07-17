package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.BulkAssignRoleRequest;
import com.amalitech.hilfe.dto.BulkAssignRoleResponse;
import com.amalitech.hilfe.dto.CreateRoleRequest;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.PermissionCatalogResponse;
import com.amalitech.hilfe.dto.RoleResponse;
import com.amalitech.hilfe.dto.UpdateRoleRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class RoleResolver {

    private final RoleService roleService;

    @QueryMapping
    @PreAuthorize("hasAuthority('rbac.role.read')")
    public PageResponse<RoleResponse> roles(@Argument String query, @Argument PageInput page) {
        return PageInput.toPageResponse(
                roleService.listRoles(query, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('rbac.permission.read')")
    public PermissionCatalogResponse permissionCatalog() {
        return roleService.permissionCatalog();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('rbac.role.update')")
    public RoleResponse createRole(@Argument CreateRoleRequest input) {
        GraphQlResponseMessage.set("Role created successfully");
        return roleService.createRole(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('" + RbacPermissions.RBAC_USER_ROLE_UPDATE + "')")
    public BulkAssignRoleResponse bulkAssignRole(@Argument String roleCode, @Argument BulkAssignRoleRequest input) {
        GraphQlResponseMessage.set("Users assigned to role successfully");
        return roleService.bulkAssignRole(roleCode, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('rbac.role.update')")
    public RoleResponse updateRole(@Argument String roleCode, @Argument UpdateRoleRequest input) {
        GraphQlResponseMessage.set("Role updated successfully");
        return roleService.updateRole(roleCode, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN') and hasAuthority('rbac.user-role.update')")
    public BulkAssignRoleResponse removeRoleUsers(@Argument String roleCode, @Argument BulkAssignRoleRequest input) {
        GraphQlResponseMessage.set("Users removed from role successfully");
        return roleService.removeUsersFromRole(roleCode, input);
    }
}
