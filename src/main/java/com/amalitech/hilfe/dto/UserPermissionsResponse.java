package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "The authenticated user's ID and their full list of granted permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermissionsResponse {
    @Schema(description = "ARMS user ID of the authenticated user")
    private String userId;

    @Schema(description = "List of permission strings granted via the user's role (e.g. incident.create, dashboard.agent)")
    private List<String> permissions;
}
