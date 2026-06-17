package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Severity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Severity / priority level")
public record SeverityResponse(
        @Schema(description = "Severity ID") String id,
        @Schema(description = "Severity name") String name,
        @Schema(description = "Severity description") String description,
        @Schema(description = "Whether this severity is active") Boolean status,
        @Schema(description = "Configured response SLA threshold in minutes", nullable = true) Integer responseTimeMinutes,
        @Schema(description = "Configured resolution SLA threshold in minutes", nullable = true) Integer resolutionTimeMinutes,
        @Schema(description = "Configured response SLA threshold in seconds", nullable = true) Long responseTimeSeconds,
        @Schema(description = "Configured resolution SLA threshold in seconds", nullable = true) Long resolutionTimeSeconds,
        @Schema(description = "Created timestamp") Instant createdAt,
        @Schema(description = "Updated timestamp") Instant updatedAt
) {
    public static SeverityResponse from(Severity s) {
        return new SeverityResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getStatus(),
                s.getResponseTimeMinutes(),
                s.getResolutionTimeMinutes(),
                toSeconds(s.getResponseTimeMinutes()),
                toSeconds(s.getResolutionTimeMinutes()),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }

    private static Long toSeconds(Integer minutes) {
        return minutes != null ? minutes * 60L : null;
    }
}
