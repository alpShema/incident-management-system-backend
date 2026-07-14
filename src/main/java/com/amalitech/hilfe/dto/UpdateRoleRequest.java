package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateRoleRequest(
        @Size(max = 255, message = "Role name must not exceed 255 characters.")
        String name,
        String description,
        List<@NotBlank(message = "One or more selected permissions are invalid.") String> permissionCodes
) {}
