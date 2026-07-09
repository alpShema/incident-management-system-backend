package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.TokenService;
import com.amalitech.hilfe.utils.CookieUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
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
 */
@Component
@RequiredArgsConstructor
public class GraphQlWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String SECURITY_CONTEXT_ATTRIBUTE = "amali.securityContext";

    private final TokenService tokenService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = CookieUtils.getCookieValue(servletRequest.getServletRequest(), CookieUtils.ACCESS_TOKEN_COOKIE);
            if (token != null) {
                tokenService.authenticateAccessToken(token).ifPresent(authentication -> {
                    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
                    securityContext.setAuthentication(authentication);
                    attributes.put(SECURITY_CONTEXT_ATTRIBUTE, securityContext);
                });
            }
        }
        // Always allow the handshake through, even without a valid cookie — mirrors plain
        // HTTP /graphql being permitAll; @PreAuthorize enforces auth per-operation instead.
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake action needed; authentication is fully handled in beforeHandshake.
    }
}
