package com.amalitech.hilfe.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StubLlmServiceTest {

    private final StubLlmService stubLlmService = new StubLlmService();

    @Test
    void splitQuestions_returnsInputUnchangedAsSingleElementList() {
        List<String> result = stubLlmService.splitQuestions("how do I reset my password and login with it?", List.of("some history"));

        assertThat(result).containsExactly("how do I reset my password and login with it?");
    }

    @Test
    void generateMultiAnswer_concatenatesFaqAnswersAndNotesUnanswered() {
        List<LlmService.FaqMatchForAnswer> matches = List.of(
                new LlmService.FaqMatchForAnswer("How do I reset my password?", "How do I reset my password?", "Use the reset link."),
                new LlmService.FaqMatchForAnswer("How do I log in?", "How do I log in?", "Use your email and password.")
        );
        List<String> unanswered = List.of("What is the weather today?");

        String answer = stubLlmService.generateMultiAnswer(matches, unanswered, "summary");

        assertThat(answer)
                .contains("Use the reset link.")
                .contains("Use your email and password.")
                .contains("I couldn't find an answer for: What is the weather today?");
    }
}
