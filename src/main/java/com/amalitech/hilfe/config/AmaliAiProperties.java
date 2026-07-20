package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@ConfigurationProperties(prefix = "amali-ai")
public record AmaliAiProperties(
        String apiKey,
        String baseUrl,
        String provider,
        Duration connectTimeout,
        Duration readTimeout
) {
    public AmaliAiProperties {
        if (provider == null || provider.isBlank()) provider = "openai";
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(6);
    }

    public String llmUrl() {
        return baseUrl + "/api/v2/public/";
    }

    public String embeddingUrl() {
        return baseUrl + "/api/v2/public/v1/embeddings";
    }

    /**
     * A request factory bounded well under the reverse proxy's read timeout, so a hung or
     * unreachable AI provider fails fast with a normal error response (CORS headers included)
     * instead of the proxy timing out first and returning a bare, CORS-less 504.
     * <p>
     * Worst case per call is not just {@code readTimeout}: once the first read times out,
     * {@code HttpURLConnection} makes one more attempt to read the error stream, bound by the
     * same readTimeout again — so budget roughly {@code connectTimeout + 2 * readTimeout} per
     * call. A single /chatbot/query request can make up to 3 such sequential calls; the
     * defaults keep that worst case comfortably under the reverse proxy's 60s read timeout.
     */
    public ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
