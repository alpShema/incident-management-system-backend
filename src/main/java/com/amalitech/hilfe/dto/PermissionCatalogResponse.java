package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Grouped permission catalog for role configuration UI")
public record PermissionCatalogResponse(
        @Schema(description = "Incident management permissions (incident.*)")
        List<RoleResponse.PermissionItem> incidentPermissions,

        @Schema(description = "Agent and agent group management permissions (agent.*, agent-group.*)")
        List<RoleResponse.PermissionItem> agentAndGroupPermissions,

        @Schema(description = "System settings and lookup management permissions (department.*, status.*, severity.*, location.*, incident-type.*, incident-category.*, system.*)")
        List<RoleResponse.PermissionItem> settingsPermissions,

        @Schema(description = "User and role management permissions (rbac.*)")
        List<RoleResponse.PermissionItem> userPermissions,

        @Schema(description = "Dashboard and reporting permissions (dashboard.*)")
        List<RoleResponse.PermissionItem> reportPermissions
) {
}
