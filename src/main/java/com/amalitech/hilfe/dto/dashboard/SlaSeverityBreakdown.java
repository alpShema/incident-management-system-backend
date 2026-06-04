package com.amalitech.hilfe.dto.dashboard;

public record SlaSeverityBreakdown(
        String severityId,
        String severityName,
        long trackedIncidents,
        long responseBreachCount,
        long resolutionBreachCount,
        Double averageResponseMinutes,
        Double averageResolutionMinutes
) {
}
