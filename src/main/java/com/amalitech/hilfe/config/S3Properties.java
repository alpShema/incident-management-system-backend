package com.amalitech.hilfe.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

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
