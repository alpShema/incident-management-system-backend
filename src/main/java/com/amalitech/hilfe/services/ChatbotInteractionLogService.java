package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.ChatbotInteraction;
import com.amalitech.hilfe.repositories.ChatbotInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotInteractionLogService {

    private final ChatbotInteractionRepository interactionRepository;

    @Async
    public void logInteraction(String userId, String query, String faqId, double confidence, String outcome) {
        try {
            ChatbotInteraction interaction = ChatbotInteraction.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .query(query)
                    .faqId(faqId)
                    .confidence(confidence)
                    .outcome(outcome)
                    .build();
            interactionRepository.save(interaction);
        } catch (Exception e) {
            log.error("Failed to log chatbot interaction for user {}: {}", userId, e.getMessage());
        }
    }

}
