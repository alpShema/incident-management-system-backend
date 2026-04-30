package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.models.User;
import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * Owner: Basit
 * Depends on: JWT library, key/secret management, and user role mapping.
 */
public interface TokenService {
    /** Owner: Basit. Depends on: JWT signing keys and claims. */
    String generateAccessToken(User user);
    /** Owner: Basit. Depends on: refresh token strategy and storage. */
    String generateRefreshToken(User user);
    /** Owner: Basit. Depends on: JWT validation and user role mapping. */
    Optional<Authentication> authenticateAccessToken(String token);
    /** Owner: Basit. Depends on: refresh token validation and claim parsing. */
    Optional<RefreshPrincipal> authenticateRefreshToken(String token);
    /** Owner: Basit. */
    long getAccessTokenTtlSeconds();
    /** Owner: Basit. */
    long getRefreshTokenTtlSeconds();

    /** Owner: Basit. */
    record RefreshPrincipal(String userId, String email) {}
}
