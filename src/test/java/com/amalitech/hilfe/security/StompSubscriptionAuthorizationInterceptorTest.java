package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompSubscriptionAuthorizationInterceptorTest {

    private final StompSubscriptionAuthorizationInterceptor interceptor = new StompSubscriptionAuthorizationInterceptor();
    private final MessageChannel channel = new MessageChannel() {
        @Override
        public boolean send(Message<?> message) {
            return true;
        }

        @Override
        public boolean send(Message<?> message, long timeout) {
            return true;
        }
    };

    private Authentication authenticationFor(String userId) {
        JwtTokenService.AuthPrincipal principal = new JwtTokenService.AuthPrincipal(userId, userId + "@test.com", "USER");
        return new UsernamePasswordAuthenticationToken(principal, null, java.util.List.of());
    }

    private Message<byte[]> subscribeMessage(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        if (authentication != null) {
            sessionAttributes.put("principal", authentication);
        }
        accessor.setSessionAttributes(sessionAttributes);
        accessor.setLeaveMutable(true);
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> commandMessage(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void subscribingToOwnUserTopic_isAllowed() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-1/notifications", authenticationFor("user-1"));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribingToAnotherUsersTopic_isRejected() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-2/notifications", authenticationFor("user-1"));

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribingWithoutAuthenticatedPrincipal_isRejected() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-1/notifications", null);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribingToUnrelatedTopic_passesThrough() {
        Message<byte[]> message = subscribeMessage("/topic/incidents/inc-1/messages", authenticationFor("user-1"));

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void nonSubscribeCommands_passThroughUnchecked() {
        Message<byte[]> connect = commandMessage(StompCommand.CONNECT, null);
        Message<byte[]> send = commandMessage(StompCommand.SEND, "/topic/users/user-2/notifications");

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
        assertThat(interceptor.preSend(send, channel)).isSameAs(send);
    }
}
