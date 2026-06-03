package com.amalitech.hilfe.notifications.events;

public record IncidentAssignedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String actorName
) {
}
