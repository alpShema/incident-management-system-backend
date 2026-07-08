package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.LlmProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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
            Every fact in your answer must come from the FAQ content provided below.
            Do NOT add information, speculate, or infer anything beyond what is written there.
            Do NOT just copy the FAQ answer verbatim — rephrase it in your own words, in a \
            friendly conversational tone, and address the user's specific wording of the question.
            If the FAQ content does not fully address the question, say so clearly and \
            suggest the user raise a support ticket.
            Keep your answer concise and directly relevant to how the user asked.
            """;

    private final RestClient restClient;
    private final LlmProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiLlmService(LlmProperties llmProps, AmaliAiProperties amaliAiProps) {
        this.props = llmProps;
        this.restClient = RestClient.builder()
                .baseUrl(amaliAiProps.llmUrl())
                .defaultHeader("X-Api-Key", amaliAiProps.apiKey())
                .defaultHeader("Provider", amaliAiProps.provider())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("OpenAiLlmService initialized — model={}, temperature={}, maxTokens={}, url={}",
                llmProps.model(), llmProps.temperature(), llmProps.maxTokens(), amaliAiProps.llmUrl());
    }

    @Override
    public String rewriteQuery(String userQuery, List<String> recentTurns) {
        log.debug("LLM rewrite | query=\"{}\" | historyTurns={}", userQuery, recentTurns.size());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, "system", CONTENT, REWRITE_SYSTEM_PROMPT));

        if (!recentTurns.isEmpty()) {
            String history = String.join("\n", recentTurns);
            messages.add(Map.of(ROLE, "user", CONTENT,
                    "Conversation so far:\n" + history + "\n\nLatest message: " + userQuery));
        } else {
            messages.add(Map.of(ROLE, "user", CONTENT, userQuery));
        }

        return callChatCompletion("rewrite", messages);
    }

    @Override
    public String generateAnswer(String userQuery, String faqQuestion, String faqAnswer, String conversationSummary) {
        log.debug("LLM answer | query=\"{}\" | faqQuestion=\"{}\" | hasSummary={}",
                userQuery, faqQuestion, conversationSummary != null && !conversationSummary.isBlank());
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

        return callChatCompletion("answer", messages);
    }

    @Override
    public void streamAnswer(String userQuery, String faqQuestion, String faqAnswer,
                              String conversationSummary, Consumer<String> onChunk) {
        log.debug("LLM stream answer | query=\"{}\" | faqQuestion=\"{}\" | hasSummary={}",
                userQuery, faqQuestion, conversationSummary != null && !conversationSummary.isBlank());
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

        streamChatCompletion("answer-stream", messages, onChunk);
    }

    private void streamChatCompletion(String operation, List<Map<String, String>> messages, Consumer<String> onChunk) {
        long start = System.currentTimeMillis();
        try {
            restClient.post()
                    .body(Map.of(
                            "model", props.model(),
                            "messages", messages,
                            "temperature", props.temperature(),
                            "max_tokens", props.maxTokens(),
                            "stream", true
                    ))
                    .exchange((request, response) -> {
                        readSseStream(response.getBody(), onChunk);
                        return null;
                    });
            log.debug("LLM {} stream done | {}ms", operation, System.currentTimeMillis() - start);
        } catch (RestClientException e) {
            log.error("LLM {} stream failed | {}ms | {}", operation, System.currentTimeMillis() - start, e.getMessage());
            throw new ServiceUnavailableException("LLM service is temporarily unavailable", e);
        }
    }

    private void readSseStream(java.io.InputStream body, Consumer<String> onChunk) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                StreamChunk chunk = objectMapper.readValue(data, StreamChunk.class);
                if (chunk.choices() == null || chunk.choices().isEmpty()) {
                    continue;
                }
                String content = chunk.choices().getFirst().delta().content();
                if (content != null && !content.isEmpty()) {
                    onChunk.accept(content);
                }
            }
        }
    }

    private String callChatCompletion(String operation, List<Map<String, String>> messages) {
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = restClient.post()
                    .body(Map.of(
                            "model", props.model(),
                            "messages", messages,
                            "temperature", props.temperature(),
                            "max_tokens", props.maxTokens()
                    ))
                    .retrieve()
                    .body(ChatResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new ServiceUnavailableException("LLM API returned an empty response");
            }

            String content = response.choices().getFirst().message().content();
            log.debug("LLM {} done | {}ms | {} chars | model={}", operation, System.currentTimeMillis() - start, content.length(), props.model());
            return content;

        } catch (RestClientException e) {
            log.error("LLM {} failed | {}ms | {}", operation, System.currentTimeMillis() - start, e.getMessage());
            throw new ServiceUnavailableException("LLM service is temporarily unavailable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatResponse(List<Choice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StreamChunk(List<StreamChoice> choices) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StreamChoice(Delta delta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delta(String content) {}
}
