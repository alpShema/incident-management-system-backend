package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalTime;

@Schema(description = "A location that can be associated with an incident")
public record LocationResponse(
        @Schema(description = "Stable location ID", example = "loc-accra") String id,
        @Schema(description = "Location display name", example = "Accra") String name,
        @Schema(description = "Optional description") String description,
        @Schema(description = "Whether the location is active") Boolean status,
        @Schema(description = "IANA timezone id", example = "Africa/Accra") String timezone,
        @Schema(description = "Business hours start") LocalTime businessHoursStart,
        @Schema(description = "Business hours end") LocalTime businessHoursEnd,
        @Schema(description = "Timestamp when the location was last updated (UTC)") Instant updatedAt
) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getDescription(),
                location.getStatus(),
                location.getTimezone(),
                location.getBusinessHoursStart(),
                location.getBusinessHoursEnd(),
                location.getUpdatedAt()
        );
    }
}
