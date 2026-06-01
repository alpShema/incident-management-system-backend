package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Admin;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin details with linked user information")
public record AdminResponse(
        @Schema(description = "Admin record ID") String adminId,
        @Schema(description = "ARMS user ID linked to the admin") String userId,
        @Schema(description = "Admin's full name", nullable = true) String fullName,
        @Schema(description = "Admin's email address", nullable = true) String email,
        @Schema(description = "URL to admin's profile image", nullable = true) String profileImg,
        @Schema(description = "Whether the admin is available") boolean status
) {
    public static AdminResponse from(Admin admin) {
        return new AdminResponse(
                admin.getId(),
                admin.getUserId(),
                admin.getUser() != null ? admin.getUser().getFullName() : null,
                admin.getUser() != null ? admin.getUser().getEmail() : null,
                admin.getUser() != null ? admin.getUser().getProfileImg() : null,
                admin.isStatus()
        );
    }
}
