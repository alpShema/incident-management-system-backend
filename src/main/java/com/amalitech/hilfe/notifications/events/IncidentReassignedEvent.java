package com.amalitech.hilfe.notifications.events;

public record IncidentReassignedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String actorName
) {
}
