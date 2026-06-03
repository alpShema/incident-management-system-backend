package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A location that can be associated with an incident")
public record LocationResponse(
        @Schema(description = "Stable location ID", example = "loc-accra") String id,
        @Schema(description = "Location display name", example = "Accra") String name,
        @Schema(description = "Timestamp when the location was last updated (UTC)") Instant updatedAt
) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName(), location.getUpdatedAt());
    }
}
