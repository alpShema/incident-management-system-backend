package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.LlmProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "amali-ai.api-key", matchIfMissing = false)
public class OpenAiLlmService implements LlmService {

    private static final String ROLE    = "role";
    private static final String CONTENT = "content";

    private static final String REWRITE_SYSTEM_PROMPT = """
            You are a query rewriter for a support FAQ chatbot.
            Given the user's latest message and the recent conversation history, \
            rewrite the user's message into a single, self-contained question that \
            can be understood without any prior context.
            Return only the rewritten question — no explanation, no preamble.
            If the message is already self-contained, return it unchanged.
            """;

    private static final String ANSWER_SYSTEM_PROMPT = """
            You are a helpful support assistant for a helpdesk system.
            You must answer the user's question using ONLY the FAQ content provided below.
            Do NOT generate information that is not explicitly present in the FAQ content.
            Do NOT speculate, infer beyond what is written, or add your own knowledge.
            If the FAQ content does not fully address the question, say so clearly and \
            suggest the user raise a support ticket.
            Keep your answer concise, friendly, and directly relevant to how the user asked.
            """;

    private final RestClient restClient;
    private final LlmProperties props;

    public OpenAiLlmService(LlmProperties llmProps, AmaliAiProperties amaliAiProps) {
        this.props = llmProps;
        this.restClient = RestClient.builder()
                .baseUrl(amaliAiProps.llmUrl())
                .defaultHeader("X-Api-Key", amaliAiProps.apiKey())
                .defaultHeader("Provider", amaliAiProps.provider())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("OpenAiLlmService initialized — model={}, url={}", llmProps.model(), amaliAiProps.llmUrl());
    }

    @Override
    public String rewriteQuery(String userQuery, List<String> recentTurns) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, REWRITE_SYSTEM_PROMPT));

        if (!recentTurns.isEmpty()) {
            String history = String.join("\n", recentTurns);
            messages.add(Map.of(ROLE, "user", CONTENT,
                    "Conversation so far:\n" + history + "\n\nLatest message: " + userQuery));
        } else {
            messages.add(Map.of(ROLE, "user", CONTENT, userQuery));
        }

        return callChatCompletion(messages);
    }

    @Override
    public String generateAnswer(String userQuery, String faqQuestion, String faqAnswer, String conversationSummary) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, ANSWER_SYSTEM_PROMPT));

        StringBuilder userContent = new StringBuilder();
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            userContent.append("Conversation context: ").append(conversationSummary).append("\n\n");
        }
        userContent.append("FAQ content:\n")
                .append("Q: ").append(faqQuestion).append("\n")
                .append("A: ").append(faqAnswer).append("\n\n")
                .append("User's question: ").append(userQuery);

        messages.add(Map.of(ROLE, "user", CONTENT, userContent.toString()));

        return callChatCompletion(messages);
    }

    private String callChatCompletion(List<Map<String, String>> messages) {
        try {
            ChatResponse response = restClient.post()
                    .body(Map.of("model", props.model(), "messages", messages))
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ServiceUnavailableException("LLM API returned an empty response");
            }

            String content = response.choices().getFirst().message().content();
            log.debug("LLM response received ({} chars)", content.length());
            return content;

        } catch (RestClientException e) {
            log.error("LLM API call failed: {}", e.getMessage());
            throw new ServiceUnavailableException("LLM service is temporarily unavailable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}
}
