package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "Session info returned after successful login or token refresh")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionResponse {
    @Schema(description = "The authenticated user's ID from ARMS")
    private String userId;

    @Schema(description = "User's email address")
    private String email;

    @Schema(description = "User's full name")
    private String fullName;

    @Schema(description = "URL to the user's profile image", nullable = true)
    private String profileImg;

    @Schema(description = "User's role code (e.g. ADMIN, AGENT, CLIENT)")
    private String role;

    @Schema(description = "List of permission strings granted to this user based on their role")
    private List<String> permissions;
}
