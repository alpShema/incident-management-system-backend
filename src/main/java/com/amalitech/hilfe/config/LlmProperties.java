package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String model, double temperature, int maxTokens) {
    public LlmProperties {
        if (temperature <= 0) temperature = 0.2;
        if (maxTokens <= 0) maxTokens = 512;
    }
}
