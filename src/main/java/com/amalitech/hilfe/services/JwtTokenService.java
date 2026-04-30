package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class JwtTokenService implements TokenService {
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE  = "type";

    private final SecretKey signingKey;
    private final String jwtIssuer;
    private final String jwtAudience;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer}") String jwtIssuer,
            @Value("${app.jwt.audience}") String jwtAudience,
            @Value("${app.jwt.access-ttl-seconds:3600}") long accessTokenTtlSeconds,
            @Value("${app.jwt.refresh-ttl-seconds:86400}") long refreshTokenTtlSeconds
    ) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtIssuer = jwtIssuer;
        this.jwtAudience = jwtAudience;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    @Override
    public String generateAccessToken(User user) {
        return buildToken(user, "access", accessTokenTtlSeconds, true);
    }

    @Override
    public String generateRefreshToken(User user) {
        return buildToken(user, "refresh", refreshTokenTtlSeconds, false);
    }

    @Override
    public Optional<Authentication> authenticateAccessToken(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);

            Claims claims = parsed.getPayload();

            if (!"access".equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }

            String userId = claims.getSubject();
            String email  = claims.get(CLAIM_EMAIL, String.class);
            List<String> roles = extractRoles(claims.get(CLAIM_ROLES));

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));

            AuthPrincipal principal = new AuthPrincipal(userId, email, roles);
            return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Access token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RefreshPrincipal> authenticateRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!"refresh".equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }

            String userId = claims.getSubject();
            String email  = claims.get(CLAIM_EMAIL, String.class);
            if (userId == null || email == null) {
                return Optional.empty();
            }

            return Optional.of(new RefreshPrincipal(userId, email));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Refresh token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    @Override
    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    private String buildToken(User user, String type, long ttlSeconds, boolean includeRoles) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);

        var builder = Jwts.builder()
                .subject(user.getId())
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (includeRoles) {
            builder.claim(CLAIM_ROLES, resolveRoles(user));
        }

        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private List<String> resolveRoles(User user) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_CLIENT");

        if (user.getAgent() != null && Boolean.TRUE.equals(user.getAgent().getStatus())) {
            roles.add("ROLE_AGENT");
        }
        if (user.getAdmin() != null && user.getAdmin().isStatus()) {
            roles.add("ROLE_ADMIN");
        }

        return roles;
    }

    private List<String> extractRoles(Object rolesClaim) {
        if (!(rolesClaim instanceof List<?> rawRoles)) {
            return new ArrayList<>();
        }
        List<String> roles = new ArrayList<>();
        for (Object role : rawRoles) {
            if (role != null) roles.add(role.toString());
        }
        return roles;
    }

    public record AuthPrincipal(String userId, String email, List<String> roles) {}
}
