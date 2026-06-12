package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Incident;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Full incident detail returned by create, get, and update operations")
public record IncidentResponse(
        @Schema(description = "Unique incident ID", example = "c26c5ba5-f654-4829-9675-d09704e667be") String id,
        @Schema(description = "Auto-incremented human-readable incident number", example = "42") int incidentNo,
        @Schema(description = "Short summary of the incident") String title,
        @Schema(description = "Detailed description of the issue") String description,
        @Schema(description = "Incident topic (type) with its parent category") IncidentTopicResponse incidentTopic,
        @Schema(description = "Location where the incident occurred") LocationResponse location,
        @Schema(description = "Current priority level", nullable = true) LookupResponse priority,
        @Schema(description = "Current lifecycle status", nullable = true) LookupResponse status,
        @Schema(description = "The user who created this incident") CreatorResponse createdBy,
        @Schema(description = "Assigned agent details, or null if unassigned", nullable = true) AssignedAgentResponse assignedTo,
        @Schema(description = "Whether the incident has been read/acknowledged by the assigned agent") boolean read,
        @Schema(description = "Reason provided when the status was set to Pending or Reopened, null otherwise", nullable = true) String statusReason,
        @Schema(description = "Timestamp when the incident was marked resolved, or null if not yet resolved", nullable = true) Instant resolvedAt,
        @Schema(description = "Timestamp when the incident was closed, or null if still open", nullable = true) Instant closedAt,
        @Schema(description = "Timestamp when the incident was created (UTC)") Instant createdAt,
        @Schema(description = "Timestamp when the incident was last updated (UTC)") Instant updatedAt,
        @Schema(description = "Current SLA state for the incident", nullable = true) IncidentSlaResponse sla,
        @Schema(description = "File attachments (populated on detail view, null on list view)", nullable = true) List<MediaResponse> attachments
) {
    public static IncidentResponse from(Incident incident) {
        return from(incident, null, null);
    }

    public static IncidentResponse from(Incident incident, List<MediaResponse> attachments) {
        return from(incident, attachments, null);
    }

    public static IncidentResponse from(Incident incident, List<MediaResponse> attachments, IncidentSlaResponse sla) {
        return new IncidentResponse(
                incident.getId(),
                incident.getIncidentNo(),
                incident.getTitle(),
                incident.getDescription(),
                incident.getIncidentType() != null ? IncidentTopicResponse.from(incident.getIncidentType()) : null,
                incident.getLocation() != null ? LocationResponse.from(incident.getLocation()) : null,
                incident.getSeverity() != null ? LookupResponse.from(incident.getSeverity().getId(), incident.getSeverity().getName()) : null,
                incident.getStatus() != null ? LookupResponse.from(incident.getStatus().getId(), incident.getStatus().getName()) : null,
                CreatorResponse.from(incident.getCreatedBy()),
                AssignedAgentResponse.from(incident.getAssignedTo()),
                incident.isRead(),
                incident.getStatusReason(),
                incident.getResolvedAt(),
                incident.getClosedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),
                sla,
                attachments
        );
    }
}
