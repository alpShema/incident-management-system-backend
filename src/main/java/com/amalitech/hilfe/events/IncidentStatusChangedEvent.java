package com.amalitech.hilfe.events;

public record IncidentStatusChangedEvent(
        String incidentId,
        int incidentNo,
        String actorUserId,
        String clientUserId,
        String assignedAgentUserId,
        String previousStatus,
        String newStatus,
        String reason
) {}
