package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for updating a user's role")
public record UpdateUserRoleRequest(
        @Schema(description = "New role code to assign", example = "AGENT")
        @NotBlank(message = "Please select a role.")
        String roleCode
) {
    public UpdateUserRoleRequest(RoleCode roleCode) {
        this(roleCode == null ? null : roleCode.name());
    }
}
