package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update agent availability status")
public record UpdateAvailabilityRequest(
        @Schema(description = "Whether the agent is available", example = "true")
        @NotNull(message = "Please specify your availability status.")
        Boolean available
) {}
