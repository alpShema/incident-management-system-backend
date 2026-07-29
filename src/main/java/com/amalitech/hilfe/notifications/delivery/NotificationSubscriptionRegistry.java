package com.amalitech.hilfe.notifications.delivery;

import com.amalitech.hilfe.dto.NotificationResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user multicast bridge from the notification pipeline to the notificationReceived GraphQL
 * subscription. Additive to NotificationBroadcaster (STOMP) — not a replacement.
 *
 * Sinks are created lazily, only when a user subscribes, and removed once that user has no
 * subscribers left, so this never grows unbounded for users who open a subscription once and
 * disconnect.
 */
@Component
public class NotificationSubscriptionRegistry {

    private final Map<String, Sinks.Many<NotificationResponse>> sinksByUserId = new ConcurrentHashMap<>();

    public Flux<NotificationResponse> subscribe(String userId) {
        Sinks.Many<NotificationResponse> sink = sinksByUserId.computeIfAbsent(
                userId, id -> Sinks.many().multicast().onBackpressureBuffer());

        return sink.asFlux()
                .doFinally(signalType -> cleanupIfUnused(userId, sink));
    }

    public void push(String userId, NotificationResponse payload) {
        Sinks.Many<NotificationResponse> sink = sinksByUserId.get(userId);
        if (sink == null) {
            return;
        }
        sink.tryEmitNext(payload);
    }

    private void cleanupIfUnused(String userId, Sinks.Many<NotificationResponse> sink) {
        if (sink.currentSubscriberCount() == 0) {
            sinksByUserId.remove(userId, sink);
        }
    }

    boolean hasSinkFor(String userId) {
        return sinksByUserId.containsKey(userId);
    }
}
