package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long!";
    private static final long TTL_MS = 3_600_000L;

    @Mock UserRepository userRepository;

    JwtTokenService tokenService;
    User testUser;

    @BeforeEach
    void setUp() {
        tokenService = new JwtTokenService(SECRET, TTL_MS, userRepository);
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
        when(userRepository.findById("u1")).thenReturn(Optional.of(testUser));

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isPresent();
        assertThat(auth.get().getPrincipal()).isEqualTo(testUser);
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
                "different-secret-also-at-least-32-bytes-!", TTL_MS, userRepository);
        String foreignToken = otherService.generateAccessToken(testUser);

        Optional<Authentication> auth = tokenService.authenticateAccessToken(foreignToken);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_userNotInDb_returnsEmpty() {
        String token = tokenService.generateAccessToken(testUser);
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        Optional<Authentication> auth = tokenService.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void authenticateAccessToken_expiredToken_returnsEmpty() throws InterruptedException {
        JwtTokenService shortLived = new JwtTokenService(SECRET, 1L, userRepository);
        String token = shortLived.generateAccessToken(testUser);
        Thread.sleep(10);

        Optional<Authentication> auth = shortLived.authenticateAccessToken(token);

        assertThat(auth).isEmpty();
    }

    @Test
    void getAccessTokenTtlSeconds_returnsTtlMsDividedBy1000() {
        assertThat(tokenService.getAccessTokenTtlSeconds()).isEqualTo(TTL_MS / 1000);
    }

    @Test
    void getRefreshTokenTtlSeconds_returns86400() {
        assertThat(tokenService.getRefreshTokenTtlSeconds()).isEqualTo(86_400L);
    }

    @Test
    void shortSecret_throwsAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenService("short", TTL_MS, userRepository))
                .isInstanceOf(Exception.class);
    }
}
