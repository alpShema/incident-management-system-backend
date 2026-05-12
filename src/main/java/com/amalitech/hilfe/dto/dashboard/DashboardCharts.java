package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record DashboardCharts(List<LabelCount> byStatus, List<TrendSeries> trends) {}
