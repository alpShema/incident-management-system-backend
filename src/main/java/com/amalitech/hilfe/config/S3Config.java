package com.amalitech.hilfe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(S3Properties properties) {
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(S3Properties properties) {
        if (!hasText(properties.accessKeyId()) && !hasText(properties.secretAccessKey())) {
            return DefaultCredentialsProvider.create();
        }

        if (!hasText(properties.accessKeyId()) || !hasText(properties.secretAccessKey())) {
            throw new IllegalStateException("Both S3 access key ID and secret access key must be configured together");
        }

        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKeyId(), properties.secretAccessKey()));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
