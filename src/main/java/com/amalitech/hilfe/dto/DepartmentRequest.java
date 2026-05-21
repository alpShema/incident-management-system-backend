package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating or updating an internal department")
public record DepartmentRequest(
        @Schema(description = "Department name", example = "Facilities")
        @NotBlank
        @Size(max = 100, message = "must not exceed 100 characters")
        String name,

        @Schema(description = "Optional department description", nullable = true)
        @Size(max = 1000, message = "must not exceed 1000 characters")
        String description
) {}
