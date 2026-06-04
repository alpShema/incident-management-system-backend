package com.amalitech.hilfe.notifications.events;

public record IncidentSlaBreachedEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String slaType,
        long minutesOverdue
) {
}
