package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.auth.TokenService;
import com.amalitech.hilfe.models.User;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Owner: Basit
 * Depends on: real JWT implementation to replace this stub.
 */
@Service
@ConditionalOnMissingBean(TokenService.class)
public class StubTokenService implements TokenService {
    @Override
    public String generateAccessToken(User user) {
        throw new UnsupportedOperationException("Token service not implemented");
    }

    @Override
    public String generateRefreshToken(User user) {
        throw new UnsupportedOperationException("Token service not implemented");
    }

    @Override
    public Optional<Authentication> authenticateAccessToken(String token) {
        return Optional.empty();
    }

    @Override
    public long getAccessTokenTtlSeconds() {
        return 3600;
    }

    @Override
    public long getRefreshTokenTtlSeconds() {
        return 86400;
    }
}
