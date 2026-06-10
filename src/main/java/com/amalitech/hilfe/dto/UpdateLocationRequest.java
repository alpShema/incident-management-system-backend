package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for partially updating an existing location. Only provided fields are updated.")
public record UpdateLocationRequest(
        @Schema(description = "Updated location name", example = "Kumasi Central")
        @Size(max = 255, message = "Name must not exceed 255 characters")
        String name,

        @Schema(description = "Updated description", example = "Ashanti regional office — central branch")
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        String description
) {}
