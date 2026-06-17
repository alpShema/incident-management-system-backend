package com.amalitech.hilfe.slack.security;

import com.amalitech.hilfe.config.SlackProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SlackRateLimiter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final SlackProperties.RateLimit rateLimitConfig;

    public SlackRateLimiter(SlackProperties slackProperties) {
        this.rateLimitConfig = slackProperties.rateLimit();
        log.info("Slack rate limiter initialized: {} requests/minute, burst size {}",
                rateLimitConfig.requestsPerMinute(), rateLimitConfig.burstSize());
    }

    public boolean tryAcquire(String slackUserId) {
        Bucket bucket = buckets.computeIfAbsent(slackUserId, this::createBucket);
        boolean acquired = bucket.tryConsume(1);
        if (!acquired) {
            log.warn("Rate limit exceeded for Slack user: {}", slackUserId);
        }
        return acquired;
    }

    public long getAvailableTokens(String slackUserId) {
        Bucket bucket = buckets.get(slackUserId);
        return bucket != null ? bucket.getAvailableTokens() : rateLimitConfig.requestsPerMinute();
    }

    private Bucket createBucket(String key) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimitConfig.requestsPerMinute())
                        .refillGreedy(rateLimitConfig.requestsPerMinute(), Duration.ofMinutes(1))
                        .build())
                .addLimit(Bandwidth.builder()
                        .capacity(rateLimitConfig.burstSize())
                        .refillGreedy(rateLimitConfig.burstSize(), Duration.ofSeconds(1))
                        .build())
                .build();
    }

    public void evictUser(String slackUserId) {
        buckets.remove(slackUserId);
    }

    public void clear() {
        buckets.clear();
    }
}
