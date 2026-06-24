package com.amalitech.hilfe.notifications.content;

import com.amalitech.hilfe.notifications.events.*;
import org.springframework.stereotype.Component;

@Component
public class NotificationContentFactory {

    private static final String INCIDENT_PREFIX = "Incident #";

    private static String incidentRef(int incidentNo) {
        return INCIDENT_PREFIX + incidentNo;
    }

    public NotificationDraft from(IncidentAssignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_ASSIGNED",
                incidentRef(event.incidentNo()) + " assigned to you",
                resolveActor(event.actorName()) + " assigned " + incidentRef(event.incidentNo()) + " to you."
        );
    }

    public NotificationDraft from(IncidentEscalatedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_ESCALATED",
                incidentRef(event.incidentNo()) + " requires attention",
                "System was unable to automatically assign " + incidentRef(event.incidentNo()) + ". Please review and assign it manually."
        );
    }

    public NotificationDraft from(IncidentStatusChangedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_STATUS_CHANGED",
                incidentRef(event.incidentNo()) + " status updated",
                buildStatusMessage(resolveActor(event.actorName()), event.incidentNo(), event.previousStatus(), event.newStatus(), event.reason())
        );
    }

    public NotificationDraft from(IncidentPendingEvent event) {
        String message = resolveActor(event.actorName()) + " placed " + incidentRef(event.incidentNo()) + " in Pending status."
                + (event.reason() != null && !event.reason().isBlank() ? " Reason: " + event.reason() : "");
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_PENDING",
                incidentRef(event.incidentNo()) + " is pending",
                message
        );
    }

    public NotificationDraft from(IncidentReopenedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_REOPENED",
                incidentRef(event.incidentNo()) + " has been reopened",
                resolveActor(event.actorName()) + " reopened " + incidentRef(event.incidentNo()) + ". It is now In Progress — please review and take action."
        );
    }

    public NotificationDraft from(IncidentSeverityChangedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_PRIORITY_CHANGED",
                incidentRef(event.incidentNo()) + " priority updated",
                resolveActor(event.actorName()) + " changed the priority of " + incidentRef(event.incidentNo())
                        + " from " + event.previousSeverity() + " to " + event.newSeverity() + "."
        );
    }

    public NotificationDraft from(IncidentUnassignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_UNASSIGNED",
                incidentRef(event.incidentNo()) + " reassigned",
                resolveActor(event.actorName()) + " reassigned " + incidentRef(event.incidentNo())
                        + " from you to " + event.newAssigneeName() + "."
        );
    }

    public NotificationDraft from(IncidentAutoClosedAgentEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_CLOSED",
                incidentRef(event.incidentNo()) + " has been automatically closed",
                "System automatically closed " + incidentRef(event.incidentNo()) + " after the resolution window elapsed."
        );
    }

    public NotificationDraft from(IncidentAutoClosedClientEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_CLOSED_CLIENT",
                incidentRef(event.incidentNo()) + " has been closed",
                "System automatically closed " + incidentRef(event.incidentNo()) + " after the resolution period elapsed."
        );
    }

    public NotificationDraft from(IncidentAutoAssignedClientEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_ASSIGNED_CLIENT",
                incidentRef(event.incidentNo()) + " is being handled",
                event.assigneeName() + " has been assigned to your " + incidentRef(event.incidentNo()) + " and will be in touch shortly."
        );
    }

    public NotificationDraft from(IncidentClientReassignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_REASSIGNED_CLIENT",
                incidentRef(event.incidentNo()) + " has a new agent",
                resolveActor(event.actorName()) + " reassigned " + incidentRef(event.incidentNo()) + " to " + event.newAssigneeName() + "."
        );
    }

    public NotificationDraft from(IncidentSlaAtRiskEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_SLA_AT_RISK",
                incidentRef(event.incidentNo()) + " " + event.slaType().toLowerCase() + " SLA at risk",
                incidentRef(event.incidentNo()) + " has about " + event.minutesRemaining()
                        + " minute(s) remaining before the " + event.slaType().toLowerCase() + " SLA is breached."
        );
    }

    public NotificationDraft from(NewIncidentMessageEvent event) {
        String preview = (event.preview() != null && !event.preview().isBlank()) ? event.preview() : "[attachment]";
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "NEW_INCIDENT_MESSAGE",
                incidentRef(event.incidentNo()) + " — new message",
                event.senderName() + " sent a message: \"" + preview + "\""
        );
    }

    public NotificationDraft from(IncidentSlaBreachedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_SLA_BREACHED",
                incidentRef(event.incidentNo()) + " " + event.slaType().toLowerCase() + " SLA breached",
                incidentRef(event.incidentNo()) + " exceeded the " + event.slaType().toLowerCase()
                        + " SLA by " + event.minutesOverdue() + " minute(s)."
        );
    }

    private String buildStatusMessage(String actorName, int incidentNo, String previousStatus, String newStatus, String reason) {
        String base = actorName + " transitioned " + incidentRef(incidentNo) + " from " + previousStatus + " to " + newStatus + ".";
        if (reason != null && !reason.isBlank()) {
            base += " Reason: " + reason;
        }
        return base;
    }

    private String resolveActor(String actorName) {
        return (actorName != null && !actorName.isBlank()) ? actorName : "Deactivated User";
    }
}
