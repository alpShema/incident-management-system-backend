package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for creating or updating an agent group. `departmentId` is required when creating a group and optional when patching.")
public record AgentGroupRequest(
        @Schema(description = "Agent group name", example = "Facilities Support")
        @Size(max = 100, message = "Agent group name must not exceed 100 characters.")
        String name,

        @Schema(description = "Optional agent group description", nullable = true)
        @Size(max = 1000, message = "Agent group description must not exceed 1000 characters.")
        String description,

        @Schema(description = "Internal department ID this agent group belongs to. Required on create.", example = "dept-facilities")
        String departmentId,

        @Schema(description = "List of agent IDs to add as members. Required on create — at least one active agent must be provided. Optional on update: null leaves membership unchanged, empty list removes all members.", nullable = true)
        List<String> agentIds,

        @Schema(description = "Optional list of topic IDs to assign to this group. On update, this replaces the full set of topics linked to the group: any topic currently linked but not included here is unassigned. Null leaves topic assignments unchanged; pass an empty list to unassign all topics.", nullable = true)
        List<String> topicIds
) {}
