package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlConfig;
import com.amalitech.hilfe.dto.NotificationResponse;
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

@GraphQlTest(NotificationResolver.class)
@Import({NotificationResolverTest.MethodSecurityTestConfig.class, GraphQlConfig.class, NotificationSubscriptionRegistry.class})
class NotificationResolverTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired GraphQlTester graphQlTester;
    @Autowired NotificationSubscriptionRegistry subscriptionRegistry;
    @MockitoBean NotificationService notificationService;

    @BeforeEach
    void authenticate() {
        var principal = new JwtTokenService.AuthPrincipal("user-1", "user@test.com", RoleCode.CLIENT);
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("notifications.read")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notificationReceived_emitsPushedNotificationForAuthenticatedUser() {
        NotificationResponse payload = new NotificationResponse(
                "notif-1", "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message", false, Instant.now());

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
                .then(() -> subscriptionRegistry.push("user-1", payload))
                .assertNext(received -> {
                    assertThat(received.id()).isEqualTo("notif-1");
                    assertThat(received.title()).isEqualTo("Assigned");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }
}
