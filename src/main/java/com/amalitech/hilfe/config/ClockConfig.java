package com.amalitech.hilfe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * HV-1674: a single injectable {@link Clock} for "now"-dependent business logic (reopen-window
 * enforcement, auto-close scanning) so those code paths are deterministically testable instead of
 * calling {@code Instant.now()} directly.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
