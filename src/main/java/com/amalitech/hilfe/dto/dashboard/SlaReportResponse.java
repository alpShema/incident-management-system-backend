package com.amalitech.hilfe.dto.dashboard;

import java.util.List;

public record SlaReportResponse(
        long trackedIncidents,
        long responseBreachCount,
        long resolutionBreachCount,
        Double averageResponseMinutes,
        Double averageResolutionMinutes,
        List<SlaSeverityBreakdown> bySeverity
) {
}
