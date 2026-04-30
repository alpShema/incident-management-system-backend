package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.TokenService;
import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owner: Basit
 * Depends on: JJWT configuration, secret management, and role mapping.
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService implements TokenService {
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    @Value("${app.jwt.audience}")
    private String jwtAudience;

    @Value("${app.jwt.access-ttl-seconds}")
    private long accessTokenTtlSeconds;

    @Value("${app.jwt.refresh-ttl-seconds}")
    private long refreshTokenTtlSeconds;

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

        return Jwts.builder()
            .subject(user.getId())
            .issuer(jwtIssuer)
            .audience().add(jwtAudience).and()
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expiry))
            .claim(CLAIM_EMAIL, user.getEmail())
            .claim(CLAIM_ROLES, resolveRoles(user))
            .signWith(signingKey(), Jwts.SIG.HS256)
            .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(refreshTokenTtlSeconds);

        return Jwts.builder()
            .subject(user.getId())
            .issuer(jwtIssuer)
            .audience().add(jwtAudience).and()
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expiry))
            .claim(CLAIM_EMAIL, user.getEmail())
            .signWith(signingKey(), Jwts.SIG.HS256)
            .compact();
    }

    @Override
    public Optional<Authentication> authenticateAccessToken(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            String subject = claims.getSubject();
            String email = claims.get(CLAIM_EMAIL, String.class);
            List<String> roles = extractRoles(claims.get(CLAIM_ROLES));

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                roles.forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
            }

            AuthPrincipal principal = new AuthPrincipal(subject, email, roles);
            return Optional.of(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RefreshPrincipal> authenticateRefreshToken(String token) {
        try {
            Jws<Claims> parsed = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token);

            Claims claims = parsed.getPayload();
            String subject = claims.getSubject();
            String email = claims.get(CLAIM_EMAIL, String.class);

            if (subject == null || email == null) {
                return Optional.empty();
            }

            return Optional.of(new RefreshPrincipal(subject, email));
        } catch (JwtException | IllegalArgumentException ex) {
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

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private List<String> resolveRoles(User user) {
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_CLIENT");

        Agent agent = user.getAgent();
        if (agent != null && Boolean.TRUE.equals(agent.getStatus())) {
            roles.add("ROLE_AGENT");
        }

        Admin admin = user.getAdmin();
        if (admin != null && admin.isStatus()) {
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
            if (role != null) {
                roles.add(role.toString());
            }
        }
        return roles;
    }

    public record AuthPrincipal(String userId, String email, List<String> roles) {}
}
