package com.amalitech.hilfe.dto;

import java.util.List;

public record BulkAssignRoleResponse(
        String roleCode,
        int updatedCount,
        List<String> updatedUserIds
) {
}
