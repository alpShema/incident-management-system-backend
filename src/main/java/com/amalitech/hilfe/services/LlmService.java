package com.amalitech.hilfe.services;

import java.util.List;

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
}
