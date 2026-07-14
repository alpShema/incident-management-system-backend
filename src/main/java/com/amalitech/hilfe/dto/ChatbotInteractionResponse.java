package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.ChatbotInteraction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "A recorded chatbot interaction")
public record ChatbotInteractionResponse(
        String id,
        String userId,
        String query,
        String faqId,
        Double confidence,
        String outcome,
        Instant createdAt
) {
    public static ChatbotInteractionResponse from(ChatbotInteraction interaction) {
        return new ChatbotInteractionResponse(
                interaction.getId(),
                interaction.getUserId(),
                interaction.getQuery(),
                interaction.getFaqId(),
                interaction.getConfidence(),
                interaction.getOutcome(),
                interaction.getCreatedAt()
        );
    }
}
