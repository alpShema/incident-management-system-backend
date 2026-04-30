package com.amalitech.hilfe.auth.impl;

import com.amalitech.hilfe.models.Admin;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Owner: Basit
 * Depends on: JwtTokenService implementation and JJWT config fields.
 */
class JwtTokenServiceTest {

    @Test
    void generateAndAuthenticateAccessToken_withRoles() {
        JwtTokenService tokenService = new JwtTokenService();
        setField(tokenService, "jwtSecret", "change-me-change-me-change-me-32chars");
        setField(tokenService, "jwtIssuer", "hilfe");
        setField(tokenService, "jwtAudience", "hilfe-web");
        setField(tokenService, "accessTokenTtlSeconds", 3600L);
        setField(tokenService, "refreshTokenTtlSeconds", 86400L);

        User user = User.builder()
            .id("user-1")
            .email("user@amalitech.com")
            .fullName("Test User")
            .build();

        Agent agent = Agent.builder().status(true).build();
        Admin admin = Admin.builder().status(true).build();
        user.setAgent(agent);
        user.setAdmin(admin);

        String token = tokenService.generateAccessToken(user);
        assertNotNull(token);

        Optional<Authentication> authentication = tokenService.authenticateAccessToken(token);
        assertTrue(authentication.isPresent());

        Set<String> roles = authentication.get().getAuthorities().stream()
            .map(auth -> auth.getAuthority())
            .collect(Collectors.toSet());

        assertTrue(roles.containsAll(List.of("ROLE_CLIENT", "ROLE_AGENT", "ROLE_ADMIN")));
    }

    @Test
    void authenticateAccessToken_withInvalidToken_returnsEmpty() {
        JwtTokenService tokenService = new JwtTokenService();
        setField(tokenService, "jwtSecret", "change-me-change-me-change-me-32chars");
        setField(tokenService, "jwtIssuer", "hilfe");
        setField(tokenService, "jwtAudience", "hilfe-web");
        setField(tokenService, "accessTokenTtlSeconds", 3600L);
        setField(tokenService, "refreshTokenTtlSeconds", 86400L);

        Optional<Authentication> authentication = tokenService.authenticateAccessToken("bad.token.value");
        assertFalse(authentication.isPresent());
    }

    @Test
    void authenticateAccessToken_withExpiredToken_returnsEmpty() {
        JwtTokenService tokenService = new JwtTokenService();
        setField(tokenService, "jwtSecret", "change-me-change-me-change-me-32chars");
        setField(tokenService, "jwtIssuer", "hilfe");
        setField(tokenService, "jwtAudience", "hilfe-web");
        setField(tokenService, "accessTokenTtlSeconds", -1L);
        setField(tokenService, "refreshTokenTtlSeconds", 86400L);

        User user = User.builder()
            .id("user-2")
            .email("expired@amalitech.com")
            .fullName("Expired User")
            .build();

        String token = tokenService.generateAccessToken(user);
        Optional<Authentication> authentication = tokenService.authenticateAccessToken(token);
        assertFalse(authentication.isPresent());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to set field: " + fieldName, ex);
        }
    }
}
