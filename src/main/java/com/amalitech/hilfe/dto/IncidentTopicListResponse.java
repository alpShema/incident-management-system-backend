package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Global incident topic listing row with category and agent group details")
public record IncidentTopicListResponse(
        @Schema(description = "Stable topic ID", example = "type-account-issues") String id,
        @Schema(description = "Topic display name", example = "Account Issues") String name,
        @Schema(description = "Topic description", example = "Login problems, password resets, account access") String description,
        @Schema(description = "Whether incidents under this topic are visible to the assigned agent group", example = "true") boolean visibleToGroup,
        @Schema(description = "Whether incidents under this topic are confidential", example = "false") boolean confidential,
        @Schema(description = "Whether the topic is active") Boolean status,
        @Schema(description = "Parent category (id + name)", nullable = true) LookupResponse category,
        @Schema(description = "Assigned agent group summary", nullable = true) AgentGroupSummary agentGroup
) {
    public static IncidentTopicListResponse from(IncidentType type) {
        LookupResponse category = type.getCategory() != null
                ? LookupResponse.from(type.getCategory().getId(), type.getCategory().getName())
                : null;

        AgentGroupSummary group = null;
        if (type.getAgentGroup() != null) {
            group = new AgentGroupSummary(type.getAgentGroup().getId(), type.getAgentGroup().getName());
        }

        return new IncidentTopicListResponse(
                type.getId(),
                type.getName(),
                type.getDescription(),
                type.isVisibleToGroup(),
                type.isConfidential(),
                type.getStatus(),
                category,
                group
        );
    }

    @Schema(description = "Assigned agent group details")
    public record AgentGroupSummary(
            @Schema(description = "Agent group ID", example = "agent-group-it-support") String id,
            @Schema(description = "Agent group name", example = "IT Support") String name
    ) {}
}
