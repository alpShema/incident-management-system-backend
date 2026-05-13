package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        long maxFileSize,
        int maxAttachments,
        List<String> allowedContentTypes
) {
}
