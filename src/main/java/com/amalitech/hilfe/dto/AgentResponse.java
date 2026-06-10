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
        @Schema(description = "Agent's office location name", nullable = true) String officeLocation,
        @Schema(description = "Whether the agent is active") Boolean status,
        @Schema(description = "Timestamp when the agent record was last updated (UTC)") Instant updatedAt
) {
    public static AgentResponse from(Agent agent) {
        var user = agent.getUser();
        String officeLocation = null;
        if (user != null && user.getLocation() != null) {
            officeLocation = user.getLocation().getName();
        }
        return new AgentResponse(
                agent.getId(),
                agent.getUserId(),
                user != null ? user.getFullName() : null,
                user != null ? user.getEmail() : null,
                user != null ? user.getProfileImg() : null,
                officeLocation,
                agent.getStatus(),
                agent.getUpdatedAt()
        );
    }
}
