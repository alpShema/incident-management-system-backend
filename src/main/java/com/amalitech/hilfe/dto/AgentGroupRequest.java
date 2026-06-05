package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

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

        @Schema(description = "Optional list of agent IDs to add as members immediately on creation.", nullable = true)
        List<String> agentIds,

        @Schema(description = "Optional list of topic IDs to assign to this group. Replaces existing assignments when present. Pass an empty list to remove all topics.", nullable = true)
        List<String> topicIds
) {}
