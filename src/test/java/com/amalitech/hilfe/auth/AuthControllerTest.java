package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.controllers.AuthController;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.RefreshTokenRequest;
import com.amalitech.hilfe.dto.TokenResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class AuthControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean AuthService authService;
    @MockitoBean TokenService tokenService;

    @Test
    void login_validRequest_returns200WithTokensAndThreeCookies() throws Exception {
        TokenResponse tokens = TokenResponse.builder()
                .accessToken("access-jwt").refreshToken("refresh-jwt")
                .accessTokenExpiresIn(3600L).refreshTokenExpiresIn(86400L)
                .build();
        when(authService.login(any())).thenReturn(tokens);

        var result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("arms-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-jwt"))
                .andReturn();

        var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies).anyMatch(c -> c.startsWith("access_token="));
        assertThat(cookies).anyMatch(c -> c.startsWith("refresh_token="));
        assertThat(cookies).anyMatch(c -> c.startsWith("arms_token="));
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("HttpOnly").contains("SameSite=Strict"));
    }

    @Test
    void login_armsClientReturns401_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new ArmsAuthException("Invalid token", 401));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("bad-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_armsServiceUnavailable_returns502() throws Exception {
        when(authService.login(any())).thenThrow(new ArmsAuthException("ARMS down", 502));

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("token"))))
                .andExpect(status().isBadGateway());
    }

    @Test
    void refresh_validRequest_returns200WithUpdatedCookies() throws Exception {
        TokenResponse tokens = TokenResponse.builder()
                .accessToken("new-access")
                .refreshToken("new-refresh")
                .accessTokenExpiresIn(3600L)
                .refreshTokenExpiresIn(1800L)
                .build();
        when(authService.refresh(any(), any())).thenReturn(tokens);

        var result = mvc.perform(post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new MockCookie("arms_token", "arms-cookie-token"))
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("rt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andReturn();

        var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies).anyMatch(c -> c.startsWith("access_token="));
        assertThat(cookies).anyMatch(c -> c.startsWith("refresh_token="));
        assertThat(cookies).anyMatch(c -> c.startsWith("arms_token=arms-cookie-token"));
    }

    @Test
    void logout_validRequest_returns204AndClearsCookies() throws Exception {
        var result = mvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("rt"))))
                .andExpect(status().isNoContent())
                .andReturn();

        var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    }
}
