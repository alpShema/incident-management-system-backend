package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record AgentDashboardCharts(
        List<StatusCount> byStatus,
        List<MonthlyCount> myIncidentsTrend,
        List<MonthlyCount> assignedIncidentsTrend
) {}
