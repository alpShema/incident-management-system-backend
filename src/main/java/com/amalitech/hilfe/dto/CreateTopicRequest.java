package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating a new incident topic (type) under a category")
public record CreateTopicRequest(
        @Schema(description = "Display name of the topic", example = "Projector")
        @NotBlank(message = "Topic name is required and cannot be blank.")
        String name,

        @Schema(description = "Description of what incidents belong under this topic", example = "Issues with projection equipment in meeting rooms")
        @NotBlank(message = "Topic description is required and cannot be blank.")
        String description,

        @Schema(description = "Responsible agent group ID. Required. The group must have a primary agent and belong to the same department as the category.", example = "agent-group-facilities")
        @NotBlank(message = "Please select a responsible agent group for this topic.")
        String agentGroupId,

        @Schema(description = "Whether this topic is visible to group members (non-admin reporters)", example = "true")
        boolean visibleToGroup
) {}
