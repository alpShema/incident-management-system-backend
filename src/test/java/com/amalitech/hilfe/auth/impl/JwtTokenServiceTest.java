package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long!";
    private static final long ACCESS_TTL_SECONDS = 3600L;
    private static final long REFRESH_TTL_SECONDS = 86400L;
    private static final Instant FIXED_NOW = Instant.parse("2026-06-30T10:00:00Z");

    @Mock
    UserAuthorityService userAuthorityService;

    JwtTokenService tokenService;
    User testUser;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(
                SECRET,
                "hilfe",
                "hilfe-web",
                ACCESS_TTL_SECONDS,
                REFRESH_TTL_SECONDS,
                userAuthorityService
        );
        testUser = User.builder()
            .id("u1")
            .email("john@test.com")
            .fullName("John Doe")
            .roleCode(RoleCode.CLIENT)
            .build();
    }

    @Test
    void generateAccessToken_producesNonBlankToken() {
        mockResolvedRole(testUser, RoleCode.CLIENT);
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
        mockResolvedRole(testUser, RoleCode.CLIENT);
        String token = tokenService.generateAccessToken(testUser);
        mockResolvedAuthorities("u1", "john@test.com", RoleCode.CLIENT, "ROLE_CLIENT");

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.userId()).isEqualTo("u1");
        assertThat(principal.email()).isEqualTo("john@test.com");
        assertThat(principal.roleCode()).isEqualTo("CLIENT");
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
        mockResolvedRole(testUser, RoleCode.CLIENT);
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
            REFRESH_TTL_SECONDS,
            userAuthorityService
        );
        mockResolvedRole(testUser, RoleCode.CLIENT);
        String foreignToken = otherService.generateAccessToken(testUser);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(foreignToken);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_expiredToken_returnsEmpty() {
        JwtTokenService shortLived = new JwtTokenService(
                SECRET,
                "hilfe",
                "hilfe-web",
                -1L,
                REFRESH_TTL_SECONDS,
                userAuthorityService
        );
        mockResolvedRole(testUser, RoleCode.CLIENT);
        String token = shortLived.generateAccessToken(testUser);

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
            REFRESH_TTL_SECONDS,
            userAuthorityService
        ))
                .isInstanceOf(Exception.class);
    }

    // ── authenticateRefreshToken ───────────────────────────────────────────────

    @Test
    void authenticateRefreshToken_validToken_returnsRefreshPrincipalWithJtiAndExpiry() {
        String token = tokenService.generateRefreshToken(testUser);

        Optional<TokenService.RefreshPrincipal> result = tokenService.authenticateRefreshToken(token);

        assertThat(result).isPresent();
        assertThat(result.get().userId()).isEqualTo("u1");
        assertThat(result.get().email()).isEqualTo("john@test.com");
        assertThat(result.get().jti()).isNotBlank();
        assertThat(result.get().expiresAt()).isAfter(FIXED_NOW);
    }

    @Test
    void authenticateRefreshToken_twoTokensProduceDifferentJtis() {
        String token1 = tokenService.generateRefreshToken(testUser);
        String token2 = tokenService.generateRefreshToken(testUser);

        String jti1 = tokenService.authenticateRefreshToken(token1).orElseThrow().jti();
        String jti2 = tokenService.authenticateRefreshToken(token2).orElseThrow().jti();

        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    void authenticateRefreshToken_accessTokenPresentedAsRefresh_returnsEmpty() {
        mockResolvedRole(testUser, RoleCode.CLIENT);
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

    // ── authority resolution ───────────────────────────────────────────────────

    @Test
    void authenticateAccessToken_userWithAgentRole_returnsRoleAgent() {
        User userWithAgentRole = User.builder()
            .id("u2")
            .email("agent@test.com")
            .fullName("Agent User")
            .roleCode(RoleCode.AGENT)
            .build();

        mockResolvedRole(userWithAgentRole, RoleCode.AGENT);
        String token = tokenService.generateAccessToken(userWithAgentRole);
        mockResolvedAuthorities("u2", "agent@test.com", RoleCode.AGENT, "ROLE_AGENT", "incident.assign");
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roleCode()).isEqualTo("AGENT");
        assertThat(auth.get().getAuthorities())
            .extracting(Object::toString)
            .contains("ROLE_AGENT", "incident.assign");
    }

    @Test
    void authenticateAccessToken_userWithAdminRole_returnsRoleAdmin() {
        User userWithAdminRole = User.builder()
            .id("u3")
            .email("admin@test.com")
            .fullName("Admin User")
            .roleCode(RoleCode.ADMIN)
            .build();

        mockResolvedRole(userWithAdminRole, RoleCode.ADMIN);
        String token = tokenService.generateAccessToken(userWithAdminRole);
        mockResolvedAuthorities("u3", "admin@test.com", RoleCode.ADMIN, "ROLE_ADMIN", "agent.create");
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roleCode()).isEqualTo("ADMIN");
    }

    @Test
    void authenticateAccessToken_userMissingFromAuthorityService_returnsEmpty() {
        User user = User.builder()
            .id("u4")
            .email("u4@test.com")
            .fullName("Missing User")
            .roleCode(RoleCode.CLIENT)
            .build();

        mockResolvedRole(user, RoleCode.CLIENT);
        String token = tokenService.generateAccessToken(user);
        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_staleTokenVersion_returnsEmpty() {
        // token issued with version=1; DB now reports version=2 (post-logout increment)
        mockResolvedRole(testUser, RoleCode.CLIENT);
        String token = tokenService.generateAccessToken(testUser);
        mockResolvedAuthorities("u1", "john@test.com", RoleCode.CLIENT, 2, "ROLE_CLIENT");

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_matchingTokenVersion_returnsAuthentication() {
        // token issued with version=1; DB also reports version=1 — valid
        mockResolvedRole(testUser, RoleCode.CLIENT);
        String token = tokenService.generateAccessToken(testUser);
        mockResolvedAuthorities("u1", "john@test.com", RoleCode.CLIENT, 1, "ROLE_CLIENT");

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
    }

    @Test
    void authenticateAccessToken_mismatchedRoleClaim_returnsEmpty() {
        User userWithAdminRole = User.builder()
            .id("u5")
            .email("u5@test.com")
            .fullName("Role Drift User")
            .roleCode(RoleCode.ADMIN)
            .build();

        mockResolvedRole(userWithAdminRole, RoleCode.ADMIN);
        String token = tokenService.generateAccessToken(userWithAdminRole);
        mockResolvedAuthorities("u5", "u5@test.com", RoleCode.AGENT, "ROLE_AGENT");

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    private void mockResolvedAuthorities(
        String userId,
        String email,
        RoleCode roleCode,
        String... authorities
    ) {
        mockResolvedAuthorities(userId, email, roleCode, 1, authorities);
    }

    private void mockResolvedAuthorities(
        String userId,
        String email,
        RoleCode roleCode,
        int tokenVersion,
        String... authorities
    ) {
        List<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
            .map(SimpleGrantedAuthority::new)
            .toList();

        when(userAuthorityService.resolveByUserId(userId)).thenReturn(Optional.of(
            new UserAuthorityService.ResolvedAuthorities(userId, email, roleCode, grantedAuthorities, tokenVersion)
        ));
    }

    private void mockResolvedRole(User user, RoleCode roleCode) {
        when(userAuthorityService.resolve(user)).thenReturn(
            new UserAuthorityService.ResolvedAuthorities(
                user.getId(),
                user.getEmail(),
                roleCode,
                List.of(new SimpleGrantedAuthority("ROLE_" + roleCode.name())),
                user.getTokenVersion()
            )
        );
    }
}
