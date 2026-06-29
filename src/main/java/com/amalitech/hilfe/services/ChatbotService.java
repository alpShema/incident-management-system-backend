package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import com.amalitech.hilfe.dto.ChatbotInteractionResponse;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.ChatbotInteractionRepository;
import com.amalitech.hilfe.repositories.FaqRepository;
import com.amalitech.hilfe.services.ConversationContextService.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    // Uses CAST(? AS vector) with Types.OTHER binding so PostgreSQL receives the
    // parameter as untyped and resolves the <=> operator dispatch correctly.
    // Hibernate's setString() sends VARCHAR, which fails the operator lookup.
    private static final String VECTOR_SEARCH_SQL = """
            SELECT id, (embedding <=> CAST(? AS vector)) AS dist
            FROM "Faq"
            WHERE active = TRUE AND embedding IS NOT NULL
            ORDER BY dist
            LIMIT 1
            """;

    private final FaqRepository faqRepository;
    private final ChatbotInteractionRepository interactionRepository;
    private final EmbeddingService embeddingService;
    private final LlmService llmService;
    private final ConversationContextService contextService;
    private final ChatbotProperties props;
    private final ChatbotInteractionLogService interactionLogService;
    private final JdbcTemplate jdbcTemplate;

    private record SearchResult(String faqId, double distance) {}

    private record FaqMatch(Optional<Faq> faq, double similarity) {}

    public ChatbotQueryResponse query(String userId, String rawQuery) {
        log.debug("Chatbot query start | userId={} | rawQuery=\"{}\"", userId, rawQuery);
        ConversationContext ctx = contextService.getContext(userId);

        try {
            String rewrittenQuery = llmService.rewriteQuery(rawQuery, ctx.recentTurns());
            log.debug("Step 1 rewrite | rewrittenQuery=\"{}\"", rewrittenQuery);

            float[] vector = embeddingService.embed(rewrittenQuery);
            log.debug("Step 2 embed | dims={}", vector != null ? vector.length : 0);

            FaqMatch match = findNearestFaq(vector);
            double roundedSimilarity = Math.round(match.similarity() * 100.0) / 100.0;

            if (match.faq().isEmpty() || match.similarity() < props.confidenceThreshold()) {
                return escalate(userId, rawQuery, match, roundedSimilarity);
            }

            return respondWithAnswer(userId, rawQuery, rewrittenQuery, ctx, match, roundedSimilarity);

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chatbot query failed for user {}: {}", userId, e.getMessage(), e);
            interactionLogService.logInteraction(userId, rawQuery, null, 0.0, OUTCOME_ERROR);
            throw new ServiceUnavailableException("Chatbot service is temporarily unavailable. Please try again shortly.", e);
        }
    }

    private FaqMatch findNearestFaq(float[] vector) {
        if (vector == null || vector.length == 0) {
            log.debug("Step 3 FAQ match | skipped — embedding vector was empty");
            return new FaqMatch(Optional.empty(), 0.0);
        }

        String literal = EmbeddingService.toVectorLiteral(vector);
        log.debug("Step 3 vector search | literal length={} | prefix=\"{}\"",
                literal.length(), literal.substring(0, Math.min(60, literal.length())));

        List<SearchResult> results = searchNearestVectors(literal);
        log.debug("Step 3 vector results | count={} | topId={} | dist={}",
                results.size(),
                results.isEmpty() ? "none" : results.getFirst().faqId(),
                results.isEmpty() ? "none" : String.format("%.4f", results.getFirst().distance()));

        if (results.isEmpty()) {
            log.debug("Step 3 FAQ match | no active FAQ found with embeddings");
            return new FaqMatch(Optional.empty(), 0.0);
        }

        SearchResult top = results.getFirst();
        Optional<Faq> matchOpt = faqRepository.findById(top.faqId());
        double similarity = 1.0 - top.distance();
        if (matchOpt.isPresent()) {
            log.debug("Step 3 FAQ match | faqId={} | similarity={} | question=\"{}\"",
                    matchOpt.get().getId(), String.format("%.4f", similarity), matchOpt.get().getQuestion());
        } else {
            log.warn("Step 3 FAQ match | id={} returned by vector search but not found in repo", top.faqId());
        }
        return new FaqMatch(matchOpt, similarity);
    }

    private List<SearchResult> searchNearestVectors(String literal) {
        return jdbcTemplate.query(
                con -> {
                    PreparedStatement ps = con.prepareStatement(VECTOR_SEARCH_SQL);
                    ps.setObject(1, literal, Types.OTHER);
                    return ps;
                },
                (rs, rowNum) -> new SearchResult(rs.getString("id"), rs.getDouble("dist"))
        );
    }

    private ChatbotQueryResponse escalate(String userId, String rawQuery, FaqMatch match, double roundedSimilarity) {
        log.debug("Step 4 threshold | ESCALATED | similarity={} threshold={} | faqFound={}",
                roundedSimilarity, props.confidenceThreshold(), match.faq().isPresent());
        interactionLogService.logInteraction(userId, rawQuery, null, match.similarity(), OUTCOME_ESCALATED);
        return new ChatbotQueryResponse(ESCALATION_HINT, roundedSimilarity, OUTCOME_ESCALATED);
    }

    private ChatbotQueryResponse respondWithAnswer(String userId, String rawQuery, String rewrittenQuery,
                                                     ConversationContext ctx, FaqMatch match, double roundedSimilarity) {
        Faq faq = match.faq().get();
        log.debug("Step 4 threshold | PASSED | similarity={} >= threshold={}", roundedSimilarity, props.confidenceThreshold());

        String answer = llmService.generateAnswer(
                rewrittenQuery,
                faq.getQuestion(),
                faq.getAnswer(),
                ctx.rollingSummary()
        );
        log.debug("Step 5 answer | length={} chars", answer != null ? answer.length() : 0);

        contextService.addTurn(userId, rawQuery, answer);

        interactionLogService.logInteraction(userId, rawQuery, faq.getId(), match.similarity(), OUTCOME_ANSWERED);
        log.debug("Chatbot query done | outcome=ANSWERED | userId={}", userId);

        return new ChatbotQueryResponse(answer, roundedSimilarity, OUTCOME_ANSWERED);
    }

    public PageResponse<ChatbotInteractionResponse> listInteractions(
            String userId, String outcome, Instant from, Instant to, Pageable pageable) {
        return PageResponse.from(
                interactionRepository.findAllFiltered(userId, outcome, from, to, pageable)
                        .map(ChatbotInteractionResponse::from)
        );
    }

}
