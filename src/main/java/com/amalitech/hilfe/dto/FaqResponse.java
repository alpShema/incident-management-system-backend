package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Faq;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "FAQ entry details")
public record FaqResponse(
        String id,
        String question,
        String answer,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static FaqResponse from(Faq faq) {
        return new FaqResponse(
                faq.getId(),
                faq.getQuestion(),
                faq.getAnswer(),
                faq.isActive(),
                faq.getCreatedAt(),
                faq.getUpdatedAt()
        );
    }
}
