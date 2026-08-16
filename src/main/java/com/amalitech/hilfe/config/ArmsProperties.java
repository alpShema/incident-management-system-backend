package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arms")
public record ArmsProperties(
        String ssoUrl,
        String authUrl,
        String employeeInfoUrl,
        String apiKey,
        String ssoPublicKey
) {
}
