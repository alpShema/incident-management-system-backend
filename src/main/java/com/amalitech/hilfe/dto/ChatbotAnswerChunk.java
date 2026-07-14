package com.amalitech.hilfe.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single incremental chunk of a streamed chatbot answer")
public record ChatbotAnswerChunk(
        @Schema(description = "Text delta to append to the answer so far")
        String delta,

        @Schema(description = "True on the final chunk of the stream")
        boolean done,

        @Schema(description = "Confidence score of the matched FAQ, present only on the final chunk")
        Double confidence,

        @Schema(description = "Outcome of the query: ANSWERED or ESCALATED, present only on the final chunk")
        String outcome
) {
    public static ChatbotAnswerChunk delta(String text) {
        return new ChatbotAnswerChunk(text, false, null, null);
    }

    public static ChatbotAnswerChunk done(double confidence, String outcome) {
        return new ChatbotAnswerChunk(null, true, confidence, outcome);
    }
}
