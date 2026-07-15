package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.MailProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Paces outgoing emails to stay under the mail provider's rate limit (Mailtrap's free Testing plan
 * enforces a strict emails/second cap; a burst of SLA notifications across many incidents at once
 * was enough to trip it). Blocks the calling (already-async) thread until a token is available,
 * rather than dropping the send — every notification should still go out, just paced.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class EmailRateLimiter {

    private final Bucket bucket;

    public EmailRateLimiter(MailProperties mailProperties) {
        int emailsPerSecond = mailProperties.rateLimit().emailsPerSecond();
        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(emailsPerSecond)
                        .refillGreedy(emailsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();
        log.info("Email rate limiter initialized: {} emails/second", emailsPerSecond);
    }

    public void acquireBlocking() {
        try {
            bucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
