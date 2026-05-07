package com.amalitech.hilfe.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ArmsClientConfig {

    @Bean
    public RestClient armsRestClient(@Value("${arms.sso-url}") String armsSsoUrl) {
        return RestClient.builder()
                .baseUrl(armsSsoUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
