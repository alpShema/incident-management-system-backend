package com.amalitech.hilfe.notifications.content;

public record NotificationDraft(
        String userId,
        String incidentId,
        String type,
        String title,
        String message
) {
}
