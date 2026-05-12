package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record AgentDashboardSummary(
        int totalAssigned,
        List<StatusCount> byStatus,
        int highCriticalCount,
        int unacknowledgedCount,
        int closedThisWeek,
        Double avgResolutionHours,
        List<DashboardIncident> recentlyUpdated
) {}
