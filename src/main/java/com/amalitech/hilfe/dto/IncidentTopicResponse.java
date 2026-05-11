package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;

public record IncidentTopicResponse(
        String id,
        String name,
        String description
) {
    public static IncidentTopicResponse from(IncidentType type) {
        return new IncidentTopicResponse(type.getId(), type.getName(), type.getDescription());
    }
}
