package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import com.amalitech.hilfe.dto.ChatbotAnswerChunk;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatbotService {

    private static final String OUTCOME_ANSWERED  = "ANSWERED";
    private static final String OUTCOME_ESCALATED = "ESCALATED";
    private static final String OUTCOME_ERROR     = "ERROR";
    private static final String CHATBOT_UNAVAILABLE_MESSAGE = "Chatbot service is temporarily unavailable. Please try again shortly.";

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

    private record AnswerContext(String userId, String rawQuery, String resolvedQuery, String rollingSummary,
                                  Faq faq, double similarity, double roundedSimilarity) {}

    private record SubQuestionResolution(
            List<LlmService.FaqMatchForAnswer> matched,
            List<Double> matchedSimilarities,
            List<String> matchedFaqIds,
            List<String> unanswered,
            double bestSimilaritySeen
    ) {}

    private record MultiAnswerContext(String userId, String rawQuery, String rollingSummary, SubQuestionResolution resolution) {}

    public ChatbotQueryResponse query(String userId, String rawQuery) {
        log.debug("Chatbot query start | userId={} | rawQuery=\"{}\"", userId, rawQuery);
        ConversationContext ctx = contextService.getContext(userId);

        try {
            List<String> subQuestions = safeSplit(rawQuery, ctx.recentTurns());
            log.debug("Step 1 split | subQuestions={}", subQuestions.size());

            if (subQuestions.size() == 1) {
                String resolvedQuery = subQuestions.get(0);
                float[] vector = embeddingService.embed(resolvedQuery);
                log.debug("Step 2 embed | dims={}", vector != null ? vector.length : 0);

                FaqMatch match = findNearestFaq(vector);
                double roundedSimilarity = round2(match.similarity());

                if (match.faq().isEmpty() || match.similarity() < props.confidenceThreshold()) {
                    return escalate(userId, rawQuery, match, roundedSimilarity);
                }

                return respondWithAnswer(userId, rawQuery, resolvedQuery, ctx, match, roundedSimilarity);
            }

            SubQuestionResolution resolution = resolveSubQuestions(subQuestions);
            if (resolution.matched().isEmpty()) {
                FaqMatch synthetic = new FaqMatch(Optional.empty(), resolution.bestSimilaritySeen());
                return escalate(userId, rawQuery, synthetic, round2(synthetic.similarity()));
            }
            return respondWithMultiAnswer(userId, rawQuery, ctx, resolution);

        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chatbot query failed for user {}: {}", userId, e.getMessage(), e);
            interactionLogService.logInteraction(userId, rawQuery, null, 0.0, OUTCOME_ERROR);
            throw new ServiceUnavailableException(CHATBOT_UNAVAILABLE_MESSAGE, e);
        }
    }

    private List<String> safeSplit(String rawQuery, List<String> recentTurns) {
        List<String> split = llmService.splitQuestions(rawQuery, recentTurns);
        if (split == null || split.isEmpty()) {
            log.warn("splitQuestions returned empty — falling back to whole message");
            return List.of(rawQuery);
        }
        return split.size() > props.maxSubQuestions() ? split.subList(0, props.maxSubQuestions()) : split;
    }

    private SubQuestionResolution resolveSubQuestions(List<String> subQuestions) {
        Map<String, LlmService.FaqMatchForAnswer> byFaqId = new LinkedHashMap<>();
        Map<String, Double> similarityByFaqId = new HashMap<>();
        List<String> unanswered = new ArrayList<>();
        double best = 0.0;

        for (String sub : subQuestions) {
            try {
                float[] vector = embeddingService.embed(sub);
                FaqMatch match = findNearestFaq(vector);
                best = Math.max(best, match.similarity());
                if (match.faq().isPresent() && match.similarity() >= props.confidenceThreshold()) {
                    Faq faq = match.faq().get();
                    byFaqId.putIfAbsent(faq.getId(), new LlmService.FaqMatchForAnswer(sub, faq.getQuestion(), faq.getAnswer()));
                    similarityByFaqId.merge(faq.getId(), match.similarity(), Math::max);
                } else {
                    unanswered.add(sub);
                }
            } catch (Exception e) {
                log.warn("Sub-question resolution failed, treating as unanswered | subQuestion=\"{}\" | {}", sub, e.getMessage());
                unanswered.add(sub);
            }
        }

        List<String> matchedFaqIds = new ArrayList<>(byFaqId.keySet());
        return new SubQuestionResolution(
                new ArrayList<>(byFaqId.values()),
                matchedFaqIds.stream().map(similarityByFaqId::get).toList(),
                matchedFaqIds,
                unanswered,
                best);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static String highestSimilarityFaqId(SubQuestionResolution r) {
        int bestIdx = 0;
        for (int i = 1; i < r.matchedSimilarities().size(); i++) {
            if (r.matchedSimilarities().get(i) > r.matchedSimilarities().get(bestIdx)) {
                bestIdx = i;
            }
        }
        return r.matchedFaqIds().get(bestIdx);
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

    private ChatbotQueryResponse respondWithAnswer(String userId, String rawQuery, String resolvedQuery,
                                                     ConversationContext ctx, FaqMatch match, double roundedSimilarity) {
        Faq faq = match.faq().get();
        log.debug("Step 4 threshold | PASSED | similarity={} >= threshold={}", roundedSimilarity, props.confidenceThreshold());

        String answer = llmService.generateAnswer(
                resolvedQuery,
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

    private ChatbotQueryResponse respondWithMultiAnswer(String userId, String rawQuery, ConversationContext ctx,
                                                          SubQuestionResolution r) {
        String answer = llmService.generateMultiAnswer(r.matched(), r.unanswered(), ctx.rollingSummary());
        double avgSimilarity = average(r.matchedSimilarities());
        double roundedSimilarity = round2(avgSimilarity);
        String primaryFaqId = highestSimilarityFaqId(r);

        contextService.addTurn(userId, rawQuery, answer);
        interactionLogService.logInteraction(userId, rawQuery, primaryFaqId, avgSimilarity, OUTCOME_ANSWERED);
        log.debug("Chatbot query done | outcome=ANSWERED (multi, {} matched, {} unanswered) | userId={}",
                r.matched().size(), r.unanswered().size(), userId);

        return new ChatbotQueryResponse(answer, roundedSimilarity, OUTCOME_ANSWERED);
    }

    /**
     * Streaming variant of {@link #query}. Performs the rewrite/embed/search/threshold steps
     * then streams the LLM answer as incremental {@link ChatbotAnswerChunk}s, ending with a
     * chunk where {@code done=true} carrying the final confidence/outcome.
     * <p>
     * The whole body is deferred until subscription (rather than running the rewrite/embed/
     * search prelude eagerly when this method is called) so the GraphQL subscription is
     * established immediately and none of this blocking work stalls the caller — it all runs
     * on {@link Schedulers#boundedElastic()} once the client actually subscribes.
     */
    public Flux<ChatbotAnswerChunk> queryStream(String userId, String rawQuery) {
        return Flux.defer(() -> buildStream(userId, rawQuery)).subscribeOn(Schedulers.boundedElastic());
    }

    private Flux<ChatbotAnswerChunk> buildStream(String userId, String rawQuery) {
        log.debug("Chatbot stream start | userId={} | rawQuery=\"{}\"", userId, rawQuery);
        ConversationContext ctx = contextService.getContext(userId);

        List<String> subQuestions = safeSplit(rawQuery, ctx.recentTurns());

        if (subQuestions.size() == 1) {
            String resolvedQuery = subQuestions.get(0);
            float[] vector = embeddingService.embed(resolvedQuery);
            FaqMatch match = findNearestFaq(vector);
            double roundedSimilarity = round2(match.similarity());

            if (match.faq().isEmpty() || match.similarity() < props.confidenceThreshold()) {
                return escalationStream(userId, rawQuery, match, roundedSimilarity);
            }

            AnswerContext answerContext = new AnswerContext(
                    userId, rawQuery, resolvedQuery, ctx.rollingSummary(), match.faq().get(), match.similarity(), roundedSimilarity);
            return answerStream(answerContext);
        }

        SubQuestionResolution resolution = resolveSubQuestions(subQuestions);
        if (resolution.matched().isEmpty()) {
            FaqMatch synthetic = new FaqMatch(Optional.empty(), resolution.bestSimilaritySeen());
            return escalationStream(userId, rawQuery, synthetic, round2(synthetic.similarity()));
        }
        return multiAnswerStream(new MultiAnswerContext(userId, rawQuery, ctx.rollingSummary(), resolution));
    }

    private Flux<ChatbotAnswerChunk> escalationStream(String userId, String rawQuery, FaqMatch match, double roundedSimilarity) {
        interactionLogService.logInteraction(userId, rawQuery, null, match.similarity(), OUTCOME_ESCALATED);
        return Flux.just(
                ChatbotAnswerChunk.delta(ESCALATION_HINT),
                ChatbotAnswerChunk.done(roundedSimilarity, OUTCOME_ESCALATED)
        );
    }

    private Flux<ChatbotAnswerChunk> answerStream(AnswerContext context) {
        return Flux.<ChatbotAnswerChunk>create(sink -> emitAnswer(sink, context));
    }

    private void emitAnswer(FluxSink<ChatbotAnswerChunk> sink, AnswerContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        try {
            llmService.streamAnswer(context.resolvedQuery(), context.faq().getQuestion(), context.faq().getAnswer(),
                    context.rollingSummary(), delta -> emitDelta(sink, fullAnswer, delta));

            String answer = fullAnswer.toString();
            contextService.addTurn(context.userId(), context.rawQuery(), answer);
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), context.faq().getId(), context.similarity(), OUTCOME_ANSWERED);
            log.debug("Chatbot stream done | outcome=ANSWERED | userId={}", context.userId());

            sink.next(ChatbotAnswerChunk.done(context.roundedSimilarity(), OUTCOME_ANSWERED));
            sink.complete();
        } catch (StreamCancelledException e) {
            log.debug("Chatbot stream cancelled by client | userId={}", context.userId());
        } catch (ServiceUnavailableException e) {
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), null, 0.0, OUTCOME_ERROR);
            sink.error(e);
        } catch (Exception e) {
            log.error("Chatbot stream failed for user {}: {}", context.userId(), e.getMessage(), e);
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), null, 0.0, OUTCOME_ERROR);
            sink.error(new ServiceUnavailableException(CHATBOT_UNAVAILABLE_MESSAGE, e));
        }
    }

    private Flux<ChatbotAnswerChunk> multiAnswerStream(MultiAnswerContext context) {
        return Flux.<ChatbotAnswerChunk>create(sink -> emitMultiAnswer(sink, context));
    }

    private void emitMultiAnswer(FluxSink<ChatbotAnswerChunk> sink, MultiAnswerContext context) {
        StringBuilder fullAnswer = new StringBuilder();
        SubQuestionResolution r = context.resolution();
        double avgSimilarity = average(r.matchedSimilarities());
        double roundedSimilarity = round2(avgSimilarity);
        String primaryFaqId = highestSimilarityFaqId(r);

        try {
            llmService.streamMultiAnswer(r.matched(), r.unanswered(), context.rollingSummary(),
                    delta -> emitDelta(sink, fullAnswer, delta));

            String answer = fullAnswer.toString();
            contextService.addTurn(context.userId(), context.rawQuery(), answer);
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), primaryFaqId, avgSimilarity, OUTCOME_ANSWERED);
            log.debug("Chatbot stream done | outcome=ANSWERED (multi, {} matched, {} unanswered) | userId={}",
                    r.matched().size(), r.unanswered().size(), context.userId());

            sink.next(ChatbotAnswerChunk.done(roundedSimilarity, OUTCOME_ANSWERED));
            sink.complete();
        } catch (StreamCancelledException e) {
            log.debug("Chatbot stream cancelled by client | userId={}", context.userId());
        } catch (ServiceUnavailableException e) {
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), null, 0.0, OUTCOME_ERROR);
            sink.error(e);
        } catch (Exception e) {
            log.error("Chatbot stream failed for user {}: {}", context.userId(), e.getMessage(), e);
            interactionLogService.logInteraction(context.userId(), context.rawQuery(), null, 0.0, OUTCOME_ERROR);
            sink.error(new ServiceUnavailableException(CHATBOT_UNAVAILABLE_MESSAGE, e));
        }
    }

    private void emitDelta(FluxSink<ChatbotAnswerChunk> sink, StringBuilder fullAnswer, String delta) {
        if (sink.isCancelled()) {
            throw new StreamCancelledException();
        }
        fullAnswer.append(delta);
        sink.next(ChatbotAnswerChunk.delta(delta));
    }

    private static final class StreamCancelledException extends RuntimeException {
        StreamCancelledException() {
            super(null, null, false, false);
        }
    }

    public PageResponse<ChatbotInteractionResponse> listInteractions(
            String userId, String outcome, Instant from, Instant to, Pageable pageable) {
        return PageResponse.from(
                interactionRepository.findAllFiltered(userId, outcome, from, to, pageable)
                        .map(ChatbotInteractionResponse::from)
        );
    }

}
