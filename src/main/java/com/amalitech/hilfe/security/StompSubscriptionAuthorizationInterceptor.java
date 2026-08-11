package com.amalitech.hilfe.security;

import com.amalitech.hilfe.services.JwtTokenService;
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

public class StompSubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private static final Pattern USER_TOPIC_PATTERN = Pattern.compile("^/topic/users/([^/]+)(?:/.*)?$");

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher matcher = USER_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }

        String requestedUserId = matcher.group(1);
        String authenticatedUserId = resolveAuthenticatedUserId(accessor.getSessionAttributes());
        if (authenticatedUserId == null || !authenticatedUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("Not authorized to subscribe to this topic");
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
