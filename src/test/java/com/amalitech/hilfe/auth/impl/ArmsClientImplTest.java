package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.ArmsAuthException;
import com.amalitech.hilfe.auth.dto.ArmsUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArmsClientImplTest {

    private static final String BASE_URL = "http://arms-test";

    MockRestServiceServer server;
    ArmsClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ArmsClientImpl(builder.build());
    }

    @Test
    void getUserByToken_validResponse_mapsFieldsCorrectly() {
        String json = """
                {"user_id":"u1","first_name":"John","last_name":"Doe",
                 "email":"john@test.com","profile_image":"http://img.png"}
                """;
        server.expect(requestTo(BASE_URL))
              .andExpect(header("Authorization", "Bearer valid-token"))
              .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ArmsUserInfo info = client.getUserByToken("valid-token");

        assertThat(info.userId()).isEqualTo("u1");
        assertThat(info.firstName()).isEqualTo("John");
        assertThat(info.lastName()).isEqualTo("Doe");
        assertThat(info.email()).isEqualTo("john@test.com");
        assertThat(info.profileImage()).isEqualTo("http://img.png");
        server.verify();
    }

    @Test
    void getUserByToken_4xxResponse_throwsArmsAuthException401() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getUserByToken("bad-token"))
                .isInstanceOf(ArmsAuthException.class)
                .satisfies(ex -> assertThat(((ArmsAuthException) ex).getHttpStatus()).isEqualTo(401));
    }

    @Test
    void getUserByToken_5xxResponse_throwsArmsAuthException502() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getUserByToken("token"))
                .isInstanceOf(ArmsAuthException.class)
                .satisfies(ex -> assertThat(((ArmsAuthException) ex).getHttpStatus()).isEqualTo(502));
    }

    @Test
    void getUserByToken_emptyBody_throwsArmsAuthException502() {
        server.expect(requestTo(BASE_URL))
              .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUserByToken("token"))
                .isInstanceOf(ArmsAuthException.class)
                .satisfies(ex -> assertThat(((ArmsAuthException) ex).getHttpStatus()).isEqualTo(502));
    }
}
