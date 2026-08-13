package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "An incident topic (type) with its parent category")
public record IncidentTopicResponse(
        @Schema(description = "Stable topic ID", example = "type-account-issues") String id,
        @Schema(description = "Topic display name", example = "Account Issues") String name,
        @Schema(description = "Topic description", example = "Login problems, password resets, account access") String description,
        @Schema(description = "Whether incidents under this topic are visible to the assigned agent group", example = "true") boolean visibleToGroup,
        @Schema(description = "Whether incidents under this topic are confidential", example = "false") boolean confidential,
        @Schema(description = "Whether the topic is active") Boolean status,
        @Schema(description = "Parent category (id + name)", nullable = true) LookupResponse category,
        @Schema(description = "Timestamp when the topic was last updated (UTC)") Instant updatedAt
) {
    public static IncidentTopicResponse from(IncidentType type) {
        LookupResponse category = type.getCategory() != null
                ? LookupResponse.from(type.getCategory().getId(), type.getCategory().getName())
                : null;
        return new IncidentTopicResponse(
                type.getId(),
                type.getName(),
                type.getDescription(),
                type.isVisibleToGroup(),
                type.isConfidential(),
                type.getStatus(),
                category,
                type.getUpdatedAt());
    }
}
