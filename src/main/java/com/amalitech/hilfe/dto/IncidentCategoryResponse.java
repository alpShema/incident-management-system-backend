package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An incident category that groups related topics")
public record IncidentCategoryResponse(
        @Schema(description = "Stable category ID", example = "cat-facilities") String id,
        @Schema(description = "Category display name", example = "Facilities") String name,
        @Schema(description = "Optional description of the category", nullable = true) String description,
        @Schema(description = "Internal department this category belongs to", nullable = true) LookupResponse department,
        @Schema(description = "Category status", example = "active") String status
) {
    public static IncidentCategoryResponse from(IncidentCategory category) {
        LookupResponse department = category.getDepartment() != null
                ? LookupResponse.from(category.getDepartment().getId(), category.getDepartment().getName())
                : null;
        return new IncidentCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                department,
                category.getStatus());
    }
}
