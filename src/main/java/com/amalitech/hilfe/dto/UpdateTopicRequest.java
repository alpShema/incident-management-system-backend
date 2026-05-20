package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for updating an incident topic")
public record UpdateTopicRequest(
        @Schema(description = "New topic display name", nullable = true)
        String name,

        @Schema(description = "New topic description", nullable = true)
        String description,

        @Schema(description = "Responsible agent ID", nullable = true)
        String agentId,

        @Schema(description = "Whether this topic is visible to group members", nullable = true)
        Boolean visibleToGroup
) {}
