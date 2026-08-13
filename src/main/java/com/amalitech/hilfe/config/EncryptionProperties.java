package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "encryption")
@Validated
public record EncryptionProperties(
        String key,
        String fieldKey
) {
}
