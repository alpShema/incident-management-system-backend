package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.time.ZoneId;

@Schema(description = "Payload for creating a new location")
public record CreateLocationRequest(
        @Schema(description = "Location name", example = "Kumasi")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Schema(description = "Optional description", example = "Ashanti regional office")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description,

        @Schema(description = "IANA timezone id. Defaults to UTC when omitted.", example = "Africa/Kigali")
        String timezone,

        @Schema(description = "Business hours start. Defaults to 08:00 when omitted.", example = "08:00:00")
        LocalTime businessHoursStart,

        @Schema(description = "Business hours end. Defaults to 17:30 when omitted.", example = "17:30:00")
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
