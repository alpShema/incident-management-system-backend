package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload for updating a location's active status")
public record UpdateLocationStatusRequest(
        @Schema(description = "true to activate, false to deactivate", example = "false")
        @NotNull(message = "Status is required")
        Boolean status
) {}
