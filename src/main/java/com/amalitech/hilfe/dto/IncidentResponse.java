package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Incident;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Full incident detail returned by create, get, and update operations")
public record IncidentResponse(
        @Schema(description = "Unique incident UUID") String id,
        @Schema(description = "Auto-incremented human-readable incident number", example = "42") int incidentNo,
        @Schema(description = "Short summary of the incident") String title,
        @Schema(description = "Detailed description of the issue") String description,
        @Schema(description = "Incident topic (type) with its parent category") IncidentTopicResponse incidentType,
        @Schema(description = "Location where the incident occurred") LocationResponse location,
        @Schema(description = "Current severity/priority level", nullable = true) LookupResponse severity,
        @Schema(description = "Current lifecycle status", nullable = true) LookupResponse status,
        @Schema(description = "Agent record ID of the assigned agent, or null if unassigned", nullable = true) String assignedToId,
        @Schema(description = "Whether the incident has been read/acknowledged by the assigned agent") boolean read,
        @Schema(description = "Timestamp when the incident was closed, or null if still open", nullable = true) Instant closedAt,
        @Schema(description = "Timestamp when the incident was created (UTC)") Instant createdAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getIncidentType() != null ? IncidentTopicResponse.from(incident.getIncidentType()) : null,
                incident.getLocation() != null ? LocationResponse.from(incident.getLocation()) : null,
                incident.getSeverity() != null ? LookupResponse.from(incident.getSeverity().getId(), incident.getSeverity().getName()) : null,
                incident.getStatus() != null ? LookupResponse.from(incident.getStatus().getId(), incident.getStatus().getName()) : null,
                incident.getAssignedToId(),
                incident.isRead(),
                incident.getClosedAt(),
                incident.getCreatedAt()
        );
    }
}
