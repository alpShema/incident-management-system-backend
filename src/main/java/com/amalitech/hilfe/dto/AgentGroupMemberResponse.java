package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Agent;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent group member details")
public record AgentGroupMemberResponse(
        @Schema(description = "Agent record ID") String agentId,
        @Schema(description = "User ID linked to the agent") String userId,
        @Schema(description = "User full name", nullable = true) String fullName,
        @Schema(description = "User profile image URL", nullable = true) String profileImage,
        @Schema(description = "Whether the agent is active") Boolean status,
        @Schema(description = "Location ID of the agent", nullable = true) String locationId,
        @Schema(description = "Location name of the agent", nullable = true) String locationName
) {
    public static AgentGroupMemberResponse from(Agent agent) {
        String locationId = null;
        String locationName = null;
        if (agent.getUser() != null) {
            locationId = agent.getUser().getLocationId();
            if (agent.getUser().getLocation() != null) {
                locationName = agent.getUser().getLocation().getName();
            }
        }
        return new AgentGroupMemberResponse(
                agent.getId(),
                agent.getUserId(),
                agent.getUser() != null ? agent.getUser().getFullName() : null,
                agent.getUser() != null ? agent.getUser().getProfileImg() : null,
                agent.getStatus(),
                locationId,
                locationName
        );
    }
}
