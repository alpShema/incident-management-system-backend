package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "s3")
@Validated
public record S3Properties(
        @NotBlank
        String bucketName,

        @NotBlank
        String region,

        String accessKeyId,

        String secretAccessKey,

        @NotNull
        Duration presignExpiry
) {
}
