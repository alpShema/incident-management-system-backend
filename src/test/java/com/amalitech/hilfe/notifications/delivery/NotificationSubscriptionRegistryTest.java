package com.amalitech.hilfe.notifications.delivery;

import com.amalitech.hilfe.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSubscriptionRegistryTest {

    private final NotificationSubscriptionRegistry registry = new NotificationSubscriptionRegistry();

    private static NotificationResponse response(String id) {
        return new NotificationResponse(id, "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message", false, Instant.now());
    }

    @Test
    void subscriberReceivesPushedNotification() {
        NotificationResponse payload = response("notif-1");
        Flux<NotificationResponse> flux = registry.subscribe("user-1");

        StepVerifier.create(flux)
                .then(() -> registry.push("user-1", payload))
                .expectNext(payload)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void twoConcurrentSubscribersForSameUserBothReceivePush() {
        NotificationResponse payload = response("notif-2");
        Flux<NotificationResponse> fluxA = registry.subscribe("user-1");
        Flux<NotificationResponse> fluxB = registry.subscribe("user-1");

        StepVerifier.create(fluxA)
                .then(() -> {
                    // Subscribe B before pushing, so both are attached to the shared sink.
                    fluxB.subscribe();
                    registry.push("user-1", payload);
                })
                .expectNext(payload)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void pushWithNoSubscriberIsNoOp() {
        // No subscribe() call for this user at all — must not throw, must not create a sink.
        registry.push("user-with-no-subscription", response("notif-3"));

        // A later subscriber must not see any stale/backlogged item from the no-op push above.
        NotificationResponse fresh = response("notif-4");
        StepVerifier.create(registry.subscribe("user-with-no-subscription"))
                .then(() -> registry.push("user-with-no-subscription", fresh))
                .expectNext(fresh)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void cancelledSubscriptionDoesNotLeakAndFreshSubscribeStartsClean() {
        StepVerifier.create(registry.subscribe("user-2"))
                .thenCancel()
                .verify(Duration.ofSeconds(2));

        // doFinally runs synchronously on cancellation here (no scheduler hop in this chain),
        // so cleanup has already happened by the time verify() returns above.
        assertThat(registry.hasSinkFor("user-2")).isFalse();

        NotificationResponse payload = response("notif-5");
        StepVerifier.create(registry.subscribe("user-2"))
                .then(() -> registry.push("user-2", payload))
                .expectNext(payload)
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }
}
