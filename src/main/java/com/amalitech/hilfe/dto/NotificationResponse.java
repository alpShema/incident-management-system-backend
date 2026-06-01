package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "An in-app notification for a user")
public record NotificationResponse(
        @Schema(description = "Notification ID") String id,
        @Schema(description = "Incident this notification relates to", nullable = true) String incidentId,
        @Schema(description = "Notification type, e.g. INCIDENT_STATUS_CHANGED") String type,
        @Schema(description = "Short notification title") String title,
        @Schema(description = "Full notification message") String message,
        @Schema(description = "Whether the user has read this notification") boolean read,
        @Schema(description = "When the notification was created (UTC)") Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getIncidentId(), n.getType(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }
}
