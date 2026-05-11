package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentCategory;

public record IncidentCategoryResponse(
        String id,
        String name,
        String description
) {
    public static IncidentCategoryResponse from(IncidentCategory category) {
        return new IncidentCategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
