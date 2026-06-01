package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User summary with their current role assignment")
public record UserRoleSummaryResponse(
        @Schema(description = "ARMS user ID") String userId,
        @Schema(description = "User's email address") String email,
        @Schema(description = "User's full name") String fullName,
        @Schema(description = "URL to the user's profile image", nullable = true) String profileImg,
        @Schema(description = "User's current role code") String roleCode,
        @Schema(description = "User's active/inactive status", nullable = true) Boolean status,
        @Schema(description = "User's office location name", nullable = true) String officeLocation,
        @Schema(description = "Total incidents submitted by this user (lifetime)") Long submittedIncidentsCount,
        @Schema(description = "Total incidents assigned to this user as an agent (0 for non-agents, lifetime)") Long assignedIncidentsCount
) {
    public UserRoleSummaryResponse(
            String userId,
            String email,
            String fullName,
            String profileImg,
            RoleCode roleCode,
            Boolean status,
            String officeLocation,
            Long submittedIncidentsCount,
            Long assignedIncidentsCount
    ) {
        this(userId, email, fullName, profileImg, roleCode == null ? null : roleCode.name(),
                status, officeLocation, submittedIncidentsCount, assignedIncidentsCount);
    }
}
