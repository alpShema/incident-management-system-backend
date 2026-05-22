package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating or updating an agent group. `departmentId` is required when creating a group and optional when patching.")
public record AgentGroupRequest(
        @Schema(description = "Agent group name", example = "Facilities Support")
        @Size(max = 100, message = "must not exceed 100 characters")
        String name,

        @Schema(description = "Optional agent group description", nullable = true)
        @Size(max = 1000, message = "must not exceed 1000 characters")
        String description,

        @Schema(description = "Internal department ID this agent group belongs to. Required on create.", example = "dept-facilities")
        String departmentId,

        @Schema(description = "Primary agent ID for incident auto-assignment. Required on create. On update, the agent must already be a member of this group.", example = "agent-seed-001", nullable = true)
        String primaryAgentId
) {}
