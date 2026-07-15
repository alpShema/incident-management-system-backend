package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.constants.ApiMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank(message = "Role name is required and cannot be blank.")
        @Size(max = 255, message = ApiMessages.ROLE_NAME_MAX_LENGTH)
        String name,
        @NotBlank(message = "Role description is required and cannot be blank.")
        @Size(max = 1000, message = "Role description must not exceed 1000 characters.")
        String description,
        @NotEmpty(message = "Please select at least one permission for this role.")
        List<@NotBlank(message = ApiMessages.PERMISSION_CODE_INVALID) String> permissionCodes
) {
}
