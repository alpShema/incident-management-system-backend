package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A single audit log entry recording a system action")
public record ActivityLogResponse(
        @Schema(description = "Auto-incremented log entry ID") Long id,
        @Schema(description = "Full name of the person who performed the action") String actorName,
        @Schema(description = "Profile image URL of the person who performed the action", nullable = true) String actorProfileUrl,
        @Schema(description = "Full name of the person affected by the action, if applicable", nullable = true) String targetName,
        @Schema(description = "Action code describing what happened (e.g. INCIDENT_STATUS_CHANGED, ROLE_UPDATED)") String action,
        @Schema(description = "Type of the subject entity (e.g. INCIDENT, USER)", nullable = true) String subjectType,
        @Schema(description = "Sequential incident number when subjectType is INCIDENT, null otherwise", nullable = true) Integer subjectNo,
        @Schema(description = "Human-readable description of the action") String description,
        @Schema(description = "Additional JSON metadata associated with the action", nullable = true) String metadata,
        @Schema(description = "Timestamp when the action occurred (UTC)") Instant createdAt
) {
}
