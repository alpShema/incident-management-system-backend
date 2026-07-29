package com.amalitech.hilfe.services;

import com.amalitech.hilfe.constants.ApiMessages;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.authorization.UserAuthorityService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
public class JwtTokenService implements TokenService {
    private static final String CLAIM_USER_ID = "user_id";

    private final PublicKey armsPublicKey;
    private final UserAuthorityService userAuthorityService;
    private final TokenRevocationService tokenRevocationService;

    public JwtTokenService(
            @Value("${arms.sso-public-key}") String armsSsoPublicKeyPem,
            UserAuthorityService userAuthorityService,
            TokenRevocationService tokenRevocationService
    ) {
        this.armsPublicKey = parsePublicKey(armsSsoPublicKeyPem);
        this.userAuthorityService = userAuthorityService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Override
    public Optional<Authentication> authenticateAccessToken(String armsToken) {
        try {
            Claims claims = verifyArmsToken(armsToken);
            Object rawUserId = claims.get(CLAIM_USER_ID);
            String userId = rawUserId == null ? null : rawUserId.toString();
            if (userId == null || userId.isBlank()) {
                log.warn("access_token rejected: user_id claim missing/blank (raw={})", rawUserId);
                return Optional.empty();
            }
            if (tokenRevocationService.isRevoked(armsToken)) {
                log.warn("access_token rejected: token is revoked for userId={}", userId);
                return Optional.empty();
            }
            Optional<Authentication> resolved = userAuthorityService.resolveByUserId(userId)
                    .map(r -> {
                        AuthPrincipal principal = new AuthPrincipal(r.userId(), r.email(), r.roleCode());
                        return new UsernamePasswordAuthenticationToken(principal, null, r.authorities());
                    });
            if (resolved.isEmpty()) {
                log.warn("access_token rejected: userAuthorityService.resolveByUserId({}) returned empty", userId);
            }
            return resolved;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Rejected access_token on read path: {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long getArmsTokenRemainingSeconds(String armsToken) {
        try {
            Claims claims = verifyArmsToken(armsToken);
            long remainingSeconds = claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond();
            if (remainingSeconds <= 0) {
                throw new ArmsAuthException(ApiMessages.SESSION_EXPIRED, 401);
            }
            return remainingSeconds;
        } catch (ArmsAuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            throw new ArmsAuthException(ApiMessages.SESSION_UNVERIFIABLE, 401, e);
        }
    }

    private Claims verifyArmsToken(String armsToken) {
        return Jwts.parser()
                .verifyWith(armsPublicKey)
                .build()
                .parseSignedClaims(armsToken)
                .getPayload();
    }

    private static PublicKey parsePublicKey(String pem) {
        String base64Body = pem
                .replace("\\n", "")
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(base64Body);
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to parse ARMS SSO public key", e);
        }
    }

    public record AuthPrincipal(String userId, String email, String roleCode) {
        public AuthPrincipal(String userId, String email, RoleCode roleCode) {
            this(userId, email, roleCode == null ? null : roleCode.name());
        }
    }
}
