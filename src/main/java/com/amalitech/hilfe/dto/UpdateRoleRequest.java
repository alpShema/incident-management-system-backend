package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.constants.ApiMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateRoleRequest(
        @Size(max = 255, message = ApiMessages.ROLE_NAME_MAX_LENGTH)
        String name,
        String description,
        List<@NotBlank(message = ApiMessages.PERMISSION_CODE_INVALID) String> permissionCodes
) {}
