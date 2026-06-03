package com.amalitech.hilfe.events;

import com.amalitech.hilfe.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IncidentCreatedEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IncidentCreatedEvent event) {
        if (event.assignedAgentUserId() != null) {
            notificationService.sendAssignmentNotification(
                    event.assignedAgentUserId(), event.incidentId(), event.incidentNo());
            notificationService.sendAutoAssignedClientNotification(
                    event.clientUserId(), event.incidentId(), event.incidentNo());
        } else {
            for (String adminUserId : event.adminUserIds()) {
                notificationService.sendEscalationNotification(
                        adminUserId, event.incidentId(), event.incidentNo());
            }
        }
    }
}
