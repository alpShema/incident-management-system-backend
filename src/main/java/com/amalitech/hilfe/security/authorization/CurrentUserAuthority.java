package com.amalitech.hilfe.security.authorization;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Reads granted authorities off the current {@link SecurityContextHolder} so services and
 * controllers can make permission-based decisions (e.g. cross-incident update access) that
 * a single {@code @PreAuthorize} on the endpoint can't express, since the same endpoint is
 * also reachable by callers who only own the resource.
 */
public final class CurrentUserAuthority {

    private CurrentUserAuthority() {
    }

    public static boolean has(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
