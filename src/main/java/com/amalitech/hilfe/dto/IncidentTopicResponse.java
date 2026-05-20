package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.IncidentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An incident topic (type) with its parent category")
public record IncidentTopicResponse(
        @Schema(description = "Stable topic ID", example = "type-account-issues") String id,
        @Schema(description = "Topic display name", example = "Account Issues") String name,
        @Schema(description = "Topic description", example = "Login problems, password resets, account access") String description,
        @Schema(description = "Whether incidents under this topic are visible to the assigned agent group", example = "true") boolean visibleToGroup,
        @Schema(description = "Parent category (id + name)", nullable = true) LookupResponse category,
        @Schema(description = "Responsible agent group and primary agent routing details", nullable = true) AssignedAgentGroupResponse assignedAgentGroup
) {
    public static IncidentTopicResponse from(IncidentType type) {
        LookupResponse category = type.getCategory() != null
                ? LookupResponse.from(type.getCategory().getId(), type.getCategory().getName())
                : null;
        AssignedAgentGroupResponse assignedAgentGroup = type.getAgentGroup() != null
                ? AssignedAgentGroupResponse.from(type.getAgentGroup())
                : null;
        return new IncidentTopicResponse(
                type.getId(),
                type.getName(),
                type.getDescription(),
                type.isVisibleToGroup(),
                category,
                assignedAgentGroup);
    }

    @Schema(description = "Responsible agent group assigned to a topic")
    public record AssignedAgentGroupResponse(
            @Schema(description = "Agent group ID", example = "agent-group-facilities") String id,
            @Schema(description = "Agent group name", example = "Facilities Support") String name,
            @Schema(description = "Primary agent for incident auto-assignment", nullable = true) AssignedAgentResponse primaryAgent
    ) {
        static AssignedAgentGroupResponse from(com.amalitech.hilfe.models.AgentGroup agentGroup) {
            AssignedAgentResponse primaryAgent = agentGroup.getPrimaryAgent() != null
                    ? AssignedAgentResponse.from(agentGroup.getPrimaryAgent())
                    : null;
            return new AssignedAgentGroupResponse(agentGroup.getId(), agentGroup.getName(), primaryAgent);
        }
    }

    @Schema(description = "Primary agent assigned through an agent group")
    public record AssignedAgentResponse(
            @Schema(description = "Stable agent ID", example = "agent-seed-001") String id,
            @Schema(description = "User ID linked to the agent", example = "1208") String userId,
            @Schema(description = "Agent display name", example = "Ethan Sabith Williams") String name,
            @Schema(description = "Agent email address", example = "ethan.williams@amalitech.com") String email
    ) {
        static AssignedAgentResponse from(com.amalitech.hilfe.models.Agent agent) {
            String name = agent.getUser() != null ? agent.getUser().getFullName() : agent.getUserId();
            String email = agent.getUser() != null ? agent.getUser().getEmail() : null;
            return new AssignedAgentResponse(agent.getId(), agent.getUserId(), name, email);
        }
    }
}
