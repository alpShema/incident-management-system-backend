package com.amalitech.hilfe.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Top-level incident counts for the dashboard")
public record DashboardStats(
        @Schema(description = "Total incidents in scope", example = "120") long totalIncidents,
        @Schema(description = "Incidents with status Open",        example = "45")  long openCount,
        @Schema(description = "Incidents with status Pending",     example = "5")   long pendingCount,
        @Schema(description = "Incidents with status In Progress", example = "8")   long inProgressCount,
        @Schema(description = "Incidents with status Closed",      example = "60")  long closedCount,
        @Schema(description = "Incidents with status Resolved",    example = "15")  long resolvedCount
) {}
