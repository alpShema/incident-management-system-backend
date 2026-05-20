package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.AgentGroup;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Department details")
public record AgentGroupResponse(
        @Schema(description = "Department ID") String id,
        @Schema(description = "Department name") String name,
        @Schema(description = "Department description", nullable = true) String description,
        @Schema(description = "Whether the department is active") Boolean status,
        @Schema(description = "Number of agents in this department") long memberCount
) {
    public static AgentGroupResponse from(AgentGroup group, long memberCount) {
        return new AgentGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getStatus(),
                memberCount
        );
    }
}
