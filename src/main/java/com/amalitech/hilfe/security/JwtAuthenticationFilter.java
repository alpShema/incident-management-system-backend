package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Owner: Basit
 * Depends on: TokenService and CookieUtils (access token extraction).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        String cookieNames = cookies == null
                ? "(no cookies at all)"
                : Arrays.stream(cookies).map(Cookie::getName).collect(Collectors.joining(", "));
        String accessToken = CookieUtils.getCookieValue(request, CookieUtils.ACCESS_TOKEN_COOKIE);
        log.info("{} {} — cookies received: [{}] — access_token present: {}",
                request.getMethod(), request.getRequestURI(), cookieNames, accessToken != null);

        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Authentication> authentication = tokenService.authenticateAccessToken(accessToken);
            log.info("access_token authentication result for {}: {}", request.getRequestURI(), authentication.isPresent());
            authentication.ifPresent(auth -> SecurityContextHolder.getContext().setAuthentication(auth));
        }
        filterChain.doFilter(request, response);
    }
}
