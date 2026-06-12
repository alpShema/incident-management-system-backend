package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a new location")
public record CreateLocationRequest(
        @Schema(description = "Location name", example = "Kumasi")
        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Schema(description = "Optional description", example = "Ashanti regional office")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {}
