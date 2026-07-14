package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response from the FAQ chatbot")
public record ChatbotQueryResponse(
        @Schema(description = "The answer composed for the user, or the escalation message if no match was found")
        String answer,

        @Schema(description = "Confidence score of the matched FAQ (0.0 – 1.0), rounded to 2 decimal places")
        double confidence,

        @Schema(description = "Outcome of the query: ANSWERED, ESCALATED, or ERROR")
        String outcome
) {}
