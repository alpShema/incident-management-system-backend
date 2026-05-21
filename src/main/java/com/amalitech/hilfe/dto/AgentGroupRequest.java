package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating or updating an agent group")
public record AgentGroupRequest(
        @Schema(description = "Agent group name", example = "Facilities Support")
        @NotBlank
        @Size(max = 100, message = "must not exceed 100 characters")
        String name,

        @Schema(description = "Optional agent group description", nullable = true)
        @Size(max = 1000, message = "must not exceed 1000 characters")
        String description,

        @Schema(description = "Optional primary agent ID for incident auto-assignment", example = "agent-seed-001", nullable = true)
        String primaryAgentId
) {}
