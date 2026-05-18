package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for updating a user's role")
public record UpdateUserRoleRequest(
        @Schema(description = "New role to assign. Valid values: CLIENT, AGENT, ADMIN, SUPER_ADMIN", example = "AGENT")
        @NotNull RoleCode roleCode
) {
}
