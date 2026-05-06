package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.controllers.AuthController;
import com.amalitech.hilfe.dto.AuthResult;
import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.dto.AuthSessionResponse;
import com.amalitech.hilfe.dto.AuthTokens;
import com.amalitech.hilfe.dto.LoginRequest;
import com.amalitech.hilfe.dto.UpdateUserRoleRequest;
import com.amalitech.hilfe.dto.UserPermissionsResponse;
import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.AuthService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    void login_validRequest_returns200WithSessionAndThreeCookies() throws Exception {
        AuthResult authResult = new AuthResult(
                AuthTokens.builder()
                .accessToken("access-jwt").refreshToken("refresh-jwt")
                .accessTokenExpiresIn(3600L).refreshTokenExpiresIn(86400L)
                .build(),
                AuthSessionResponse.builder()
                        .userId("u1")
                        .email("john@test.com")
                        .fullName("John Doe")
                        .profileImg("http://img.png")
                        .build()
        );
        when(authService.login(any())).thenReturn(authResult);

        var result = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("arms-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.email").value("john@test.com"))
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
        AuthResult authResult = new AuthResult(
                AuthTokens.builder()
                        .accessToken("new-access")
                        .refreshToken("new-refresh")
                        .accessTokenExpiresIn(3600L)
                        .refreshTokenExpiresIn(1800L)
                        .build(),
                AuthSessionResponse.builder()
                        .userId("u1")
                        .email("john@test.com")
                        .fullName("John Doe")
                        .build()
        );
        when(authService.refresh(any(), any())).thenReturn(authResult);

        var result = mvc.perform(post("/auth/refresh-token")
                        .cookie(new MockCookie("refresh_token", "rt"))
                        .cookie(new MockCookie("arms_token", "arms-cookie-token")))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.userId").value("u1"))
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
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent())
                .andReturn();

        var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(3);
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("Max-Age=0"));
    }

    @Test
    void permissions_authenticatedRequest_returnsPermissions() throws Exception {
        when(authService.getUserPermissions(nullable(String.class))).thenReturn(
                UserPermissionsResponse.builder()
                        .userId("u1")
                        .permissions(List.of("incident.create", "incident.read.own"))
                        .build()
        );

        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal(
                "u1",
                "john@test.com",
                com.amalitech.hilfe.models.RoleCode.CLIENT
        );

        mvc.perform(get("/auth/permissions")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(() -> "ROLE_CLIENT")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.permissions[0]").value("incident.create"))
                .andExpect(jsonPath("$.permissions[1]").value("incident.read.own"));
    }

    @Test
    void userRoles_adminRequest_returnsPaginatedRoles() throws Exception {
        when(authService.getUserRoles(any())).thenReturn(new PageImpl<>(
                List.of(new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN)),
                PageRequest.of(0, 10),
                1
        ));

        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal(
                "admin-1",
                "admin@test.com",
                RoleCode.ADMIN
        );

        mvc.perform(get("/auth/users/roles")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(() -> "ROLE_ADMIN")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value("u1"))
                .andExpect(jsonPath("$.items[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void activityLogs_adminRequest_returnsPaginatedLogs() throws Exception {
        when(authService.getActivityLogs(any())).thenReturn(new PageImpl<>(
                List.of(
                        new ActivityLogResponse(
                                1L,
                                "admin-1",
                                "u1",
                                "ROLE_CHANGED",
                                "USER",
                                "u1",
                                "Changed role for user u1 from CLIENT to ADMIN",
                                "{\"previousRoleCode\":\"CLIENT\",\"newRoleCode\":\"ADMIN\"}",
                                java.time.Instant.parse("2026-05-06T08:00:00Z")
                        )
                ),
                PageRequest.of(0, 10),
                1
        ));

        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal(
                "admin-1",
                "admin@test.com",
                RoleCode.ADMIN
        );

        mvc.perform(get("/auth/activity-logs")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(() -> "ROLE_ADMIN")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1))
                .andExpect(jsonPath("$.items[0].action").value("ROLE_CHANGED"))
                .andExpect(jsonPath("$.items[0].subjectType").value("USER"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    void assignUserRole_adminRequest_returnsUpdatedUserRole() throws Exception {
        when(authService.assignUserRole(anyString(), eq("u1"), eq(RoleCode.ADMIN))).thenReturn(
                new UserRoleSummaryResponse("u1", "john@test.com", "John Doe", "http://img.png", RoleCode.ADMIN)
        );

        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal(
                "admin-1",
                "admin@test.com",
                RoleCode.ADMIN
        );

        mvc.perform(patch("/auth/users/u1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(RoleCode.ADMIN)))
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(() -> "ROLE_ADMIN")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("u1"))
                .andExpect(jsonPath("$.roleCode").value("ADMIN"));
    }
}
