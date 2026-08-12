package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for updating an incident topic")
public record UpdateTopicRequest(
        @Schema(description = "New topic display name", nullable = true)
        String name,

        @Schema(description = "New topic description", nullable = true)
        String description,

        @Schema(description = "New category ID to reassign the topic to. When provided alongside agentGroupId, the agent group is validated against the new category's department.", nullable = true)
        String categoryId,

        @Schema(description = "Responsible agent group ID. The group must have a primary agent and belong to the same department as the category. Ignored when removeAgentGroup is true.", nullable = true)
        String agentGroupId,

        @Schema(description = "When true, unassigns this topic from its current agent group. Cannot be combined with a non-blank agentGroupId in the same request.", nullable = true)
        Boolean removeAgentGroup,

        @Schema(description = "Whether this topic is visible to group members", nullable = true)
        Boolean visibleToGroup,

        @Schema(description = "Whether incidents under this topic are confidential. Requires the topic to have a "
                + "responsible agent group (or legacy single agent) — cannot be enabled together with removeAgentGroup.",
                nullable = true)
        Boolean confidential
) {}
