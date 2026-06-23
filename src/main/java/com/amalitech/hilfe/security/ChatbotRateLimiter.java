package com.amalitech.hilfe.security;

import com.amalitech.hilfe.config.ChatbotProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ChatbotRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int requestsPerMinute;

    public ChatbotRateLimiter(ChatbotProperties props) {
        this.requestsPerMinute = props.rateLimit();
        log.info("Chatbot rate limiter initialized: {} requests/minute", requestsPerMinute);
    }

    public boolean tryAcquire(String userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::createBucket);
        boolean acquired = bucket.tryConsume(1);
        if (!acquired) {
            log.warn("Chatbot rate limit exceeded for user: {}", userId);
        }
        return acquired;
    }

    private Bucket createBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
