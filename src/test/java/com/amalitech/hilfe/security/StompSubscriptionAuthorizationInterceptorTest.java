package com.amalitech.hilfe.security;

import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import com.amalitech.hilfe.services.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompSubscriptionAuthorizationInterceptorTest {

    @Mock IncidentRepository incidentRepository;
    @Mock ConfidentialIncidentAccess confidentialIncidentAccess;

    private StompSubscriptionAuthorizationInterceptor interceptor;

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

    private StompSubscriptionAuthorizationInterceptor interceptor() {
        return new StompSubscriptionAuthorizationInterceptor(incidentRepository, confidentialIncidentAccess);
    }

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

    private Incident confidentialIncident(String id) {
        IncidentType topic = IncidentType.builder().id("type-1").agentGroupId("group-1").confidential(true).build();
        Incident incident = Incident.builder().id(id).build();
        incident.setIncidentType(topic);
        return incident;
    }

    private Incident nonConfidentialIncident(String id) {
        return Incident.builder().id(id).build();
    }

    @Test
    void subscribingToOwnUserTopic_isAllowed() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-1/notifications", authenticationFor("user-1"));

        Message<?> result = interceptor().preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribingToAnotherUsersTopic_isRejected() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-2/notifications", authenticationFor("user-1"));

        assertThatThrownBy(() -> interceptor().preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribingWithoutAuthenticatedPrincipal_isRejected() {
        Message<byte[]> message = subscribeMessage("/topic/users/user-1/notifications", null);

        assertThatThrownBy(() -> interceptor().preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonSubscribeCommands_passThroughUnchecked() {
        Message<byte[]> connect = commandMessage(StompCommand.CONNECT, null);
        Message<byte[]> send = commandMessage(StompCommand.SEND, "/topic/users/user-2/notifications");
        StompSubscriptionAuthorizationInterceptor interceptor = interceptor();

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
        assertThat(interceptor.preSend(send, channel)).isSameAs(send);
    }

    // ── HV-1619: incident chat topics ────────────────────────────────────────

    @Test
    void subscribingToNonConfidentialIncidentTopic_passesThrough() {
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(nonConfidentialIncident("inc-1")));
        Message<byte[]> message = subscribeMessage("/topic/incidents/inc-1/messages", authenticationFor("user-1"));

        Message<?> result = interceptor().preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribingToUnknownIncidentTopic_passesThrough() {
        // Nothing will ever be published to a nonexistent incident's topic anyway; not worth
        // denying outright and this keeps the check narrowly scoped to confidentiality.
        when(incidentRepository.findByIdWithDetails("missing")).thenReturn(Optional.empty());
        Message<byte[]> message = subscribeMessage("/topic/incidents/missing/messages", authenticationFor("user-1"));

        Message<?> result = interceptor().preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribingToConfidentialIncidentTopic_memberIsAllowed() {
        Incident incident = confidentialIncident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("member-user", incident)).thenReturn(true);
        Message<byte[]> message = subscribeMessage("/topic/incidents/inc-1/messages", authenticationFor("member-user"));

        Message<?> result = interceptor().preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void subscribingToConfidentialIncidentTopic_nonMemberIsRejected() {
        // The actual vulnerability this closes: sendMessage/listMessages already block a
        // non-member from the HTTP/GraphQL surface, but a client that just knew the incident ID
        // could otherwise subscribe directly and receive every future message live.
        Incident incident = confidentialIncident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        when(confidentialIncidentAccess.canAccess("outsider", incident)).thenReturn(false);
        Message<byte[]> message = subscribeMessage("/topic/incidents/inc-1/messages", authenticationFor("outsider"));

        assertThatThrownBy(() -> interceptor().preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void subscribingToConfidentialIncidentTopic_unauthenticatedIsRejected() {
        Incident incident = confidentialIncident("inc-1");
        when(incidentRepository.findByIdWithDetails("inc-1")).thenReturn(Optional.of(incident));
        Message<byte[]> message = subscribeMessage("/topic/incidents/inc-1/messages", null);

        assertThatThrownBy(() -> interceptor().preSend(message, channel))
                .isInstanceOf(AccessDeniedException.class);
    }
}
