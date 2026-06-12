package com.amalitech.hilfe.notifications.events;

public record IncidentClientReassignedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String actorName,
        String newAssigneeName
) {
}
