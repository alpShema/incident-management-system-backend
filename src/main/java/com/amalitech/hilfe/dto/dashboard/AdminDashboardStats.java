package com.amalitech.hilfe.dto.dashboard;

public record AdminDashboardStats(
        long totalIncidents,
        long openIncidents,
        long closedIncidents,
        long resolvedIncidents
) {}
