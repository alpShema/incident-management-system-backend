package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.AgentGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent group details")
public record AgentGroupResponse(
        @Schema(description = "Agent group ID") String id,
        @Schema(description = "Agent group name") String name,
        @Schema(description = "Agent group description", nullable = true) String description,
        @Schema(description = "Internal department this agent group belongs to", nullable = true) LookupResponse department,
        @Schema(description = "Whether the agent group is active") Boolean status,
        @Schema(description = "Number of agents in this agent group") long memberCount
) {
    public static AgentGroupResponse from(AgentGroup group, LookupResponse department, long memberCount) {
        return new AgentGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                department,
                group.getStatus(),
                memberCount
        );
    }
}
