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
        @Schema(description = "Created timestamp") Instant createdAt,
        @Schema(description = "Updated timestamp") Instant updatedAt
) {
    public static SeverityResponse from(Severity s) {
        return new SeverityResponse(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
