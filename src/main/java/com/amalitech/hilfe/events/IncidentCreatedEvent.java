package com.amalitech.hilfe.events;

public record IncidentCreatedEvent(
        String incidentId,
        int incidentNo,
        String clientUserId,
        String assignedAgentUserId,
        java.util.List<String> adminUserIds
) {}
