package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.User;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface TokenService {
    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    String generateRefreshToken(User user, long ttlSeconds);

    Optional<Authentication> authenticateAccessToken(String token);

    Optional<RefreshPrincipal> authenticateRefreshToken(String token);

    long getAccessTokenTtlSeconds();

    long getRefreshTokenTtlSeconds();

    record RefreshPrincipal(String userId, String email) {
    }
}
