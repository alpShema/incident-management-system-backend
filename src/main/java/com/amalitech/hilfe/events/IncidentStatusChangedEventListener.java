package com.amalitech.hilfe.events;

import com.amalitech.hilfe.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class IncidentStatusChangedEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(IncidentStatusChangedEvent event) {
        String clientUserId = event.clientUserId();
        String agentUserId = event.assignedAgentUserId();
        String actorUserId = event.actorUserId();
        int incidentNo = event.incidentNo();
        String incidentId = event.incidentId();
        String previousStatus = event.previousStatus();
        String newStatus = event.newStatus();
        String reason = event.reason();

        // Notify client (unless they are the actor)
        if (clientUserId != null && !clientUserId.equals(actorUserId)) {
            if ("Pending".equals(newStatus)) {
                notificationService.sendPendingNotification(clientUserId, incidentId, incidentNo, reason);
            } else {
                notificationService.sendStatusChangeNotification(clientUserId, incidentId, incidentNo, previousStatus, newStatus, reason);
            }
        }

        // Notify agent (unless they are the actor)
        if (agentUserId != null && !agentUserId.equals(actorUserId)) {
            if ("Reopened".equalsIgnoreCase(newStatus) || "In Progress".equalsIgnoreCase(newStatus)) {
                notificationService.sendReopenedNotification(agentUserId, incidentId, incidentNo);
            } else {
                notificationService.sendStatusChangeNotification(agentUserId, incidentId, incidentNo, previousStatus, newStatus, reason);
            }
        }
    }
}
