package com.amalitech.hilfe.notifications.events;

public record IncidentAutoClosedClientEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo
) {
}
