package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Global auto-close configuration")
public record AutoCloseConfigResponse(
        @Schema(description = "Duration in seconds after resolution before the system auto-closes the incident", example = "259200")
        int durationSeconds
) {
}
