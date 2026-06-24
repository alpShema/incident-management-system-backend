package com.amalitech.hilfe.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnMissingBean(EmbeddingService.class)
public class StubEmbeddingService implements EmbeddingService {

    public StubEmbeddingService() {
        log.warn("StubEmbeddingService is active — no embedding.api-key configured. "
                + "Semantic search will not work until a real key is provided.");
    }

    @Override
    public float[] embed(String text) {
        log.debug("StubEmbeddingService: returning empty array (no API key configured)");
        return new float[0];
    }
}
