package com.amalitech.hilfe.notifications.events;

public record IncidentPendingEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String reason
) {
}
