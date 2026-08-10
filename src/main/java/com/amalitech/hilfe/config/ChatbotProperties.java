package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chatbot")
public record ChatbotProperties(
        double confidenceThreshold,
        int rateLimit,
        int conversationWindowSize,
        int summaryTriggerInterval,
        int maxSubQuestions
) {
    public ChatbotProperties {
        if (confidenceThreshold <= 0) confidenceThreshold = 0.25;
        if (rateLimit <= 0) rateLimit = 30;
        if (conversationWindowSize <= 0) conversationWindowSize = 10;
        if (summaryTriggerInterval <= 0) summaryTriggerInterval = 5;
        if (maxSubQuestions <= 0) maxSubQuestions = 5;
    }
}
