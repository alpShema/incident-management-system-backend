package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Agent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Agent details with linked user information")
public record AgentResponse(
        @Schema(description = "Agent record ID") String agentId,
        @Schema(description = "ARMS user ID linked to the agent") String userId,
        @Schema(description = "Agent's full name", nullable = true) String fullName,
        @Schema(description = "Agent's email address", nullable = true) String email,
        @Schema(description = "URL to agent's profile image", nullable = true) String profileImg,
        @Schema(description = "Agent's location ID", nullable = true) String locationId,
        @Schema(description = "Agent's location name", nullable = true) String locationName,
        @Schema(description = "Whether the agent is active") Boolean status,
        @Schema(description = "Timestamp when the agent record was last updated (UTC)") Instant updatedAt
) {
    public static AgentResponse from(Agent agent) {
        String locationId = null;
        String locationName = null;
        if (agent.getUser() != null) {
            locationId = agent.getUser().getLocationId();
            if (agent.getUser().getLocation() != null) {
                locationName = agent.getUser().getLocation().getName();
            }
        }
        return new AgentResponse(
                agent.getId(),
                agent.getUserId(),
                agent.getUser() != null ? agent.getUser().getFullName() : null,
                agent.getUser() != null ? agent.getUser().getEmail() : null,
                agent.getUser() != null ? agent.getUser().getProfileImg() : null,
                locationId,
                locationName,
                agent.getStatus(),
                agent.getUpdatedAt()
        );
    }
}
