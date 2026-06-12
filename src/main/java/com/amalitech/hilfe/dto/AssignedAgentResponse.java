package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Agent;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Assigned agent details")
public record AssignedAgentResponse(
        @Schema(description = "Agent record ID", example = "agent-seed-001") String agentId,
        @Schema(description = "Agent's full name", example = "John Doe") String fullName,
        @Schema(description = "Agent's email address", example = "john.doe@example.com") String email,
        @Schema(description = "URL to agent's profile image", nullable = true) String profileImg,
        @Schema(description = "Agent's location ID", nullable = true) String locationId,
        @Schema(description = "Agent's location name", nullable = true) String locationName
) {
    public static AssignedAgentResponse from(Agent agent) {
        if (agent == null) {
            return null;
        }
        String locationId = null;
        String locationName = null;
        if (agent.getUser() != null) {
            locationId = agent.getUser().getLocationId();
            if (agent.getUser().getLocation() != null) {
                locationName = agent.getUser().getLocation().getName();
            }
        }
        return new AssignedAgentResponse(
                agent.getId(),
                agent.getUser() != null ? agent.getUser().getFullName() : null,
                agent.getUser() != null ? agent.getUser().getEmail() : null,
                agent.getUser() != null ? agent.getUser().getProfileImg() : null,
                locationId,
                locationName
        );
    }
}
