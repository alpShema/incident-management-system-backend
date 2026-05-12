package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record AdminDashboardSummary(
        long totalTickets,
        long unassignedCount,
        int slaBreachCount,
        List<LabelCount> byStatus,
        List<LabelCount> byCategory,
        List<LabelCount> byTopic,
        List<AgentWorkload> agentWorkload,
        List<RecentActivity> recentActivity
) {}
