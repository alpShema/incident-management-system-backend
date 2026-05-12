package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating a new incident topic (type) under a category")
public record CreateTopicRequest(
        @Schema(description = "Display name of the topic", example = "Projector")
        @NotBlank String name,

        @Schema(description = "Description of what incidents belong under this topic", example = "Issues with projection equipment in meeting rooms")
        @NotBlank String description,

        @Schema(description = "Agent ID of the default agent responsible for this topic type", example = "agent-uuid")
        @NotBlank String agentId,

        @Schema(description = "Whether this topic is visible to group members (non-admin reporters)", example = "true")
        boolean visibleToGroup
) {}
