package com.amalitech.hilfe.notifications.listener;

import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.notifications.content.NotificationContentFactory;
import com.amalitech.hilfe.notifications.content.NotificationDraft;
import com.amalitech.hilfe.notifications.delivery.NotificationBroadcaster;
import com.amalitech.hilfe.notifications.events.*;
import com.amalitech.hilfe.notifications.persistence.NotificationPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationContentFactory contentFactory;
    private final NotificationPersistenceService persistenceService;
    private final NotificationBroadcaster broadcaster;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAssigned(IncidentAssignedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentEscalated(IncidentEscalatedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentStatusChanged(IncidentStatusChangedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentPending(IncidentPendingEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentReopened(IncidentReopenedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentSeverityChanged(IncidentSeverityChangedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentUnassigned(IncidentUnassignedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoClosedAgent(IncidentAutoClosedAgentEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoClosedClient(IncidentAutoClosedClientEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoAssignedClient(IncidentAutoAssignedClientEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentClientReassigned(IncidentClientReassignedEvent event) {
        handle(event.getClass().getSimpleName(), event.recipientUserId(), event.incidentId(), () -> contentFactory.from(event));
    }

    private void handle(String eventType, String recipientUserId, String incidentId, Supplier<NotificationDraft> draftSupplier) {
        try {
            NotificationDraft draft = draftSupplier.get();
            if (draft == null || draft.userId() == null) {
                return;
            }
            Notification saved = persistenceService.save(draft);
            try {
                broadcaster.broadcast(saved);
            } catch (Exception ex) {
                log.error("Failed to broadcast notification for event {} user {} incident {}", eventType, recipientUserId, incidentId, ex);
            }
        } catch (Exception ex) {
            log.error("Failed to persist notification for event {} user {} incident {}", eventType, recipientUserId, incidentId, ex);
        }
    }
}
