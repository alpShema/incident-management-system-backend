package com.amalitech.hilfe.config;

import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.security.JwtHandshakeInterceptor;
import com.amalitech.hilfe.security.StompSubscriptionAuthorizationInterceptor;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import com.amalitech.hilfe.services.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TokenService tokenService;
    private final IncidentRepository incidentRepository;
    private final ConfidentialIncidentAccess confidentialIncidentAccess;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .addInterceptors(jwtHandshakeInterceptor())
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionAuthorizationInterceptor());
    }

    @Bean
    public JwtHandshakeInterceptor jwtHandshakeInterceptor() {
        return new JwtHandshakeInterceptor(tokenService);
    }

    @Bean
    public StompSubscriptionAuthorizationInterceptor subscriptionAuthorizationInterceptor() {
        return new StompSubscriptionAuthorizationInterceptor(incidentRepository, confidentialIncidentAccess);
    }
}
