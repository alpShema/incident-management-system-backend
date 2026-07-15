package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "mail")
@Validated
public record MailProperties(
        boolean enabled,
        String fromAddress,
        String fromName,
        RateLimit rateLimit
) {
    public record RateLimit(
            int emailsPerSecond
    ) {
        public RateLimit {
            if (emailsPerSecond <= 0) emailsPerSecond = 2;
        }
    }

    public MailProperties {
        if (rateLimit == null) {
            rateLimit = new RateLimit(2);
        }
    }
}
