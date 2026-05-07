package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull RoleCode roleCode
) {
}
