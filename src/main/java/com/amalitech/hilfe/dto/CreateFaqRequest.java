package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for creating a new FAQ entry")
public record CreateFaqRequest(
        @Schema(description = "The question as it would be phrased by a user", example = "How do I reset my password?")
        @NotBlank
        @Size(max = 500, message = "question must not exceed 500 characters")
        String question,

        @Schema(description = "The answer to display to the user")
        @NotBlank
        String answer
) {}
