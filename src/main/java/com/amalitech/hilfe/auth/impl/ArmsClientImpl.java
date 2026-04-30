package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsAuthException;
import com.amalitech.hilfe.auth.ArmsClient;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Real implementation of ArmsClient.
 * Calls the ARMS SSO URL with the token as a Bearer credential and maps
 * the snake_case JSON response to the ArmsUserInfo record.
 * Presence of this bean suppresses StubArmsClient via @ConditionalOnMissingBean.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArmsClientImpl implements ArmsClient {

    private final RestClient armsRestClient;

    @Override
    public ArmsUserInfo getUserByToken(String armsToken) {
        ArmsResponse raw = armsRestClient.get()
                .header("Authorization", "Bearer " + armsToken)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    log.warn("ARMS returned {} for token validation", res.getStatusCode());
                    throw new ArmsAuthException("Invalid or expired ARMS token", 401);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    log.error("ARMS returned {} during token validation", res.getStatusCode());
                    throw new ArmsAuthException("ARMS service unavailable", 502);
                })
                .body(ArmsResponse.class);

        if (raw == null) {
            throw new ArmsAuthException("Empty response from ARMS", 502);
        }

        return new ArmsUserInfo(
                raw.userId(),
                raw.firstName(),
                raw.lastName(),
                raw.email(),
                raw.profileImage()
        );
    }

    /** Internal DTO that handles the snake_case JSON field names from ARMS. */
    private record ArmsResponse(
            @JsonProperty("user_id")       String userId,
            @JsonProperty("first_name")    String firstName,
            @JsonProperty("last_name")     String lastName,
            @JsonProperty("email")         String email,
            @JsonProperty("profile_image") String profileImage
    ) {}
}
