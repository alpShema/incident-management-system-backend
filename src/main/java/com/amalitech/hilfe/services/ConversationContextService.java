package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationContextService {

    private final ChatbotProperties props;
    private final LlmService llmService;

    private final Map<String, ConversationContext> contexts = new ConcurrentHashMap<>();

    public ConversationContext getContext(String userId) {
        return contexts.getOrDefault(userId, new ConversationContext(Collections.emptyList(), ""));
    }

    public void addTurn(String userId, String query, String answer) {
        ConversationContext current = contexts.computeIfAbsent(userId,
                k -> new ConversationContext(new ArrayList<>(), ""));

        List<String> turns = new ArrayList<>(current.recentTurns());
        turns.add("User: " + query);
        turns.add("Assistant: " + answer);

        // Cap to window size (each exchange is 2 entries)
        int maxEntries = props.conversationWindowSize() * 2;
        if (turns.size() > maxEntries) {
            turns = turns.subList(turns.size() - maxEntries, turns.size());
        }

        String summary = current.rollingSummary();
        int totalTurns = turns.size() / 2;
        if (totalTurns > 0 && totalTurns % props.summaryTriggerInterval() == 0) {
            summary = refreshSummary(userId, turns, current.rollingSummary());
        }

        contexts.put(userId, new ConversationContext(turns, summary));
    }

    public void clearContext(String userId) {
        contexts.remove(userId);
    }

    private String refreshSummary(String userId, List<String> turns, String existingSummary) {
        try {
            String history = String.join("\n", turns);
            String prompt = "Summarise this conversation concisely, capturing key facts the user has mentioned:\n"
                    + (existingSummary.isEmpty() ? "" : "Prior summary: " + existingSummary + "\n")
                    + history;
            return llmService.rewriteQuery(prompt, Collections.emptyList());
        } catch (Exception e) {
            log.warn("Failed to refresh conversation summary for user {}: {}", userId, e.getMessage());
            return existingSummary;
        }
    }

    public record ConversationContext(List<String> recentTurns, String rollingSummary) {}
}
