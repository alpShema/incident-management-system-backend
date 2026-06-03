package com.amalitech.hilfe.notifications.events;

public record IncidentAutoAssignedClientEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo
) {
}
