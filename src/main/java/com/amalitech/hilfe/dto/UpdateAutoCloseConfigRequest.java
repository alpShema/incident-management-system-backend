package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body to update the global auto-close duration")
public record UpdateAutoCloseConfigRequest(
        @Schema(description = "Number of hours after resolution before auto-close fires. Minimum 1.", example = "72")
        @NotNull(message = "durationHours is required")
        @Min(value = 1, message = "durationHours must be at least 1")
        Integer durationHours
) {
}
