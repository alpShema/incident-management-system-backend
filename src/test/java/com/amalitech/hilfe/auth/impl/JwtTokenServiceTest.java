package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenRevocationService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    @Mock UserAuthorityService userAuthorityService;
    @Mock TokenRevocationService tokenRevocationService;

    KeyPair keyPair;
    JwtTokenService tokenService;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = generateKeyPair();
        tokenService = new JwtTokenService(toPem(keyPair.getPublic()), userAuthorityService, tokenRevocationService);
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPem(PublicKey key) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(key.getEncoded())
                + "\n-----END PUBLIC KEY-----";
    }

    private String signedToken(KeyPair signingKeyPair, String userId, Instant expiry) {
        var builder = Jwts.builder()
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry));
        if (userId != null) {
            builder.claim("user_id", userId);
        }
        return builder.signWith(signingKeyPair.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String signedToken(String userId, Instant expiry) {
        return signedToken(keyPair, userId, expiry);
    }

    private void mockRevoked(String token, boolean revoked) {
        when(tokenRevocationService.isRevoked(token)).thenReturn(revoked);
    }

    private void mockResolvedAuthorities(String userId, String email, RoleCode roleCode, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        when(userAuthorityService.resolveByUserId(userId)).thenReturn(Optional.of(
                new UserAuthorityService.ResolvedAuthorities(userId, email, roleCode, grantedAuthorities, 1)
        ));
    }

    @Test
    void authenticateAccessToken_validSignatureAndClaims_returnsAuthenticationWithCorrectPrincipal() {
        String token = signedToken("u1", Instant.now().plusSeconds(3600));
        mockRevoked(token, false);
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
    void authenticateAccessToken_tamperedToken_returnsEmpty() {
        String token = signedToken("u1", Instant.now().plusSeconds(3600)) + "x";

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_tokenSignedWithDifferentKey_returnsEmpty() throws Exception {
        KeyPair otherKeyPair = generateKeyPair();
        String foreignToken = signedToken(otherKeyPair, "u1", Instant.now().plusSeconds(3600));

        Optional<Authentication> auth = tokenService.authenticateAccessToken(foreignToken);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_expiredToken_returnsEmpty() {
        String token = signedToken("u1", Instant.now().minusSeconds(10));

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_missingUserIdClaim_returnsEmpty() {
        String token = signedToken(null, Instant.now().plusSeconds(3600));

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_revokedToken_returnsEmpty() {
        String token = signedToken("u1", Instant.now().plusSeconds(3600));
        mockRevoked(token, true);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_userMissingFromAuthorityService_returnsEmpty() {
        String token = signedToken("u4", Instant.now().plusSeconds(3600));
        mockRevoked(token, false);
        when(userAuthorityService.resolveByUserId("u4")).thenReturn(Optional.empty());

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_userWithAgentRole_returnsRoleAgent() {
        String token = signedToken("u2", Instant.now().plusSeconds(3600));
        mockRevoked(token, false);
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
        String token = signedToken("u3", Instant.now().plusSeconds(3600));
        mockRevoked(token, false);
        mockResolvedAuthorities("u3", "admin@test.com", RoleCode.ADMIN, "ROLE_ADMIN", "agent.create");

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        JwtTokenService.AuthPrincipal principal = (JwtTokenService.AuthPrincipal) auth.get().getPrincipal();
        assertThat(principal.roleCode()).isEqualTo("ADMIN");
    }

    @Test
    void getArmsTokenRemainingSeconds_validToken_returnsRemainingLifetime() {
        String token = signedToken("u1", Instant.now().plusSeconds(7200));

        long remaining = tokenService.getArmsTokenRemainingSeconds(token);

        assertThat(remaining).isGreaterThan(7000L).isLessThanOrEqualTo(7200L);
    }

    @Test
    void getArmsTokenRemainingSeconds_expiredToken_throwsArmsAuthException() {
        String token = signedToken("u1", Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> tokenService.getArmsTokenRemainingSeconds(token))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(401);
    }

    @Test
    void getArmsTokenRemainingSeconds_tamperedToken_throwsArmsAuthException() {
        String token = signedToken("u1", Instant.now().plusSeconds(3600)) + "x";

        assertThatThrownBy(() -> tokenService.getArmsTokenRemainingSeconds(token))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(401);
    }
}
