package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for assigning an incident to an agent")
public record AssignIncidentRequest(
        @Schema(description = "Agent record ID (not user ID) of the agent to assign the incident to", example = "agent-seed")
        @NotBlank String agentId
) {}
