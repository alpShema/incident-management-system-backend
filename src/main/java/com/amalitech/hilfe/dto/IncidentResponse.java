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
        @Schema(description = "The user who created this incident, or null when masked for confidentiality", nullable = true) CreatorResponse createdBy,
        @Schema(description = "Assigned agent details, or null if unassigned or masked for confidentiality", nullable = true) AssignedAgentResponse assignedTo,
        @Schema(description = "Whether this incident belongs to a confidential topic. When true and the caller isn't "
                + "a member of the topic's linked agent group, title/description are replaced with a redacted "
                + "placeholder and createdBy/assignedTo/incidentTopic are masked (null) in list views.") boolean confidential,
        @Schema(description = "Whether the incident has been read. Shared across all viewers of the All Incidents, "
                + "Department Assigned Incidents, and Assigned Incidents tables — not private to any one user.") boolean read,
        @Schema(description = "Reason provided when the status was set to Pending or Reopened, null otherwise", nullable = true) String statusReason,
        @Schema(description = "Timestamp when the incident was marked resolved, or null if not yet resolved", nullable = true) Instant resolvedAt,
        @Schema(description = "Timestamp when the incident was closed, or null if still open", nullable = true) Instant closedAt,
        @Schema(description = "Timestamp when the incident was created (UTC)") Instant createdAt,
        @Schema(description = "Timestamp when the incident was last updated (UTC)") Instant updatedAt,
        @Schema(description = "Current SLA state for the incident", nullable = true) IncidentSlaResponse sla,
        @Schema(description = "File attachments (populated on detail view, null on list view)", nullable = true) List<MediaResponse> attachments
) {
    // Both fields are non-null in the GraphQL schema (Incident.title/description: String!) --
    // masked() must substitute a value rather than null, or the whole list response would null
    // out per GraphQL's non-null propagation rules.
    private static final String MASKED_PLACEHOLDER = "**********";

    public static IncidentResponse from(Incident incident) {
        return from(incident, null, null);
    }

    public static IncidentResponse from(Incident incident, List<MediaResponse> attachments) {
        return from(incident, attachments, null);
    }

    public static IncidentResponse from(Incident incident, List<MediaResponse> attachments, IncidentSlaResponse sla) {
        boolean confidential = incident.getIncidentType() != null && incident.getIncidentType().isConfidential();
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
                confidential,
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

    /**
     * HV-1619: list-view row for a confidential incident, seen by someone outside the topic's
     * linked agent group. Only the incident number and the confidential flag (drives the lock
     * icon) survive — title/description become a redacted placeholder (they're non-null in the
     * GraphQL schema), and creator, category/topic, and assignee are masked (null).
     */
    public IncidentResponse masked() {
        return new IncidentResponse(
                id, incidentNo, MASKED_PLACEHOLDER, MASKED_PLACEHOLDER, null, location, priority, status,
                null, null, true, read, statusReason, resolvedAt, closedAt,
                createdAt, updatedAt, sla, null);
    }
}
