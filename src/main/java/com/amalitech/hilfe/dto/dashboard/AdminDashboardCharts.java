package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record AdminDashboardCharts(
        List<LabelCount> byStatus,
        List<MonthlyCount> monthlyTrend
) {}
