package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for partially updating an incident category. All fields are optional — only supplied fields are updated.")
public record UpdateIncidentCategoryRequest(
        @Schema(description = "New display name for the category", nullable = true, example = "Updated Facilities")
        String name,

        @Schema(description = "New description for the category", nullable = true, example = "Issues related to building and room facilities")
        String description,

        @Schema(description = "New department ID this category should belong to", nullable = true, example = "dept-facilities")
        String departmentId
) {}
