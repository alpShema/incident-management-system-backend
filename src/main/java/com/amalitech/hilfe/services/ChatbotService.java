package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import com.amalitech.hilfe.dto.ChatbotInteractionResponse;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.ChatbotInteraction;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.ChatbotInteractionRepository;
import com.amalitech.hilfe.repositories.FaqRepository;
import com.amalitech.hilfe.services.ConversationContextService.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotService {

    private static final String OUTCOME_ANSWERED  = "ANSWERED";
    private static final String OUTCOME_ESCALATED = "ESCALATED";
    private static final String OUTCOME_ERROR     = "ERROR";

    private static final String ESCALATION_HINT =
            "I couldn't find a clear answer to your question. "
            + "Please raise a ticket and our team will assist you directly.";

    private final FaqRepository faqRepository;
    private final ChatbotInteractionRepository interactionRepository;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final ConversationContextService contextService;
    private final ChatbotProperties props;

    public ChatbotQueryResponse query(String userId, String rawQuery) {
        ConversationContext ctx = contextService.getContext(userId);

        try {
            // 1. Rewrite the query using conversation context
            String rewrittenQuery = llmService.rewriteQuery(rawQuery, ctx.recentTurns());

            // 2. Embed the rewritten query
            float[] vector = embeddingService.embed(rewrittenQuery);

            // 3. Retrieve nearest FAQ (requires non-null embedding)
            Optional<Faq> matchOpt = Optional.empty();
            double similarity = 0.0;

            if (vector != null) {
                String literal = EmbeddingService.toVectorLiteral(vector);
                matchOpt = faqRepository.findClosestActive(literal);
                if (matchOpt.isPresent()) {
                    similarity = faqRepository.findClosestActiveDistance(literal)
                            .map(distance -> 1.0 - distance)
                            .orElse(0.0);
                }
            }

            double roundedSimilarity = Math.round(similarity * 100.0) / 100.0;

            // 4. Threshold check
            if (matchOpt.isEmpty() || similarity < props.confidenceThreshold()) {
                logInteraction(userId, rawQuery, null, similarity, OUTCOME_ESCALATED);
                return new ChatbotQueryResponse(ESCALATION_HINT, roundedSimilarity, OUTCOME_ESCALATED);
            }

            Faq faq = matchOpt.get();

            // 5. Generate grounded answer
            String answer = llmService.generateAnswer(
                    rewrittenQuery,
                    faq.getQuestion(),
                    faq.getAnswer(),
                    ctx.rollingSummary()
            );

            // 6. Update context
            contextService.addTurn(userId, rawQuery, answer);

            // 7. Log interaction async
            logInteraction(userId, rawQuery, faq.getId(), similarity, OUTCOME_ANSWERED);

            return new ChatbotQueryResponse(answer, roundedSimilarity, OUTCOME_ANSWERED);

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chatbot query failed for user {}: {}", userId, e.getMessage(), e);
            logInteraction(userId, rawQuery, null, 0.0, OUTCOME_ERROR);
            throw new ServiceUnavailableException("Chatbot service is temporarily unavailable. Please try again shortly.", e);
        }
    }

    public PageResponse<ChatbotInteractionResponse> listInteractions(
            String userId, String outcome, Instant from, Instant to, Pageable pageable) {
        return PageResponse.from(
                interactionRepository.findAllFiltered(userId, outcome, from, to, pageable)
                        .map(ChatbotInteractionResponse::from)
        );
    }

    @Async
    protected void logInteraction(String userId, String query, String faqId, double confidence, String outcome) {
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
