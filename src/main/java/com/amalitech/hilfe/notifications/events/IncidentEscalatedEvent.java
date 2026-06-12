package com.amalitech.hilfe.notifications.events;

public record IncidentEscalatedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo
) {
}
