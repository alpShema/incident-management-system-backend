package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.LlmProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Slf4j
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${amali-ai.api-key:}')")
public class OpenAiLlmService implements LlmService {

    private static final String ROLE    = "role";
    private static final String CONTENT = "content";
    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER   = "user";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final String SSE_DONE_MARKER = "[DONE]";
    private static final String CONVERSATION_CONTEXT_PREFIX = "Conversation context: ";

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

    private static final String SPLIT_SYSTEM_PROMPT = """
            You are a query understanding step for a support FAQ chatbot.
            Given the user's latest message and the recent conversation history, resolve any \
            pronouns or references using the history so each question can be understood on its own.
            Then check whether the message contains more than one distinct question.
            Output each distinct, self-contained question on its own line, with no numbering, \
            bullets, or explanation — just the question text.
            If there is only one question, output exactly one line containing it, resolved \
            against the history but otherwise unchanged.
            Output only the questions, one per line, and nothing else.
            """;

    private static final String MULTI_ANSWER_SYSTEM_PROMPT = """
            You are a helpful support assistant for a helpdesk system.
            The user asked multiple questions in one message. You are given one or more FAQ \
            entries, each paired with the specific sub-question it answers, and possibly a list \
            of sub-questions with no matching FAQ.
            Every fact in your answer must come from the FAQ content provided — do not add \
            information, speculate, or infer anything beyond what is written there.
            Compose a single conversational reply that addresses each sub-question in turn, \
            in your own words rather than copying the FAQ text verbatim.
            For each sub-question with no matching FAQ, clearly say you couldn't find an answer \
            to it and suggest the user raise a support ticket for that part.
            Keep the reply concise and organized so the user can tell which part answers which question.
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
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, REWRITE_SYSTEM_PROMPT));

        if (!recentTurns.isEmpty()) {
            String history = String.join("\n", recentTurns);
            messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                    "Conversation so far:\n" + history + "\n\nLatest message: " + userQuery));
        } else {
            messages.add(Map.of(ROLE, ROLE_USER, CONTENT, userQuery));
        }

        return callChatCompletion("rewrite", messages);
    }

    @Override
    public String generateAnswer(String userQuery, String faqQuestion, String faqAnswer, String conversationSummary) {
        log.debug("LLM answer | query=\"{}\" | faqQuestion=\"{}\" | hasSummary={}",
                userQuery, faqQuestion, conversationSummary != null && !conversationSummary.isBlank());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, ANSWER_SYSTEM_PROMPT));
        messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                buildAnswerUserContent(userQuery, faqQuestion, faqAnswer, conversationSummary)));

        return callChatCompletion("answer", messages);
    }

    @Override
    public void streamAnswer(String userQuery, String faqQuestion, String faqAnswer,
                              String conversationSummary, Consumer<String> onChunk) {
        log.debug("LLM stream answer | query=\"{}\" | faqQuestion=\"{}\" | hasSummary={}",
                userQuery, faqQuestion, conversationSummary != null && !conversationSummary.isBlank());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, ANSWER_SYSTEM_PROMPT));
        messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                buildAnswerUserContent(userQuery, faqQuestion, faqAnswer, conversationSummary)));

        streamChatCompletion("answer-stream", messages, onChunk);
    }

    private static String buildAnswerUserContent(String userQuery, String faqQuestion, String faqAnswer,
                                                  String conversationSummary) {
        StringBuilder userContent = new StringBuilder();
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            userContent.append(CONVERSATION_CONTEXT_PREFIX).append(conversationSummary).append("\n\n");
        }
        userContent.append("FAQ content:\n")
                .append("Q: ").append(faqQuestion).append("\n")
                .append("A: ").append(faqAnswer).append("\n\n")
                .append("User's question: ").append(userQuery);
        return userContent.toString();
    }

    @Override
    public List<String> splitQuestions(String rawQuery, List<String> recentTurns) {
        log.debug("LLM split | query=\"{}\" | historyTurns={}", rawQuery, recentTurns.size());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, SPLIT_SYSTEM_PROMPT));

        if (!recentTurns.isEmpty()) {
            String history = String.join("\n", recentTurns);
            messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                    "Conversation so far:\n" + history + "\n\nLatest message: " + rawQuery));
        } else {
            messages.add(Map.of(ROLE, ROLE_USER, CONTENT, rawQuery));
        }

        String raw = callChatCompletion("split", messages);
        List<String> parsed = parseSplitLines(raw);
        return parsed.isEmpty() ? List.of(rawQuery) : parsed;
    }

    private static List<String> parseSplitLines(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split("\n"))
                .map(line -> line.replaceFirst("^[\\s\\-*•]+", "").replaceFirst("^\\d+[.)]\\s*", "").trim())
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public String generateMultiAnswer(List<FaqMatchForAnswer> matches, List<String> unansweredSubQuestions,
                                       String conversationSummary) {
        log.debug("LLM multi-answer | matches={} | unanswered={}", matches.size(), unansweredSubQuestions.size());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, MULTI_ANSWER_SYSTEM_PROMPT));
        messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                buildMultiAnswerUserContent(matches, unansweredSubQuestions, conversationSummary)));

        return callChatCompletion("multi-answer", messages);
    }

    @Override
    public void streamMultiAnswer(List<FaqMatchForAnswer> matches, List<String> unansweredSubQuestions,
                                   String conversationSummary, Consumer<String> onChunk) {
        log.debug("LLM stream multi-answer | matches={} | unanswered={}", matches.size(), unansweredSubQuestions.size());
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of(ROLE, ROLE_SYSTEM, CONTENT, MULTI_ANSWER_SYSTEM_PROMPT));
        messages.add(Map.of(ROLE, ROLE_USER, CONTENT,
                buildMultiAnswerUserContent(matches, unansweredSubQuestions, conversationSummary)));

        streamChatCompletion("multi-answer-stream", messages, onChunk);
    }

    private static String buildMultiAnswerUserContent(List<FaqMatchForAnswer> matches, List<String> unansweredSubQuestions,
                                                        String conversationSummary) {
        StringBuilder userContent = new StringBuilder();
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            userContent.append(CONVERSATION_CONTEXT_PREFIX).append(conversationSummary).append("\n\n");
        }
        for (int i = 0; i < matches.size(); i++) {
            FaqMatchForAnswer m = matches.get(i);
            userContent.append("Sub-question ").append(i + 1).append(": ").append(m.subQuestion()).append("\n")
                    .append("Matched FAQ Q: ").append(m.faqQuestion()).append("\n")
                    .append("Matched FAQ A: ").append(m.faqAnswer()).append("\n\n");
        }
        if (!unansweredSubQuestions.isEmpty()) {
            userContent.append("Sub-questions with no FAQ match:\n");
            unansweredSubQuestions.forEach(q -> userContent.append("- ").append(q).append("\n"));
        }
        return userContent.toString();
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
            String line = reader.readLine();
            while (line != null) {
                String data = sseDataOf(line);
                if (SSE_DONE_MARKER.equals(data)) {
                    return;
                }
                if (data != null) {
                    extractDelta(data).ifPresent(onChunk);
                }
                line = reader.readLine();
            }
        }
    }

    private static String sseDataOf(String line) {
        if (!line.startsWith(SSE_DATA_PREFIX)) {
            return null;
        }
        String data = line.substring(SSE_DATA_PREFIX.length()).trim();
        return data.isEmpty() ? null : data;
    }

    private Optional<String> extractDelta(String data) throws IOException {
        StreamChunk chunk = objectMapper.readValue(data, StreamChunk.class);
        if (chunk.choices() == null || chunk.choices().isEmpty()) {
            return Optional.empty();
        }
        String content = chunk.choices().getFirst().delta().content();
        return (content != null && !content.isEmpty()) ? Optional.of(content) : Optional.empty();
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
