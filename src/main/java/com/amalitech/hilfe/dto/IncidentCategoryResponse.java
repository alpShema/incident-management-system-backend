package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An incident category that groups related topics")
public record IncidentCategoryResponse(
        @Schema(description = "Stable category ID") String id,
        @Schema(description = "Category display name", example = "Facilities") String name,
        @Schema(description = "Optional description of the category", nullable = true) String description
) {
    public static IncidentCategoryResponse from(IncidentCategory category) {
        return new IncidentCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
