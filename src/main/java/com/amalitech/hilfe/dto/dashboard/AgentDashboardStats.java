package com.amalitech.hilfe.dto.dashboard;

public record AgentDashboardStats(
        long totalAssigned,
        long openCount,
        long closedCount,
        long resolvedCount
) {}
