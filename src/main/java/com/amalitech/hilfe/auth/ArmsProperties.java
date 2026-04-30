package com.amalitech.hilfe.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "arms")
@Component
public record ArmsProperties(
        String ssoUrl,
        String authUrl,
        String employeeInfoUrl,
        String apiKey
) {}