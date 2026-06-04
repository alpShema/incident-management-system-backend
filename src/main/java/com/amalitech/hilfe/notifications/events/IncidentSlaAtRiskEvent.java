package com.amalitech.hilfe.notifications.events;

public record IncidentSlaAtRiskEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String slaType,
        long minutesRemaining
) {
}
