package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the authenticated principal for the GraphQL-over-WebSocket endpoint at
 * handshake time — the only point where the underlying servlet request (and its
 * access_token cookie) is guaranteed to still be live; see GraphQlWebSocketAuthInterceptor
 * for why reading it later, from inside GraphQL's own request-interceptor chain, fails.
 *
 * The resulting SecurityContext is stashed in the WebSocket session's attributes, which
 * GraphQlWebSocketAuthInterceptor reads back on every operation over the connection.
 *
 * Unauthenticated handshakes are still allowed through (mirroring how plain HTTP /graphql
 * is permitAll and relies on @PreAuthorize per-operation) — this only populates the context
 * when a valid cookie is present.
 *
 * The Origin check below is stricter than Spring Security's usual CORS filter (which only
 * rejects a *present-but-mismatched* Origin, not a missing one) — a real browser always
 * sends Origin on a WebSocket upgrade, so requiring it here closes the gap for non-browser
 * clients that simply omit the header, at no cost to real browser-based clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphQlWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SECURITY_CONTEXT_ATTRIBUTE = "amali.securityContext";

    private final TokenService tokenService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }

        var httpRequest = servletRequest.getServletRequest();
        String origin = httpRequest.getHeader(HttpHeaders.ORIGIN);
        CorsConfiguration corsConfig = corsConfigurationSource.getCorsConfiguration(httpRequest);
        if (corsConfig == null || corsConfig.checkOrigin(origin) == null) {
            log.warn("Rejected GraphQL WebSocket handshake with disallowed or missing Origin: {}", origin);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        String token = CookieUtils.getCookieValue(httpRequest, CookieUtils.ACCESS_TOKEN_COOKIE);
        if (token != null) {
            tokenService.authenticateAccessToken(token).ifPresent(authentication -> {
                SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                attributes.put(SECURITY_CONTEXT_ATTRIBUTE, securityContext);
            });
        }
        // Beyond the Origin check above, unauthenticated handshakes are still allowed through
        // — mirrors plain HTTP /graphql being permitAll; @PreAuthorize enforces auth per-operation.
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake action needed; authentication is fully handled in beforeHandshake.
    }
}
