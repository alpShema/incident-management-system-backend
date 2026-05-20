package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for creating or updating a department")
public record AgentGroupRequest(
        @Schema(description = "Department name", example = "Facilities")
        @NotBlank String name,

        @Schema(description = "Optional department description", nullable = true)
        String description
) {}
