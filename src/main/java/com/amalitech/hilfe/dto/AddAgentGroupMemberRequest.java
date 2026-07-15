package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for adding an agent to an agent group")
public record AddAgentGroupMemberRequest(
        @Schema(description = "Agent record ID", example = "agent-seed-001")
        @NotBlank(message = "Please select an agent to add to this group.")
        String agentId
) {}
