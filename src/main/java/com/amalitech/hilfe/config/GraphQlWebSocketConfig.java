package com.amalitech.hilfe.config;

import com.amalitech.hilfe.security.GraphQlWebSocketHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webmvc.GraphQlWebSocketHandler;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHandlerMapping;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manually wires the GraphQL-over-WebSocket endpoint instead of relying on Spring Boot's
 * spring.graphql.websocket.path auto-configuration, so a HandshakeInterceptor can be
 * attached. This mirrors exactly what GraphQlWebMvcAutoConfiguration.WebSocketConfiguration
 * builds internally (same WebSocketHandlerMapping/order/urlMap recipe), but adds
 * GraphQlWebSocketHandshakeInterceptor for cookie-based auth (see that class for why it must
 * run at handshake time, not later).
 */
@Configuration
@RequiredArgsConstructor
public class GraphQlWebSocketConfig {

    private static final String PATH = "/graphql";

    private final WebGraphQlHandler webGraphQlHandler;
    private final GraphQlWebSocketHandshakeInterceptor handshakeInterceptor;

    @Bean
    public HandlerMapping graphQlWebSocketMapping() {
        GraphQlWebSocketHandler handler = new GraphQlWebSocketHandler(
                webGraphQlHandler,
                new MappingJackson2HttpMessageConverter(),
                Duration.ofSeconds(60)
        );

        WebSocketHttpRequestHandler requestHandler =
                handler.initWebSocketHttpRequestHandler(new DefaultHandshakeHandler());
        // initWebSocketHttpRequestHandler already registers its own internal
        // ContextHandshakeInterceptor for Micrometer context propagation — append to that
        // list rather than replacing it, or the WS handler NPEs looking for its ContextSnapshot.
        List<HandshakeInterceptor> interceptors = new ArrayList<>(requestHandler.getHandshakeInterceptors());
        interceptors.add(handshakeInterceptor);
        requestHandler.setHandshakeInterceptors(interceptors);

        WebSocketHandlerMapping mapping = new WebSocketHandlerMapping();
        mapping.setWebSocketUpgradeMatch(true);
        mapping.setUrlMap(Collections.singletonMap(PATH, requestHandler));
        mapping.setOrder(-2);
        return mapping;
    }
}
