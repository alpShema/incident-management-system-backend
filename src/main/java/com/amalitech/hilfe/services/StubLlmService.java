package com.amalitech.hilfe.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
@ConditionalOnMissingBean(LlmService.class)
public class StubLlmService implements LlmService {

    public StubLlmService() {
        log.warn("StubLlmService is active — no llm.api-key configured. "
                + "Query rewriting and answer generation are disabled until a real key is provided.");
    }

    @Override
    public String rewriteQuery(String userQuery, List<String> recentTurns) {
        log.debug("StubLlmService: returning original query unchanged");
        return userQuery;
    }

    @Override
    public String generateAnswer(String userQuery, String faqQuestion, String faqAnswer, String conversationSummary) {
        log.debug("StubLlmService: returning FAQ answer verbatim");
        return faqAnswer;
    }

    @Override
    public void streamAnswer(String userQuery, String faqQuestion, String faqAnswer,
                              String conversationSummary, Consumer<String> onChunk) {
        log.debug("StubLlmService: streaming FAQ answer verbatim as a single chunk");
        onChunk.accept(faqAnswer);
    }

    @Override
    public List<String> splitQuestions(String rawQuery, List<String> recentTurns) {
        log.debug("StubLlmService: returning single question unchanged");
        return List.of(rawQuery);
    }

    @Override
    public String generateMultiAnswer(List<FaqMatchForAnswer> matches, List<String> unansweredSubQuestions,
                                       String conversationSummary) {
        log.debug("StubLlmService: concatenating FAQ answers verbatim");
        StringBuilder sb = new StringBuilder();
        matches.forEach(m -> sb.append(m.faqAnswer()).append("\n\n"));
        unansweredSubQuestions.forEach(q -> sb.append("I couldn't find an answer for: ").append(q).append("\n"));
        return sb.toString().stripTrailing();
    }

    @Override
    public void streamMultiAnswer(List<FaqMatchForAnswer> matches, List<String> unansweredSubQuestions,
                                   String conversationSummary, Consumer<String> onChunk) {
        log.debug("StubLlmService: streaming concatenated FAQ answers as a single chunk");
        onChunk.accept(generateMultiAnswer(matches, unansweredSubQuestions, conversationSummary));
    }
}
