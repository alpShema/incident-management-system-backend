package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "A question submitted by the user to the FAQ chatbot")
public record ChatbotQueryRequest(
        @Schema(description = "The user's question or message", example = "How do I reset my password?")
        @NotBlank
        @Size(max = 1000, message = "query must not exceed 1000 characters")
        String query
) {}
