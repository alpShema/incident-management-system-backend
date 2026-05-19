package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary of the user who created the incident")
public record CreatorResponse(
        @Schema(description = "User ID of the creator", example = "1285") String id,
        @Schema(description = "Full name of the creator", example = "John Doe") String fullName,
        @Schema(description = "Profile image URL", nullable = true) String profileImage
) {
    public static CreatorResponse from(User user) {
        if (user == null) return null;
        return new CreatorResponse(user.getId(), user.getFullName(), user.getProfileImg());
    }
}
