package com.amalitech.hilfe.notifications.events;

public record IncidentStatusChangedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String previousStatus,
        String newStatus,
        String reason
) {
}
