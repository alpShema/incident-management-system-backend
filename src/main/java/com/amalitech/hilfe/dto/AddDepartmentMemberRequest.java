package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for adding an agent to a department")
public record AddDepartmentMemberRequest(
        @Schema(description = "Agent record ID", example = "agent-seed-001")
        @NotBlank String agentId
) {}
