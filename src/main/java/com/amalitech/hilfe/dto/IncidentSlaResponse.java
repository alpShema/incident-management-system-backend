package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Computed SLA state for an incident")
public record IncidentSlaResponse(
        @Schema(description = "Configured response SLA threshold snapshot in minutes", nullable = true) Integer responseThresholdMinutes,
        @Schema(description = "Configured resolution SLA threshold snapshot in minutes", nullable = true) Integer resolutionThresholdMinutes,
        @Schema(description = "Configured response SLA threshold snapshot in seconds", nullable = true) Long responseThresholdSeconds,
        @Schema(description = "Configured resolution SLA threshold snapshot in seconds", nullable = true) Long resolutionThresholdSeconds,
        @Schema(description = "Response SLA due time", nullable = true) Instant responseDueAt,
        @Schema(description = "Resolution SLA due time", nullable = true) Instant resolutionDueAt,
        @Schema(description = "Timestamp of the first assigned-agent response", nullable = true) Instant firstResponseAt,
        @Schema(description = "Timestamp when response SLA was breached", nullable = true) Instant responseBreachedAt,
        @Schema(description = "Timestamp when resolution SLA was breached", nullable = true) Instant resolutionBreachedAt,
        @Schema(description = "Current response SLA status", nullable = true) String responseStatus,
        @Schema(description = "Current resolution SLA status", nullable = true) String resolutionStatus,
        @Schema(description = "Whether SLA timing is currently paused") boolean paused,
        @Schema(description = "Frozen/elapsed response time in milliseconds once tracking has stopped (agent responded, or incident resolved/closed)", nullable = true) Long responseElapsedMs,
        @Schema(description = "Frozen/elapsed resolution time in milliseconds once tracking has stopped (incident resolved or closed)", nullable = true) Long resolutionElapsedMs
) {
}
