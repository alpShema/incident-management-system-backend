package com.amalitech.hilfe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "s3")
public record S3Properties(
        String bucketName,
        String region,
        String accessKeyId,
        String secretAccessKey,
        Duration presignExpiry
) {
}
