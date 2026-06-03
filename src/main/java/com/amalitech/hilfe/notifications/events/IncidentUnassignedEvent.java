package com.amalitech.hilfe.notifications.events;

public record IncidentUnassignedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String actorName,
        String newAssigneeName
) {
}
