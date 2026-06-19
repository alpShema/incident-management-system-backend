package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "slack")
@Validated
public record SlackProperties(
        boolean enabled,
        String signingSecret,
        String botToken,
        String clientId,
        String clientSecret,
        String redirectUri,
        String frontendConnectUrl,
        String frontendSuccessUrl,
        String appId,
        boolean notificationsEnabled,
        boolean agentFeaturesEnabled,
        RateLimit rateLimit
) {
    public record RateLimit(
            int requestsPerMinute,
            int burstSize
    ) {
        public RateLimit {
            if (requestsPerMinute <= 0) requestsPerMinute = 60;
            if (burstSize <= 0) burstSize = 10;
        }
    }

    public SlackProperties {
        if (rateLimit == null) {
            rateLimit = new RateLimit(60, 10);
        }
    }
}
