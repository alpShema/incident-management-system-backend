package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating an internal department")
public record CreateDepartmentRequest(
        @Schema(description = "Department name", example = "Facilities")
        @NotBlank(message = "Department name is required and cannot be blank.")
        @Size(max = 100, message = "Department name must not exceed 100 characters.")
        String name,

        @Schema(description = "Optional department description", nullable = true)
        @Size(max = 1000, message = "Department description must not exceed 1000 characters.")
        String description,

        @Schema(description = "User ID of the department's head. Must be an active Admin or Admin-Agent.", example = "user-123")
        @NotBlank(message = "Department head is required.")
        String headUserId
) {}