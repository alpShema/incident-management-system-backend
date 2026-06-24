package com.amalitech.hilfe.notifications.events;

public record NewIncidentMessageEvent(
        String recipientUserId,
        String incidentId,
        int incidentNo,
        String senderName,
        String preview
) {
}
