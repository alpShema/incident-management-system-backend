package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.notifications.delivery.NotificationSubscriptionRegistry;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@GraphQlTest(NotificationResolver.class)
@Import({NotificationResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class, NotificationSubscriptionRegistry.class})
class NotificationResolverTest {

    private static final String USER_ID = "user-1";
    private static final String NOTIFICATION_ID = "notif-1";
    private static final String NOTIFICATION_TYPE = "INCIDENT_ASSIGNED";
    private static final String NOTIFICATION_TITLE = "Assigned";
    private static final String NOTIFICATION_MESSAGE = "Assigned message";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @Autowired NotificationSubscriptionRegistry subscriptionRegistry;
    @MockitoBean NotificationService notificationService;

    @BeforeEach
    void authenticate() {
        var principal = new JwtTokenService.AuthPrincipal(USER_ID, "user@test.com", RoleCode.CLIENT);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("notifications.read")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notifications_withReadFilter_passesFilterThroughToService() {
        NotificationResponse unread = new NotificationResponse(
                NOTIFICATION_ID, "inc-1", NOTIFICATION_TYPE, NOTIFICATION_TITLE, NOTIFICATION_MESSAGE, false, Instant.now());
        PageResponse<NotificationResponse> page = new PageResponse<>(List.of(unread), 0, 20, 1, 1, false, false);
        when(notificationService.getNotifications(eq(USER_ID), any(), eq(false))).thenReturn(page);

        graphQlTester.document("""
                query {
                  notifications(page: { page: 0, size: 20 }, read: false) {
                    items { id read }
                    totalElements
                  }
                }
                """)
                .execute()
                .path("notifications.items[0].id").entity(String.class).isEqualTo(NOTIFICATION_ID)
                .path("notifications.items[0].read").entity(Boolean.class).isEqualTo(false);

        verify(notificationService).getNotifications(eq(USER_ID), any(), eq(false));
    }

    @Test
    void notifications_withoutReadFilter_passesNullThroughToService() {
        NotificationResponse notification = new NotificationResponse(
                NOTIFICATION_ID, "inc-1", NOTIFICATION_TYPE, NOTIFICATION_TITLE, NOTIFICATION_MESSAGE, true, Instant.now());
        PageResponse<NotificationResponse> page = new PageResponse<>(List.of(notification), 0, 20, 1, 1, false, false);
        when(notificationService.getNotifications(eq(USER_ID), any(), isNull())).thenReturn(page);

        graphQlTester.document("""
                query {
                  notifications(page: { page: 0, size: 20 }) {
                    items { id }
                  }
                }
                """)
                .execute()
                .path("notifications.items[0].id").entity(String.class).isEqualTo(NOTIFICATION_ID);

        verify(notificationService).getNotifications(eq(USER_ID), any(), isNull());
    }

    @Test
    void notificationReceived_emitsPushedNotificationForAuthenticatedUser() {
        NotificationResponse payload = new NotificationResponse(
                NOTIFICATION_ID, "inc-1", NOTIFICATION_TYPE, NOTIFICATION_TITLE, NOTIFICATION_MESSAGE, false, Instant.now());

        Flux<NotificationResponse> flux = graphQlTester.document("""
                subscription {
                  notificationReceived {
                    id
                    incidentId
                    type
                    title
                    message
                    read
                  }
                }
                """)
                .executeSubscription()
                .toFlux("notificationReceived", NotificationResponse.class);

        StepVerifier.create(flux)
                .then(() -> subscriptionRegistry.push(USER_ID, payload))
                .assertNext(received -> {
                    assertThat(received.id()).isEqualTo(NOTIFICATION_ID);
                    assertThat(received.title()).isEqualTo(NOTIFICATION_TITLE);
                })
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }
}
