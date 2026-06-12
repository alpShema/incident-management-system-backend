package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for activating or deactivating an incident topic")
public record UpdateIncidentTypeStatusRequest(
        @Schema(description = "Target topic status. true = active, false = inactive", example = "false")
        @NotNull(message = "status is required")
        Boolean status
) {}
