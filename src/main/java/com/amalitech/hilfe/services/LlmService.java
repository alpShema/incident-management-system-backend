package com.amalitech.hilfe.services;

import java.util.List;
import java.util.function.Consumer;

public interface LlmService {

    /**
     * Rewrites the user's latest message into a self-contained query using recent conversation turns.
     * Returns the original query unchanged when no API key is configured (stub mode).
     */
    String rewriteQuery(String userQuery, List<String> recentTurns);

    /**
     * Generates a conversational answer grounded strictly in the provided FAQ content.
     * Returns the FAQ answer verbatim when no API key is configured (stub mode).
     */
    String generateAnswer(
            String userQuery,
            String faqQuestion,
            String faqAnswer,
            String conversationSummary
    );

    /**
     * Same as {@link #generateAnswer} but streams the completion, invoking {@code onChunk}
     * with each text delta as it arrives. In stub mode, invokes {@code onChunk} once with
     * the full FAQ answer.
     */
    void streamAnswer(
            String userQuery,
            String faqQuestion,
            String faqAnswer,
            String conversationSummary,
            Consumer<String> onChunk
    );

    /**
     * Resolves pronouns/context from {@code recentTurns} and splits the message into one or
     * more self-contained sub-questions. Returns a one-element list containing the resolved
     * message unchanged when it is already a single question. Never returns an empty list.
     */
    List<String> splitQuestions(String rawQuery, List<String> recentTurns);

    /**
     * Composes one combined answer covering multiple matched FAQs and explicitly noting any
     * sub-questions that couldn't be matched. Returns the FAQ answers concatenated verbatim
     * when no API key is configured (stub mode).
     */
    String generateMultiAnswer(
            List<FaqMatchForAnswer> matches,
            List<String> unansweredSubQuestions,
            String conversationSummary
    );

    /**
     * Same as {@link #generateMultiAnswer} but streams the completion, invoking {@code onChunk}
     * with each text delta as it arrives.
     */
    void streamMultiAnswer(
            List<FaqMatchForAnswer> matches,
            List<String> unansweredSubQuestions,
            String conversationSummary,
            Consumer<String> onChunk
    );

    record FaqMatchForAnswer(String subQuestion, String faqQuestion, String faqAnswer) {}
}
