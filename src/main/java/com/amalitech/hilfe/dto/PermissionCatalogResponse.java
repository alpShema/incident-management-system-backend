package com.amalitech.hilfe.dto;

import java.util.List;

public record PermissionCatalogResponse(
        List<RoleResponse.PermissionItem> incidentPermissions,
        List<RoleResponse.PermissionItem> agentAndGroupPermissions,
        List<RoleResponse.PermissionItem> otherPermissions
) {
}
