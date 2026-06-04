package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for activating or deactivating an agent group")
public record UpdateAgentGroupStatusRequest(
        @Schema(description = "Target agent group status. true = active, false = inactive", example = "true")
        @NotNull(message = "status is required")
        Boolean status
) {}
