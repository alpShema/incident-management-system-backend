package com.amalitech.hilfe.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Propagates the SecurityContext captured at handshake time (by
 * GraphQlWebSocketHandshakeInterceptor) into the Reactor context for each GraphQL operation
 * sent over a WebSocket connection, so @AuthenticationPrincipal/@PreAuthorize work on
 * @SubscriptionMapping resolvers exactly as they do for plain HTTP query/mutation resolvers.
 *
 * Only WebSocket-transported requests are handled here; HTTP requests already get an
 * authenticated SecurityContext from JwtAuthenticationFilter earlier in the filter chain.
 */
@Component
@Slf4j
public class GraphQlWebSocketAuthInterceptor implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        if (!(request instanceof WebSocketGraphQlRequest wsRequest)) {
            return chain.next(request);
        }

        SecurityContext securityContext = (SecurityContext) wsRequest.getSessionInfo()
                .getAttributes()
                .get(GraphQlWebSocketHandshakeInterceptor.SECURITY_CONTEXT_ATTRIBUTE);
        if (securityContext == null) {
            log.debug("GraphQlWebSocketAuthInterceptor: no SecurityContext captured at handshake");
            return chain.next(request);
        }

        return chain.next(request)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(securityContext)));
    }
}
