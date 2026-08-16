package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.config.ArmsProperties;
import com.amalitech.hilfe.dto.ArmsEmployeeInfo;
import com.amalitech.hilfe.dto.ArmsUserInfo;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.services.ArmsClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArmsClientImplTest {

    private static final String SSO_URL = "http://arms-test";
    private static final String EMP_URL = "http://emp";
    private static final String TOKEN = buildTestToken("u1");

    MockRestServiceServer server;
    ArmsClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ArmsProperties properties = new ArmsProperties(
                SSO_URL, "http://auth", "http://emp", "api-key", "test-public-key");
        client = new ArmsClientImpl(builder.build(), properties);
    }

    private static String buildTestToken(String userId) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"user_id\":\"" + userId + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".sig";
    }

    @Test
    void getUserByToken_validResponse_mapsFieldsCorrectly() {
        String json = """
                {"data":{
                  "getEmployeeActiveInfo":{
                    "user_id":"u1",
                    "user":{"first_name":"John","last_name":"Doe","other_name":"K","email":"john@work.test"},
                    "employee_bio":{"profile_image":"http://img.png"},
                    "position":{"position_name":"Engineer"},
                    "office":{"id":"25","name":"Accra Office","archive":false,"organization_id":"1042","organization":{"offices":null}}
                  },
                  "getEmployeeContact":{
                    "id":"1185",
                    "user_id":"u1",
                    "work_email":"john@work.test",
                    "personal_email":"john@test.com",
                    "phone_number_1":"233000000"
                  }
                }}
                """;
        server.expect(requestTo(SSO_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer " + TOKEN))
              .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ArmsUserInfo info = client.getUserByToken(TOKEN);

        assertThat(info.userId()).isEqualTo("u1");
        assertThat(info.firstName()).isEqualTo("John");
        assertThat(info.lastName()).isEqualTo("Doe");
        assertThat(info.otherName()).isEqualTo("K");
        assertThat(info.email()).isEqualTo("john@work.test");
        assertThat(info.profileImage()).isEqualTo("http://img.png");
        assertThat(info.positionName()).isEqualTo("Engineer");
        assertThat(info.officeName()).isEqualTo("Accra Office");
        assertThat(info.workEmail()).isEqualTo("john@work.test");
        assertThat(info.personalEmail()).isEqualTo("john@test.com");
        assertThat(info.phoneNumber()).isEqualTo("233000000");
        server.verify();
    }

    @Test
    void getUserByToken_4xxResponse_throwsArmsAuthExceptionWith401() {
        server.expect(requestTo(SSO_URL))
              .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.getUserByToken(TOKEN))
                .isInstanceOf(ArmsAuthException.class)
                .satisfies(ex -> assertThat(((ArmsAuthException) ex).getHttpStatus()).isEqualTo(401));
    }

    @Test
    void getUserByToken_5xxResponse_throwsArmsAuthException() {
        server.expect(requestTo(SSO_URL))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getUserByToken(TOKEN))
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getUserByToken_nullEmployeeBio_throwsArmsAuthException() {
        server.expect(requestTo(SSO_URL))
              .andRespond(withSuccess(
                      "{\"data\":{\"getEmployeeActiveInfo\":null,\"getEmployeeContact\":null}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUserByToken(TOKEN))
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getUserByToken_invalidToken_throwsArmsAuthExceptionWith401() {
        assertThatThrownBy(() -> client.getUserByToken("not-a-jwt"))
                .isInstanceOf(ArmsAuthException.class)
                .satisfies(ex -> assertThat(((ArmsAuthException) ex).getHttpStatus()).isEqualTo(401));
    }

    // ── getAllUsers ────────────────────────────────────────────────────────────

    @Test
    void getAllUsers_validResponse_returnsList() {
        String json = """
                {"data":{"listEmployeeInfosWithFilters":{"EmployeeInfo":[
                  {"user_id":"u1","full_name":"John Doe","active":true,"profile_image":"http://img.png"}
                ]}}}
                """;
        server.expect(requestTo(EMP_URL))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("x-api-key", "api-key"))
              .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        List<ArmsEmployeeInfo> result = client.getAllUsers();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo("u1");
        server.verify();
    }

    @Test
    void getAllUsers_nullData_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getAllUsers())
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getAllUsers_4xxResponse_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.getAllUsers())
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getAllUsers_5xxResponse_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.getAllUsers())
                .isInstanceOf(ArmsAuthException.class);
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_validResponse_mapsFieldsCorrectly() {
        String json = """
                {"data":{"getEmployeeBioForExternalService":
                  {"user_id":"u2","full_name":"Jane Smith","email":"jane@test.com","profile_image":"http://j.png"}
                }}
                """;
        server.expect(requestTo(EMP_URL))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ArmsUserInfo info = client.getUserById("u2");

        assertThat(info.userId()).isEqualTo("u2");
        assertThat(info.firstName()).isEqualTo("Jane");
        assertThat(info.lastName()).isEqualTo("Smith");
        assertThat(info.email()).isEqualTo("jane@test.com");
        server.verify();
    }

    @Test
    void getUserById_singleWordName_setsLastNameEmpty() {
        String json = """
                {"data":{"getEmployeeBioForExternalService":
                  {"user_id":"u3","full_name":"Cher","email":"cher@test.com","profile_image":null}
                }}
                """;
        server.expect(requestTo(EMP_URL))
              .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));

        ArmsUserInfo info = client.getUserById("u3");

        assertThat(info.firstName()).isEqualTo("Cher");
        assertThat(info.lastName()).isEmpty();
    }

    @Test
    void getUserById_nullData_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withSuccess("{\"data\":null}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getUserById("u1"))
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getUserById_4xxResponse_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getUserById("u1"))
                .isInstanceOf(ArmsAuthException.class);
    }

    @Test
    void getUserById_5xxResponse_throwsArmsAuthException() {
        server.expect(requestTo(EMP_URL))
              .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.getUserById("u1"))
                .isInstanceOf(ArmsAuthException.class);
    }
}
