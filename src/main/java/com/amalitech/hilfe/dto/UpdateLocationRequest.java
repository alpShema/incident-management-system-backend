package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.ZoneId;

@Schema(description = "Payload for partially updating an existing location. Only provided fields are updated.")
public record UpdateLocationRequest(
        @Schema(description = "Updated location name", example = "Kumasi Central")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Schema(description = "Updated description", example = "Ashanti regional office — central branch")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Schema(description = "IANA timezone id", example = "Africa/Kigali")
        String timezone,

        @Schema(description = "Business hours start", example = "08:00:00")
        LocalTime businessHoursStart,

        @Schema(description = "Business hours end", example = "17:30:00")
        LocalTime businessHoursEnd
) {
        @AssertTrue(message = "timezone must be a valid IANA zone id")
        @Schema(hidden = true)
        public boolean isTimezoneValid() {
                if (timezone == null) {
                        return true;
                }
                try {
                        ZoneId.of(timezone);
                        return true;
                } catch (Exception e) {
                        return false;
                }
        }
}
