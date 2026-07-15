package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update a user's account status")
public record UpdateUserStatusRequest(
        @Schema(description = "Whether the user account is active", example = "true")
        @NotNull(message = "Please specify whether the user account should be active or inactive.")
        Boolean status
) {}
