package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkAssignRoleRequest(
        @NotEmpty List<String> userIds
) {
}
