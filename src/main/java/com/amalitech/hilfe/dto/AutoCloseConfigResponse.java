package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Global auto-close configuration")
public record AutoCloseConfigResponse(
        @Schema(description = "Number of hours after resolution before the system auto-closes the incident", example = "72")
        int durationHours
) {
}
