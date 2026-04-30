package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.auth.dto.LoginRequest;
import com.amalitech.hilfe.auth.dto.RefreshTokenRequest;
import com.amalitech.hilfe.auth.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({AuthControllerTest.OpenSecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthService authService;

    @TestConfiguration
    static class OpenSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
            return http.build();
        }
    }

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
    void refresh_notImplemented_returns501() throws Exception {
        when(authService.refresh(any())).thenThrow(new UnsupportedOperationException("Not yet implemented"));

        mvc.perform(post("/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest("rt"))))
                .andExpect(status().isNotImplemented());
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
