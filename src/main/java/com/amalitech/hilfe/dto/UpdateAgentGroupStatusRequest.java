package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request body for activating or deactivating an agent group")
public record UpdateAgentGroupStatusRequest(
        @Schema(description = "Target agent group status. true = active, false = inactive", example = "true")
        @NotNull(message = "Please specify whether the agent group should be active or inactive.")
        Boolean status
) {}
