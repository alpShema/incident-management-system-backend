package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;

public record IncidentTopicResponse(
        String id,
        String name,
        String description,
        LookupResponse category
) {
    public static IncidentTopicResponse from(IncidentType type) {
        LookupResponse category = type.getCategory() != null
                ? LookupResponse.from(type.getCategory().getId(), type.getCategory().getName())
                : null;
        return new IncidentTopicResponse(type.getId(), type.getName(), type.getDescription(), category);
    }
}
