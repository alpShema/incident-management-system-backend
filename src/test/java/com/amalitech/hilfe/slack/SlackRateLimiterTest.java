package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.security.SlackRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlackRateLimiterTest {

    // burst=2 means at most 2 requests per second regardless of per-minute limit
    private SlackRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        SlackProperties props = new SlackProperties(
                true, "secret", "token", "id", "secret",
                "https://redirect", "https://connect", "app-id",
                true, true, new SlackProperties.RateLimit(5, 2));
        rateLimiter = new SlackRateLimiter(props);
    }

    @Test
    void tryAcquire_firstRequest_succeeds() {
        assertThat(rateLimiter.tryAcquire("user-1")).isTrue();
    }

    @Test
    void tryAcquire_withinBurstLimit_succeeds() {
        assertThat(rateLimiter.tryAcquire("user-burst")).isTrue();
        assertThat(rateLimiter.tryAcquire("user-burst")).isTrue();
    }

    @Test
    void tryAcquire_exceedsBurstLimit_returnsFalse() {
        rateLimiter.tryAcquire("burst-user");
        rateLimiter.tryAcquire("burst-user");
        // 3rd request in same instant exceeds burst of 2
        assertThat(rateLimiter.tryAcquire("burst-user")).isFalse();
    }

    @Test
    void tryAcquire_differentUsers_haveIndependentBuckets() {
        // Exhaust user-1
        rateLimiter.tryAcquire("user-1");
        rateLimiter.tryAcquire("user-1");
        rateLimiter.tryAcquire("user-1");

        // user-2 should still have tokens
        assertThat(rateLimiter.tryAcquire("user-2")).isTrue();
    }

    @Test
    void getAvailableTokens_unknownUser_returnsRequestsPerMinuteDefault() {
        assertThat(rateLimiter.getAvailableTokens("new-user")).isEqualTo(5);
    }

    @Test
    void getAvailableTokens_afterConsumingOne_isLessThanMax() {
        rateLimiter.tryAcquire("tracked-user");
        long tokens = rateLimiter.getAvailableTokens("tracked-user");
        assertThat(tokens).isLessThan(5);
    }

    @Test
    void evictUser_clearsPerUserBucket() {
        rateLimiter.tryAcquire("evict-me");
        rateLimiter.tryAcquire("evict-me");
        rateLimiter.evictUser("evict-me");
        // Bucket removed — getAvailableTokens returns default for unknown user
        assertThat(rateLimiter.getAvailableTokens("evict-me")).isEqualTo(5);
    }

    @Test
    void clear_removesAllBuckets() {
        rateLimiter.tryAcquire("u1");
        rateLimiter.tryAcquire("u2");
        rateLimiter.clear();
        assertThat(rateLimiter.getAvailableTokens("u1")).isEqualTo(5);
        assertThat(rateLimiter.getAvailableTokens("u2")).isEqualTo(5);
    }
}
