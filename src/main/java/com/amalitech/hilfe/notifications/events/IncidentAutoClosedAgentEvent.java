package com.amalitech.hilfe.notifications.events;

public record IncidentAutoClosedAgentEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo
) {
}
