package com.amalitech.hilfe.notifications.events;

public record IncidentReopenedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String actorName
) {
}
