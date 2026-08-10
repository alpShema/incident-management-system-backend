package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.ChatbotProperties;
import com.amalitech.hilfe.dto.ChatbotAnswerChunk;
import com.amalitech.hilfe.dto.ChatbotQueryResponse;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.amalitech.hilfe.models.Faq;
import com.amalitech.hilfe.repositories.ChatbotInteractionRepository;
import com.amalitech.hilfe.repositories.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        ChatbotProperties props = new ChatbotProperties(0.5, 30, 10, 5, 5);
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

    private record StubEntry(String faqId, double distance, Faq faq) {}

    /**
     * Stubs the vector-search + FAQ lookup path so successive {@code findNearestFaq} calls
     * (one per sub-question) resolve to different matches, in call order.
     */
    private void stubFaqMatches(List<StubEntry> entries) {
        AtomicInteger callIndex = new AtomicInteger(0);
        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    int idx = Math.min(callIndex.getAndIncrement(), entries.size() - 1);
                    StubEntry entry = entries.get(idx);
                    RowMapper<?> rowMapper = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getString("id")).thenReturn(entry.faqId());
                    when(rs.getDouble("dist")).thenReturn(entry.distance());
                    return List.of(rowMapper.mapRow(rs, 0));
                });
        entries.forEach(e -> when(faqRepository.findById(e.faqId())).thenReturn(Optional.of(e.faq())));
    }

    @Test
    void query_singleQuestion_answersAsBefore() {
        Faq faq = Faq.builder().id("faq-1").question("How do I reset my password?").answer("Use the reset link.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), "summary"));
        when(llmService.splitQuestions(eq("how do I reset it"), any())).thenReturn(List.of("How do I reset my password?"));
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f, 0.2f});
        stubFaqMatch("faq-1", 0.1, faq); // similarity = 0.9, above the 0.5 threshold
        when(llmService.generateAnswer("How do I reset my password?", faq.getQuestion(), faq.getAnswer(), "summary"))
                .thenReturn("Here's how to reset it.");

        ChatbotQueryResponse response = chatbotService.query("user-1", "how do I reset it");

        assertThat(response.answer()).isEqualTo("Here's how to reset it.");
        assertThat(response.outcome()).isEqualTo("ANSWERED");
        assertThat(response.confidence()).isEqualTo(0.9);
        verify(contextService).addTurn("user-1", "how do I reset it", "Here's how to reset it.");
        verify(interactionLogService).logInteraction("user-1", "how do I reset it", "faq-1", 0.9, "ANSWERED");
    }

    @Test
    void query_singleQuestion_belowThreshold_escalates() {
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("what is this"));
        when(embeddingService.embed("what is this")).thenReturn(null);

        ChatbotQueryResponse response = chatbotService.query("user-1", "what is this");

        assertThat(response.outcome()).isEqualTo("ESCALATED");
        assertThat(response.confidence()).isEqualTo(0.0);
        verify(interactionLogService).logInteraction("user-1", "what is this", null, 0.0, "ESCALATED");
        verify(llmService, never()).generateAnswer(any(), any(), any(), any());
    }

    @Test
    void query_multipleQuestions_embedsEachAndComposesOneAnswer() {
        Faq pwFaq = Faq.builder().id("faq-pw").question("How do I reset my password?").answer("Use the reset link.").build();
        Faq loginFaq = Faq.builder().id("faq-login").question("How do I log in?").answer("Use your email and password.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(eq("how do I reset my password and login with it?"), any()))
                .thenReturn(List.of("How do I reset my password?", "How do I log in?"));
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("How do I log in?")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-pw", 0.1, pwFaq),     // similarity 0.9
                new StubEntry("faq-login", 0.2, loginFaq) // similarity 0.8
        ));
        when(llmService.generateMultiAnswer(any(), any(), any())).thenReturn("Here's both answers.");

        ChatbotQueryResponse response = chatbotService.query("user-1", "how do I reset my password and login with it?");

        assertThat(response.answer()).isEqualTo("Here's both answers.");
        assertThat(response.outcome()).isEqualTo("ANSWERED");
        assertThat(response.confidence()).isEqualTo(0.85); // average of 0.9 and 0.8

        ArgumentCaptor<List<LlmService.FaqMatchForAnswer>> matchesCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> unansweredCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateMultiAnswer(matchesCaptor.capture(), unansweredCaptor.capture(), eq(""));
        assertThat(matchesCaptor.getValue()).hasSize(2);
        assertThat(unansweredCaptor.getValue()).isEmpty();
        verify(embeddingService).embed("How do I reset my password?");
        verify(embeddingService).embed("How do I log in?");
    }

    @Test
    void query_multipleQuestions_oneUnmatched_stillAnsweredWithUnansweredTextPassed() {
        Faq pwFaq = Faq.builder().id("faq-pw").question("How do I reset my password?").answer("Use the reset link.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any()))
                .thenReturn(List.of("How do I reset my password?", "What is the weather today?"));
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("What is the weather today?")).thenReturn(new float[]{0.9f});
        stubFaqMatches(List.of(
                new StubEntry("faq-pw", 0.1, pwFaq),        // similarity 0.9, matched
                new StubEntry("faq-unrelated", 0.9, pwFaq)  // similarity 0.1, below 0.5 threshold
        ));
        when(llmService.generateMultiAnswer(any(), any(), any())).thenReturn("Answer plus a note about the other part.");

        ChatbotQueryResponse response = chatbotService.query("user-1", "how do I reset my password? what is the weather today?");

        assertThat(response.outcome()).isEqualTo("ANSWERED");
        assertThat(response.confidence()).isEqualTo(0.9); // average of matched only

        ArgumentCaptor<List<String>> unansweredCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateMultiAnswer(any(), unansweredCaptor.capture(), any());
        assertThat(unansweredCaptor.getValue()).containsExactly("What is the weather today?");
    }

    @Test
    void query_multipleQuestions_noneMatched_escalatesWithBestNearMissConfidence() {
        Faq unrelated = Faq.builder().id("faq-unrelated").question("Q").answer("A").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any()))
                .thenReturn(List.of("What is the weather?", "What time is it?"));
        when(embeddingService.embed("What is the weather?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("What time is it?")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-unrelated", 0.7, unrelated), // similarity 0.3
                new StubEntry("faq-unrelated", 0.6, unrelated)  // similarity 0.4 — the best near-miss
        ));

        ChatbotQueryResponse response = chatbotService.query("user-1", "what is the weather? what time is it?");

        assertThat(response.outcome()).isEqualTo("ESCALATED");
        assertThat(response.confidence()).isEqualTo(0.4);
        verify(interactionLogService).logInteraction("user-1", "what is the weather? what time is it?", null, 0.4, "ESCALATED");
        verify(llmService, never()).generateMultiAnswer(any(), any(), any());
    }

    @Test
    void query_multipleQuestions_sameFaqTwice_dedupedInPrompt() {
        Faq pwFaq = Faq.builder().id("faq-pw").question("How do I reset my password?").answer("Use the reset link.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any()))
                .thenReturn(List.of("How do I reset my password?", "How do I change my password?"));
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("How do I change my password?")).thenReturn(new float[]{0.15f});
        stubFaqMatches(List.of(
                new StubEntry("faq-pw", 0.1, pwFaq),
                new StubEntry("faq-pw", 0.05, pwFaq)
        ));
        when(llmService.generateMultiAnswer(any(), any(), any())).thenReturn("Answer.");

        chatbotService.query("user-1", "how do I reset my password? how do I change my password?");

        ArgumentCaptor<List<LlmService.FaqMatchForAnswer>> matchesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateMultiAnswer(matchesCaptor.capture(), any(), any());
        assertThat(matchesCaptor.getValue()).hasSize(1);
        verify(interactionLogService).logInteraction(eq("user-1"), anyString(), eq("faq-pw"), anyDouble(), eq("ANSWERED"));
    }

    @Test
    void query_splitReturnsEmpty_fallsBackToWholeMessage() {
        Faq faq = Faq.builder().id("faq-1").question("Q").answer("A").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(eq("some question"), any())).thenReturn(List.of());
        when(embeddingService.embed("some question")).thenReturn(new float[]{0.1f});
        stubFaqMatch("faq-1", 0.1, faq);
        when(llmService.generateAnswer(any(), any(), any(), any())).thenReturn("answer");

        ChatbotQueryResponse response = chatbotService.query("user-1", "some question");

        assertThat(response.outcome()).isEqualTo("ANSWERED");
        verify(embeddingService, times(1)).embed("some question");
    }

    @Test
    void query_splitExceedsCap_onlyMaxSubQuestionsEmbedded() {
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        List<String> sixQuestions = List.of("q1", "q2", "q3", "q4", "q5", "q6");
        when(llmService.splitQuestions(anyString(), any())).thenReturn(sixQuestions);
        when(embeddingService.embed(anyString())).thenReturn(null); // every sub-question ends up unanswered

        ChatbotQueryResponse response = chatbotService.query("user-1", "q1? q2? q3? q4? q5? q6?");

        assertThat(response.outcome()).isEqualTo("ESCALATED"); // props.maxSubQuestions() == 5, all below threshold
        verify(embeddingService, times(5)).embed(anyString());
    }

    @Test
    void query_multipleQuestions_logsOneInteractionWithHighestSimilarityFaqId() {
        Faq lowFaq = Faq.builder().id("faq-low").question("Q1").answer("A1").build();
        Faq highFaq = Faq.builder().id("faq-high").question("Q2").answer("A2").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("question one", "question two"));
        when(embeddingService.embed("question one")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("question two")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-low", 0.4, lowFaq),   // similarity 0.6
                new StubEntry("faq-high", 0.1, highFaq)  // similarity 0.9 — the highest
        ));
        when(llmService.generateMultiAnswer(any(), any(), any())).thenReturn("Answer.");

        chatbotService.query("user-1", "question one? question two?");

        verify(interactionLogService).logInteraction(eq("user-1"), anyString(), eq("faq-high"), anyDouble(), eq("ANSWERED"));
    }

    @Test
    void query_subQuestionEmbedThrows_isolatedAsUnansweredNotWholeMessageError() {
        Faq faq = Faq.builder().id("faq-1").question("Q").answer("A").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("good question", "bad question"));
        when(embeddingService.embed("good question")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("bad question")).thenThrow(new RuntimeException("embedding service down"));
        stubFaqMatch("faq-1", 0.1, faq);
        when(llmService.generateMultiAnswer(any(), any(), any())).thenReturn("Answer plus apology.");

        ChatbotQueryResponse response = chatbotService.query("user-1", "good question? bad question?");

        assertThat(response.outcome()).isEqualTo("ANSWERED");
        ArgumentCaptor<List<String>> unansweredCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmService).generateMultiAnswer(any(), unansweredCaptor.capture(), any());
        assertThat(unansweredCaptor.getValue()).containsExactly("bad question");
        verify(interactionLogService, never()).logInteraction(any(), any(), any(), anyDouble(), eq("ERROR"));
    }

    @Test
    void queryStream_multipleQuestions_streamsDeltasThenDoneWithAveragedConfidence() {
        Faq pwFaq = Faq.builder().id("faq-pw").question("How do I reset my password?").answer("Use the reset link.").build();
        Faq loginFaq = Faq.builder().id("faq-login").question("How do I log in?").answer("Use your email and password.").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any()))
                .thenReturn(List.of("How do I reset my password?", "How do I log in?"));
        when(embeddingService.embed("How do I reset my password?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("How do I log in?")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-pw", 0.1, pwFaq),     // similarity 0.9
                new StubEntry("faq-login", 0.2, loginFaq) // similarity 0.8
        ));
        doAnswer(invocation -> {
            java.util.function.Consumer<String> onChunk = invocation.getArgument(3);
            onChunk.accept("Part one. ");
            onChunk.accept("Part two.");
            return null;
        }).when(llmService).streamMultiAnswer(any(), any(), any(), any());

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "how do I reset my password and login with it?");

        StepVerifier.create(stream)
                .expectNext(ChatbotAnswerChunk.delta("Part one. "))
                .expectNext(ChatbotAnswerChunk.delta("Part two."))
                .expectNextMatches(chunk -> chunk.done() && "ANSWERED".equals(chunk.outcome()) && chunk.confidence() == 0.85)
                .verifyComplete();

        verify(contextService).addTurn("user-1", "how do I reset my password and login with it?", "Part one. Part two.");
    }

    @Test
    void queryStream_multipleQuestions_noneMatched_escalatesWithHintChunk() {
        Faq unrelated = Faq.builder().id("faq-unrelated").question("Q").answer("A").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any()))
                .thenReturn(List.of("What is the weather?", "What time is it?"));
        when(embeddingService.embed("What is the weather?")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("What time is it?")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-unrelated", 0.8, unrelated),
                new StubEntry("faq-unrelated", 0.7, unrelated) // similarity 0.3 — best near-miss
        ));

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "what is the weather? what time is it?");

        StepVerifier.create(stream)
                .expectNextMatches(chunk -> !chunk.done() && chunk.delta() != null)
                .expectNextMatches(chunk -> chunk.done() && "ESCALATED".equals(chunk.outcome()) && chunk.confidence() == 0.3)
                .verifyComplete();

        verify(llmService, never()).streamMultiAnswer(any(), any(), any(), any());
    }

    @Test
    void queryStream_multipleQuestions_llmStreamFailure_emitsServiceUnavailableAndLogsError() {
        Faq pwFaq = Faq.builder().id("faq-pw").question("Q1").answer("A1").build();
        Faq loginFaq = Faq.builder().id("faq-login").question("Q2").answer("A2").build();
        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("question one", "question two"));
        when(embeddingService.embed("question one")).thenReturn(new float[]{0.1f});
        when(embeddingService.embed("question two")).thenReturn(new float[]{0.2f});
        stubFaqMatches(List.of(
                new StubEntry("faq-pw", 0.1, pwFaq),
                new StubEntry("faq-login", 0.2, loginFaq)
        ));
        doThrow(new RuntimeException("boom"))
                .when(llmService).streamMultiAnswer(any(), any(), any(), any());

        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "question one? question two?");

        StepVerifier.create(stream)
                .expectError(ServiceUnavailableException.class)
                .verify();

        verify(interactionLogService).logInteraction("user-1", "question one? question two?", null, 0.0, "ERROR");
        verify(contextService, never()).addTurn(any(), any(), any());
    }

    @Test
    void queryStream_doesNotRunPreludeUntilSubscribed() {
        Flux<ChatbotAnswerChunk> stream = chatbotService.queryStream("user-1", "hello");

        verifyNoInteractions(contextService, llmService, embeddingService, jdbcTemplate, interactionLogService);

        when(contextService.getContext("user-1"))
                .thenReturn(new ConversationContextService.ConversationContext(List.of(), ""));
        when(llmService.splitQuestions(eq("hello"), any())).thenReturn(List.of("hello"));
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
        when(llmService.splitQuestions(eq("how do I reset it"), any())).thenReturn(List.of("How do I reset my password?"));
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
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("what is this"));
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
        when(llmService.splitQuestions(anyString(), any())).thenReturn(List.of("query"));
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
