package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record TrendSeries(String label, List<MonthlyCount> data) {}
