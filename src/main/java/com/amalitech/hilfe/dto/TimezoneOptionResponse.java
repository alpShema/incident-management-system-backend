package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A selectable IANA timezone option")
public record TimezoneOptionResponse(
        @Schema(description = "IANA timezone id", example = "Africa/Kigali") String id,
        @Schema(description = "Human-friendly display label", example = "Kigali") String label,
        @Schema(description = "Current UTC offset for this zone", example = "+02:00") String offsetNow
) {
}
