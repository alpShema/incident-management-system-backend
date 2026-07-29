package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import com.amalitech.hilfe.dto.ChatbotAnswerChunk;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.ChatbotInteractionRepository;
import com.amalitech.hilfe.repositories.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatbotServiceTest {

    @Mock FaqRepository faqRepository;
    @Mock ChatbotInteractionRepository interactionRepository;
    @Mock EmbeddingService embeddingService;
    @Mock LlmService llmService;
    @Mock ConversationContextService contextService;
    @Mock ChatbotInteractionLogService interactionLogService;
    @Mock JdbcTemplate jdbcTemplate;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        ChatbotProperties props = new ChatbotProperties(0.5, 30, 10, 5);
        chatbotService = new ChatbotService(
                faqRepository, interactionRepository, embeddingService, llmService,
                contextService, props, interactionLogService, jdbcTemplate);
    }

    /** Stubs the vector-search + FAQ lookup path so findNearestFaq resolves to a single match. */
    private void stubFaqMatch(String faqId, double distance, Faq faq) {
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(faqId);
                    when(rs.getDouble("dist")).thenReturn(distance);
                    return List.of(rowMapper.mapRow(rs, 0));
                });
        when(faqRepository.findById(faqId)).thenReturn(Optional.of(faq));
    }

    @Test
    void queryStream_doesNotRunPreludeUntilSubscribed() {
        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "hello");

        verifyNoInteractions(contextService, llmService, embeddingService, jdbcTemplate, interactionLogService);

        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.rewriteQuery(eq("hello"), any())).thenReturn("hello");
        when(embeddingService.embed("hello")).thenReturn(null);

        StepVerifier.create(stream)
                .expectNextCount(2)
                .verifyComplete();

        verify(contextService).getContext("user-1");
    }

    @Test
    void queryStream_happyPath_streamsDeltasThenDoneWithAnsweredOutcome() {
        Faq faq = Faq.builder().id("faq-1").question("How do I reset my password?").answer("Use the reset link.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), "summary"));
        when(llmService.rewriteQuery(eq("how do I reset it"), any())).thenReturn("How do I reset my password?");
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f, 0.2f});
        stubFaqMatch("faq-1", 0.1, faq); // similarity = 0.9, above the 0.5 threshold

        doAnswer(invocation -> {
            java.util.function.Consumer<String> onChunk = invocation.getArgument(4);
            onChunk.accept("Hello ");
            onChunk.accept("world");
            return null;
        }).when(llmService).streamAnswer(eq("How do I reset my password?"), eq(faq.getQuestion()), eq(faq.getAnswer()), eq("summary"), any());

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "how do I reset it");

        StepVerifier.create(stream)
                .expectNext(ChatbotAnswerChunk.delta("Hello "))
                .expectNext(ChatbotAnswerChunk.delta("world"))
                .expectNextMatches(chunk -> chunk.done() && "ANSWERED".equals(chunk.outcome()) && chunk.confidence() == 0.9)
                .verifyComplete();

        verify(contextService).addTurn("user-1", "how do I reset it", "Hello world");
        verify(interactionLogService).logInteraction("user-1", "how do I reset it", "faq-1", 0.9, "ANSWERED");
    }

    @Test
    void queryStream_belowConfidenceThreshold_escalatesWithSingleHintChunk() {
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.rewriteQuery(anyString(), any())).thenReturn("what is this");
        when(embeddingService.embed("what is this")).thenReturn(null); // empty vector short-circuits to no match

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "what is this");

        StepVerifier.create(stream)
                .expectNextMatches(chunk -> !chunk.done() && chunk.delta() != null)
                .expectNextMatches(chunk -> chunk.done() && "ESCALATED".equals(chunk.outcome()) && chunk.confidence() == 0.0)
                .verifyComplete();

        verify(interactionLogService).logInteraction("user-1", "what is this", null, 0.0, "ESCALATED");
        verify(llmService, never()).streamAnswer(any(), any(), any(), any(), any());
    }

    @Test
    void queryStream_llmStreamFailure_emitsServiceUnavailableAndLogsError() {
        Faq faq = Faq.builder().id("faq-1").question("Q").answer("A").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.rewriteQuery(anyString(), any())).thenReturn("query");
        when(embeddingService.embed("query")).thenReturn(new float[]{0.1f});
        stubFaqMatch("faq-1", 0.1, faq);

        doThrow(new RuntimeException("boom"))
                .when(llmService).streamAnswer(any(), any(), any(), any(), any());

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "query");

        StepVerifier.create(stream)
                .expectError(ServiceUnavailableException.class)
                .verify();

        verify(interactionLogService).logInteraction("user-1", "query", null, 0.0, "ERROR");
        verify(contextService, never()).addTurn(any(), any(), any());
    }
}
