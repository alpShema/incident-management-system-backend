package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@ConfigurationProperties(prefix = "media")
@Validated
public record MediaProperties(
        @Positive
        long maxFileSize,

        @Positive
        int maxAttachments,

        @NotEmpty
        List<@NotBlank String> allowedContentTypes
) {
}
