package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating an incident category")
public record IncidentCategoryRequest(
        @Schema(description = "Unique display name of the category", example = "Facilities")
        @NotBlank(message = "Category name is required and cannot be blank.")
        String name,

        @Schema(description = "Optional longer description of the category", example = "Issues related to building and room facilities", nullable = true)
        String description,

        @Schema(description = "Internal department ID this category belongs to", example = "dept-facilities")
        @NotBlank(message = "Please select a department for this category.")
        String departmentId
) {}
