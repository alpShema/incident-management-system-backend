package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateRoleRequest(
        @Size(max = 255) String name,
        String description,
        List<@NotBlank String> permissionCodes
) {}
