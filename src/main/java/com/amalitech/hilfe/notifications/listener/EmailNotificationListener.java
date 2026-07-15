package com.amalitech.hilfe.notifications.listener;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.models.Incident;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.notifications.content.EmailContent;
import com.amalitech.hilfe.notifications.content.EmailContentFactory;
import com.amalitech.hilfe.notifications.events.*;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.UserRepository;
import com.amalitech.hilfe.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Sends email notifications for the same domain events {@link NotificationEventListener} persists as
 * in-app notifications and relays to Slack. Subscribes independently — Spring allows multiple listeners
 * per event type — so nothing here touches the existing in-app/Slack pipeline. Entirely inactive unless
 * mail.enabled=true, since {@link EmailService} itself is conditional on that property.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mail.enabled", havingValue = "true")
public class EmailNotificationListener {

    private final EmailContentFactory contentFactory;
    private final EmailService emailService;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:https://hilfe-pro-frontend.amalitech-dev.net}")
    private String frontendUrl;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAssigned(IncidentAssignedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentUnassigned(IncidentUnassignedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentClientReassigned(IncidentClientReassignedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoAssignedClient(IncidentAutoAssignedClientEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentEscalated(IncidentEscalatedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentStatusChanged(IncidentStatusChangedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentPending(IncidentPendingEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentReopened(IncidentReopenedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentSeverityChanged(IncidentSeverityChangedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewIncidentMessage(NewIncidentMessageEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoClosedAgent(IncidentAutoClosedAgentEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentAutoClosedClient(IncidentAutoClosedClientEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentSlaAtRisk(IncidentSlaAtRiskEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentSlaBreached(IncidentSlaBreachedEvent event) {
        send(event.recipientUserId(), event.incidentId(), (incident, name) -> contentFactory.from(event, incident, name));
    }

    private void send(String recipientUserId, String incidentId, BiFunction<IncidentResponse, String, EmailContent> build) {
        try {
            Optional<User> recipient = userRepository.findById(recipientUserId);
            if (recipient.isEmpty() || recipient.get().getEmail() == null) {
                log.debug("No email on file for user {}, skipping email notification", recipientUserId);
                return;
            }

            Optional<Incident> incident = incidentRepository.findById(incidentId);
            if (incident.isEmpty()) {
                log.debug("Incident {} not found, skipping email notification", incidentId);
                return;
            }

            IncidentResponse incidentResponse = IncidentResponse.from(incident.get());
            EmailContent content = build.apply(incidentResponse, recipient.get().getFullName());
            String ctaHref = frontendUrl + "/incidents/" + incidentId;

            emailService.send(recipient.get().getEmail(), content, ctaHref);
        } catch (Exception ex) {
            log.error("Failed to send email notification for user {} incident {}", recipientUserId, incidentId, ex);
        }
    }
}
