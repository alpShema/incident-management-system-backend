package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.notifications.delivery.NotificationSubscriptionRegistry;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
@RequiredArgsConstructor
public class NotificationResolver {

    private final NotificationService notificationService;
    private final NotificationSubscriptionRegistry subscriptionRegistry;

    @QueryMapping
    public PageResponse<NotificationResponse> notifications(
            @Argument PageInput page,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return notificationService.getNotifications(principal.userId(), PageInput.toPageable(page));
    }

    @QueryMapping
    public long unreadNotificationCount(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return notificationService.getUnreadCount(principal.userId());
    }

    @MutationMapping
    public NotificationResponse markNotificationRead(
            @Argument String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Notification marked as read");
        return notificationService.markAsRead(principal.userId(), id);
    }

    @MutationMapping
    public boolean markAllNotificationsRead(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        notificationService.markAllAsRead(principal.userId());
        GraphQlResponseMessage.set("All notifications marked as read");
        return true;
    }

    @SubscriptionMapping
    public Flux<NotificationResponse> notificationReceived(@AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        return subscriptionRegistry.subscribe(principal.userId());
    }
}
