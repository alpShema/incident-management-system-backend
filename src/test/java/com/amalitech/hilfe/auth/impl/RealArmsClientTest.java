package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsProperties;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RealArmsClientTest {

    private static final String SSO_URL = "https://armsstage-authentication.amalitech-dev.net/sso";
    private static final String VALID_TOKEN = "valid-arms-token";
    private static final String INVALID_TOKEN = "invalid-arms-token";

    private MockRestServiceServer mockServer;
    private RealArmsClient armsClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .defaultHeader("Content-Type", "application/json");

        mockServer = MockRestServiceServer.bindTo(builder).build();

        RestClient restClient = builder.build();

        ArmsProperties properties = new ArmsProperties(
                SSO_URL,
                "https://armsstage-authentication.amalitech-dev.net/",
                "https://armsstage-employee-management.amalitech-dev.net/graphql",
                "placeholder-api-key"
        );

        armsClient = new RealArmsClient(restClient, properties);
    }

    @Test
    void getUserByToken_validToken_returnsArmsUserInfo() {
        String responseBody = """
                {
                  "user_id": "10042",
                  "first_name": "Alphonse",
                  "last_name": "Shema",
                  "email": "alphonse@amalitech.org",
                  "profile_image": "https://example.com/photo.jpg"
                }
                """;

        mockServer.expect(requestTo(SSO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + VALID_TOKEN))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ArmsUserInfo result = armsClient.getUserByToken(VALID_TOKEN);

        assertThat(result.userId()).isEqualTo("10042");
        assertThat(result.firstName()).isEqualTo("Alphonse");
        assertThat(result.lastName()).isEqualTo("Shema");
        assertThat(result.email()).isEqualTo("alphonse@amalitech.org");
        assertThat(result.profileImage()).isEqualTo("https://example.com/photo.jpg");

        mockServer.verify();
    }

    @Test
    void getUserByToken_invalidToken_throwsArmsAuthException() {
        mockServer.expect(requestTo(SSO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + INVALID_TOKEN))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> armsClient.getUserByToken(INVALID_TOKEN))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessageContaining("Invalid or expired ARMS token");

        mockServer.verify();
    }

    @Test
    void getUserByToken_armsUnreachable_throwsArmsAuthException() {
        mockServer.expect(requestTo(SSO_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> armsClient.getUserByToken(VALID_TOKEN))
                .isInstanceOf(ArmsAuthException.class);

        mockServer.verify();
    }
}
