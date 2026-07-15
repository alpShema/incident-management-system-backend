package com.amalitech.hilfe.security.authorization;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserAuthorityTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void has_noAuthentication_returnsFalse() {
        SecurityContextHolder.clearContext();

        assertThat(CurrentUserAuthority.has("incident.update.any")).isFalse();
    }

    @Test
    void has_authorityPresent_returnsTrue() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null,
                        List.of(() -> "incident.update.any", () -> "incident.assign")));

        assertThat(CurrentUserAuthority.has("incident.update.any")).isTrue();
    }

    @Test
    void has_authorityAbsent_returnsFalse() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user-1", null, List.of(() -> "incident.assign")));

        assertThat(CurrentUserAuthority.has("incident.update.any")).isFalse();
    }
}
