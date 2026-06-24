package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amali-ai")
public record AmaliAiProperties(
        String apiKey,
        String baseUrl,
        String provider
) {
    public AmaliAiProperties {
        if (provider == null || provider.isBlank()) provider = "openai";
    }

    public String llmUrl() {
        return baseUrl + "/api/v2/public/";
    }

    public String embeddingUrl() {
        return baseUrl + "/api/v2/public/v1/embeddings";
    }
}
