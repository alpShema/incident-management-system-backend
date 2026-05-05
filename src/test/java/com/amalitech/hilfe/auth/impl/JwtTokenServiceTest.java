package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long!";
    private static final long ACCESS_TTL_SECONDS = 3600L;
    private static final long REFRESH_TTL_SECONDS = 86400L;

    JwtTokenService tokenService;
    User testUser;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(
                SECRET,
                "hilfe",
                "hilfe-web",
                ACCESS_TTL_SECONDS,
                REFRESH_TTL_SECONDS
        );
        testUser = User.builder().id("u1").email("john@test.com").fullName("John Doe").build();
    }

    @Test
    void generateAccessToken_producesNonBlankToken() {
        String token = tokenService.generateAccessToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void generateRefreshToken_producesNonBlankToken() {
        String token = tokenService.generateRefreshToken(testUser);
        assertThat(token).isNotBlank();
    }

    @Test
    void authenticateAccessToken_validToken_returnsAuthenticationWithCorrectPrincipal() {
        String token = tokenService.generateAccessToken(testUser);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.userId()).isEqualTo("u1");
        assertThat(principal.email()).isEqualTo("john@test.com");
        assertThat(auth.get().getAuthorities()).isNotEmpty();
    }

    @Test
    void authenticateAccessToken_refreshTokenPresentedAsAccess_returnsEmpty() {
        String refreshToken = tokenService.generateRefreshToken(testUser);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(refreshToken);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_tamperedToken_returnsEmpty() {
        String token = tokenService.generateAccessToken(testUser) + "x";

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_tokenSignedWithDifferentKey_returnsEmpty() {
        JwtTokenService otherService = new JwtTokenService(
            "different-secret-also-at-least-32-bytes-!",
            "hilfe",
            "hilfe-web",
            ACCESS_TTL_SECONDS,
            REFRESH_TTL_SECONDS
        );
        String foreignToken = otherService.generateAccessToken(testUser);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(foreignToken);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_expiredToken_returnsEmpty() throws InterruptedException {
        JwtTokenService shortLived = new JwtTokenService(
                SECRET,
                "hilfe",
                "hilfe-web",
                1L,
                REFRESH_TTL_SECONDS
        );
        String token = shortLived.generateAccessToken(testUser);
        Thread.sleep(1100);

        Optional<Authentication> auth = shortLived.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void getAccessTokenTtlSeconds_returnsTtlMsDividedBy1000() {
        assertThat(tokenService.getAccessTokenTtlSeconds()).isEqualTo(ACCESS_TTL_SECONDS);
    }

    @Test
    void getRefreshTokenTtlSeconds_returns86400() {
        assertThat(tokenService.getRefreshTokenTtlSeconds()).isEqualTo(REFRESH_TTL_SECONDS);
    }

    @Test
    void shortSecret_throwsAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenService(
            "short",
            "hilfe",
            "hilfe-web",
            ACCESS_TTL_SECONDS,
            REFRESH_TTL_SECONDS
        ))
                .isInstanceOf(Exception.class);
    }

    // ── authenticateRefreshToken ───────────────────────────────────────────────

    @Test
    void authenticateRefreshToken_validToken_returnsRefreshPrincipal() {
        String token = tokenService.generateRefreshToken(testUser);

        Optional<TokenService.RefreshPrincipal> result = tokenService.authenticateRefreshToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("u1");
        assertThat(result.get().email()).isEqualTo("john@test.com");
    }

    @Test
    void authenticateRefreshToken_accessTokenPresentedAsRefresh_returnsEmpty() {
        String accessToken = tokenService.generateAccessToken(testUser);

        Optional<TokenService.RefreshPrincipal> result = tokenService.authenticateRefreshToken(accessToken);

        assertThat(result).isEmpty();
    }

    @Test
    void authenticateRefreshToken_tamperedToken_returnsEmpty() {
        String token = tokenService.generateRefreshToken(testUser) + "x";

        Optional<TokenService.RefreshPrincipal> result = tokenService.authenticateRefreshToken(token);

        assertThat(result).isEmpty();
    }

    // ── role resolution ────────────────────────────────────────────────────────

    @Test
    void generateAccessToken_userWithActiveAgent_includesRoleAgent() {
        Agent agent = Agent.builder().status(true).build();
        User userWithAgent = User.builder().id("u2").email("agent@test.com").fullName("Agent User").agent(agent).build();

        String token = tokenService.generateAccessToken(userWithAgent);
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roles()).contains("ROLE_AGENT");
    }

    @Test
    void generateAccessToken_userWithAdmin_includesRoleAdmin() {
        Admin admin = Admin.builder().status(true).build();
        User userWithAdmin = User.builder().id("u3").email("admin@test.com").fullName("Admin User").admin(admin).build();

        String token = tokenService.generateAccessToken(userWithAdmin);
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roles()).contains("ROLE_ADMIN");
    }

    @Test
    void generateAccessToken_userWithInactiveAgent_omitsRoleAgent() {
        Agent inactiveAgent = Agent.builder().status(false).build();
        User user = User.builder().id("u4").email("u4@test.com").fullName("Inactive Agent").agent(inactiveAgent).build();

        String token = tokenService.generateAccessToken(user);
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roles()).doesNotContain("ROLE_AGENT");
    }
}
