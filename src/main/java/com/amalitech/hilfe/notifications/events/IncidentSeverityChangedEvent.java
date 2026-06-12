package com.amalitech.hilfe.notifications.events;

public record IncidentSeverityChangedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String previousSeverity,
        String newSeverity,
        String actorName
) {
}
