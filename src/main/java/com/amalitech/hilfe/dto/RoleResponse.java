package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Role definition and its mapped permissions")
public record RoleResponse(
        String id,
        String roleCode,
        String name,
        String description,
        Boolean systemDefined,
        List<PermissionItem> permissions
) {
    public record PermissionItem(
            String code,
            String name,
            String description
    ) {}
}
