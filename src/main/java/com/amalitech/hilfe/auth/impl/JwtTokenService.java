package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.TokenService;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Real implementation of TokenService using JJWT 0.12.x.
 * Presence of this bean suppresses StubTokenService via @ConditionalOnMissingBean.
 *
 * JWT claims:
 *   sub   = user.getId() (ARMS user_id, which is the local PK)
 *   email = user.getEmail()
 *   type  = "access" | "refresh"
 */
@Slf4j
@Service
public class JwtTokenService implements TokenService {

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;
    private final UserRepository userRepository;

    public JwtTokenService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiry-ms:3600000}") long accessTokenTtlMs,
            UserRepository userRepository
    ) {
        // hmacShaKeyFor throws WeakKeyException if secret < 32 bytes — fast startup failure
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTokenTtlMs;
        this.refreshTokenTtlMs = 86_400_000L; // 24 hours
        this.userRepository = userRepository;
    }

    @Override
    public String generateAccessToken(User user) {
        return buildToken(user, "access", accessTokenTtlMs);
    }

    @Override
    public String generateRefreshToken(User user) {
        return buildToken(user, "refresh", refreshTokenTtlMs);
    }

    @Override
    public Optional<Authentication> authenticateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Reject refresh tokens presented as access tokens
            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }

            String userId = claims.getSubject();
            return userRepository.findById(userId)
                    .map(user -> (Authentication) new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    ));

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Access token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMs / 1000;
    }

    @Override
    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlMs / 1000;
    }

    private String buildToken(User user, String type, long ttlMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId())
                .claim("email", user.getEmail())
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(signingKey)
                .compact();
    }
}
