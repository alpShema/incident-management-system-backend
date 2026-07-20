package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.AmaliAiProperties;
import com.amalitech.hilfe.config.EmbeddingProperties;
import com.amalitech.hilfe.exceptions.ServiceUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@ConditionalOnProperty(name = "amali-ai.api-key", matchIfMissing = false)
public class OpenAiEmbeddingService implements EmbeddingService {

    private final RestClient restClient;
    private final EmbeddingProperties props;

    public OpenAiEmbeddingService(EmbeddingProperties embeddingProps, AmaliAiProperties amaliAiProps) {
        this.props = embeddingProps;
        this.restClient = RestClient.builder()
                .baseUrl(amaliAiProps.embeddingUrl())
                .requestFactory(amaliAiProps.requestFactory())
                .defaultHeader("X-Api-Key", amaliAiProps.apiKey())
                .defaultHeader("Provider", amaliAiProps.provider())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("OpenAiEmbeddingService initialized — model={}, url={}, connectTimeout={}, readTimeout={}",
                embeddingProps.model(), amaliAiProps.embeddingUrl(), amaliAiProps.connectTimeout(), amaliAiProps.readTimeout());
    }

    @Override
    public float[] embed(String text) {
        if (props.model() == null || props.model().isBlank()) {
            throw new ServiceUnavailableException("Embedding model is not configured — set the EMBEDDING_MODEL environment variable");
        }
        long start = System.currentTimeMillis();
        try {
            EmbeddingResponse response = restClient.post()
                    .body(Map.of("input", text, "model", props.model()))
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                throw new ServiceUnavailableException("Embedding API returned an empty response");
            }

            List<Double> raw = response.data().getFirst().embedding();
            float[] vector = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                vector[i] = raw.get(i).floatValue();
            }
            log.debug("Embed done | {}ms | {} chars → {} dims | model={}", System.currentTimeMillis() - start, text.length(), vector.length, props.model());
            return vector;

        } catch (RestClientException e) {
            log.error("Embed failed | {}ms | text=\"{}\" | {}", System.currentTimeMillis() - start, text.length() > 80 ? text.substring(0, 80) + "..." : text, e.getMessage());
            throw new ServiceUnavailableException("Embedding service is temporarily unavailable", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingResponse(List<EmbeddingData> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record EmbeddingData(List<Double> embedding, int index) {}
}
