package com.amalitech.hilfe.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "media")
@Validated
public record MediaProperties(
        @Positive
        long maxFileSize,

        @Positive
        long maxVideoFileSize,

        @Positive
        long maxTotalAttachmentSize,

        @Positive
        int maxAttachments,

        @NotEmpty
        List<@NotBlank String> allowedContentTypes
) {
}
