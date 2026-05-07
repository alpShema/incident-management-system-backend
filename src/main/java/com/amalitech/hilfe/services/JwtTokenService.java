package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtTokenService implements TokenService {
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

    private final SecretKey signingKey;
    private final String jwtIssuer;
    private final String jwtAudience;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;
    private final UserAuthorityService userAuthorityService;

    public JwtTokenService(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.issuer}") String jwtIssuer,
            @Value("${app.jwt.audience}") String jwtAudience,
            @Value("${app.jwt.access-ttl-seconds:3600}") long accessTokenTtlSeconds,
            @Value("${app.jwt.refresh-ttl-seconds:86400}") long refreshTokenTtlSeconds,
            UserAuthorityService userAuthorityService
    ) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtIssuer = jwtIssuer;
        this.jwtAudience = jwtAudience;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.userAuthorityService = userAuthorityService;
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
    public String generateRefreshToken(User user, long ttlSeconds) {
        return buildToken(user, "refresh", ttlSeconds, false);
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
            String email = claims.get(CLAIM_EMAIL, String.class);
            String claimedRole = claims.get(CLAIM_ROLE, String.class);
            return userAuthorityService.resolveByUserId(userId)
                    .filter(resolvedAuthorities -> email.equals(resolvedAuthorities.email()))
                    .filter(resolvedAuthorities -> hasMatchingRoleClaim(claimedRole, resolvedAuthorities.roleCode()))
                    .map(resolvedAuthorities -> {
                        AuthPrincipal principal = new AuthPrincipal(
                                resolvedAuthorities.userId(),
                                resolvedAuthorities.email(),
                                resolvedAuthorities.roleCode()
                        );
                        return new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                resolvedAuthorities.authorities()
                        );
                    });

        } catch (JwtException | IllegalArgumentException e) {
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
            String email = claims.get(CLAIM_EMAIL, String.class);
            if (userId == null || email == null) {
                return Optional.empty();
            }

            return Optional.of(new RefreshPrincipal(userId, email));
        } catch (JwtException | IllegalArgumentException e) {
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

    private String buildToken(User user, String type, long ttlSeconds, boolean includeRoleClaim) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(ttlSeconds);

        var builder = Jwts.builder()
                .subject(user.getId())
                .issuer(jwtIssuer)
                .audience().add(jwtAudience).and()
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (includeRoleClaim) {
            RoleCode resolvedRoleCode = userAuthorityService.resolve(user).roleCode();
            builder.claim(CLAIM_ROLE, resolvedRoleCode.name());
        }

        return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private boolean hasMatchingRoleClaim(String claimedRole, RoleCode resolvedRoleCode) {
        return claimedRole != null && claimedRole.equals(resolvedRoleCode.name());
    }

    public record AuthPrincipal(String userId, String email, RoleCode roleCode) {
    }
}
