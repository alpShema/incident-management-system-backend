package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Location;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A location that can be associated with an incident")
public record LocationResponse(
        @Schema(description = "Stable location ID") String id,
        @Schema(description = "Location display name", example = "Accra Office - Floor 2") String name
) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName());
    }
}
