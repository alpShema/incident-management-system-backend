package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Incident;

import java.time.Instant;

public record IncidentResponse(
        String id,
        int incidentNo,
        String title,
        IncidentTopicResponse incidentType,
        LocationResponse location,
        LookupResponse severity,
        LookupResponse status,
        String assignedToId,
        boolean read,
        Instant createdAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getTitle(),
                incident.getIncidentType() != null ? IncidentTopicResponse.from(incident.getIncidentType()) : null,
                incident.getLocation() != null ? LocationResponse.from(incident.getLocation()) : null,
                incident.getSeverity() != null ? LookupResponse.from(incident.getSeverity().getId(), incident.getSeverity().getName()) : null,
                incident.getStatus() != null ? LookupResponse.from(incident.getStatus().getId(), incident.getStatus().getName()) : null,
                incident.getAssignedToId(),
                incident.isRead(),
                incident.getCreatedAt()
        );
    }
}
