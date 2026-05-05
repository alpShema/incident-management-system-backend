package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Owner: Basit
 * Depends on: TokenService and CookieUtils (access token extraction).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = CookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE);
        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Authentication> authentication = tokenService.authenticateAccessToken(accessToken);
            authentication.ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        filterChain.doFilter(request, response);
    }
}
