package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User summary with their current role assignment")
public record UserRoleSummaryResponse(
        @Schema(description = "ARMS user ID") String userId,
        @Schema(description = "User's email address") String email,
        @Schema(description = "User's full name") String fullName,
        @Schema(description = "URL to the user's profile image", nullable = true) String profileImg,
        @Schema(description = "User's current role. One of: CLIENT, AGENT, ADMIN, SUPER_ADMIN") RoleCode roleCode
) {
}
