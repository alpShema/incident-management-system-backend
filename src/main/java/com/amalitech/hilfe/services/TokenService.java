package com.amalitech.hilfe.services;

import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface TokenService {
    Optional<Authentication> authenticateAccessToken(String armsToken);

    long getArmsTokenRemainingSeconds(String armsToken);
}
