package com.amalitech.hilfe.security;

import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.services.ConfidentialIncidentAccess;
import com.amalitech.hilfe.services.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class StompSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern USER_TOPIC_PATTERN = Pattern.compile("^/topic/users/([^/]+)(?:/.*)?$");
    // HV-1619: incident chat is broadcast live on this topic. sendMessage/listMessages already
    // gate the HTTP/GraphQL history, but a client that just knows (or guesses) an incident ID
    // could otherwise SUBSCRIBE directly and receive every future message pushed to it,
    // bypassing that check entirely for a confidential incident.
    private static final Pattern INCIDENT_TOPIC_PATTERN = Pattern.compile("^/topic/incidents/([^/]+)/messages(?:/.*)?$");

    private final IncidentRepository incidentRepository;
    private final ConfidentialIncidentAccess confidentialIncidentAccess;

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) { // NOSONAR java:S2638 - false positive: verified byte-for-byte against Spring's compiled ChannelInterceptor.preSend (spring-messaging 7.0.6), which carries the identical RuntimeVisibleTypeAnnotations Nullable marker on its @NullMarked-package return type
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher userTopicMatcher = USER_TOPIC_PATTERN.matcher(destination);
        if (userTopicMatcher.matches()) {
            String requestedUserId = userTopicMatcher.group(1);
            String authenticatedUserId = resolveAuthenticatedUserId(accessor.getSessionAttributes());
            if (authenticatedUserId == null || !authenticatedUserId.equals(requestedUserId)) {
                throw new AccessDeniedException("Not authorized to subscribe to this topic");
            }
            return message;
        }

        Matcher incidentTopicMatcher = INCIDENT_TOPIC_PATTERN.matcher(destination);
        if (incidentTopicMatcher.matches()) {
            String incidentId = incidentTopicMatcher.group(1);
            Incident incident = incidentRepository.findByIdWithDetails(incidentId).orElse(null);
            boolean isConfidential = incident != null && incident.getIncidentType() != null
                    && incident.getIncidentType().isConfidential();
            // Only confidential incidents are restricted here -- every other incident's live
            // chat is already reachable by anyone who knows/guesses its ID, same as before this
            // check existed; narrowing that further is a separate, non-confidentiality concern.
            if (isConfidential) {
                String authenticatedUserId = resolveAuthenticatedUserId(accessor.getSessionAttributes());
                if (authenticatedUserId == null || !confidentialIncidentAccess.canAccess(authenticatedUserId, incident)) {
                    throw new AccessDeniedException("Not authorized to subscribe to this topic");
                }
            }
            return message;
        }

        return message;
    }

    private String resolveAuthenticatedUserId(Map<String, Object> sessionAttributes) {
        if (sessionAttributes == null) {
            return null;
        }
        Object principalAttribute = sessionAttributes.get("principal");
        if (!(principalAttribute instanceof Authentication authentication)) {
            return null;
        }
        if (!(authentication.getPrincipal() instanceof JwtTokenService.AuthPrincipal authPrincipal)) {
            return null;
        }
        return authPrincipal.userId();
    }
}
