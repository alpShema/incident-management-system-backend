package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A single audit log entry recording a system action")
public record ActivityLogResponse(
        @Schema(description = "Auto-incremented log entry ID") Long id,
        @Schema(description = "User ID of the person who performed the action") String actorUserId,
        @Schema(description = "User ID of the person affected by the action, if applicable", nullable = true) String targetUserId,
        @Schema(description = "Action code describing what happened (e.g. INCIDENT_STATUS_CHANGED, ROLE_UPDATED)") String action,
        @Schema(description = "Type of the subject entity (e.g. INCIDENT, USER)", nullable = true) String subjectType,
        @Schema(description = "ID of the subject entity", nullable = true) String subjectId,
        @Schema(description = "Human-readable description of the action") String description,
        @Schema(description = "Additional JSON metadata associated with the action", nullable = true) String metadata,
        @Schema(description = "Timestamp when the action occurred (UTC)") Instant createdAt
) {
}
