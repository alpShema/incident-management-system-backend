package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "ARMS SSO login request")
public record LoginRequest(
        @NotBlank(message = "Please provide a valid login token.")
        @Schema(description = "Short-lived ARMS SSO token obtained from the ARMS login flow", example = "eyJhbGci...") String armsToken
) {
}
