package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.AgentGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent group details")
public record AgentGroupResponse(
        @Schema(description = "Agent group ID") String id,
        @Schema(description = "Agent group name") String name,
        @Schema(description = "Agent group description", nullable = true) String description,
        @Schema(description = "Whether the agent group is active") Boolean status,
        @Schema(description = "Primary agent for incident auto-assignment", nullable = true) LookupResponse primaryAgent,
        @Schema(description = "Number of agents in this agent group") long memberCount
) {
    public static AgentGroupResponse from(AgentGroup group, long memberCount) {
        LookupResponse primaryAgent = group.getPrimaryAgent() != null
                ? LookupResponse.from(
                group.getPrimaryAgent().getId(),
                group.getPrimaryAgent().getUser() != null
                        ? group.getPrimaryAgent().getUser().getFullName()
                        : group.getPrimaryAgent().getUserId())
                : null;
        return from(group, primaryAgent, memberCount);
    }

    public static AgentGroupResponse from(AgentGroup group, LookupResponse primaryAgent, long memberCount) {
        return new AgentGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getStatus(),
                primaryAgent,
                memberCount
        );
    }
}
