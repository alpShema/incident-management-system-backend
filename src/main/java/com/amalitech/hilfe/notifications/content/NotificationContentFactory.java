package com.amalitech.hilfe.notifications.content;

import com.amalitech.hilfe.notifications.events.*;
import org.springframework.stereotype.Component;

@Component
public class NotificationContentFactory {

    public NotificationDraft from(IncidentAssignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_ASSIGNED",
                "Incident #" + event.incidentNo() + " assigned to you",
                "Incident #" + event.incidentNo() + " has been assigned to you."
        );
    }

    public NotificationDraft from(IncidentEscalatedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_ESCALATED",
                "Incident #" + event.incidentNo() + " requires attention",
                "Incident #" + event.incidentNo() + " could not be automatically assigned. Please review and assign it manually."
        );
    }

    public NotificationDraft from(IncidentStatusChangedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_STATUS_CHANGED",
                "Incident #" + event.incidentNo() + " status updated",
                buildStatusMessage(event.incidentNo(), event.previousStatus(), event.newStatus(), event.reason())
        );
    }

    public NotificationDraft from(IncidentPendingEvent event) {
        String message = "Incident #" + event.incidentNo() + " has been placed in Pending status."
                + (event.reason() != null && !event.reason().isBlank() ? " Reason: " + event.reason() : "");
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_PENDING",
                "Incident #" + event.incidentNo() + " is pending",
                message
        );
    }

    public NotificationDraft from(IncidentReopenedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_REOPENED",
                "Incident #" + event.incidentNo() + " has been reopened",
                "Incident #" + event.incidentNo() + " has been reopened and is now In Progress. Please review and take action."
        );
    }

    public NotificationDraft from(IncidentSeverityChangedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_PRIORITY_CHANGED",
                "Incident #" + event.incidentNo() + " priority updated",
                "The priority of Incident #" + event.incidentNo() + " has been changed from "
                        + event.previousSeverity() + " to " + event.newSeverity() + "."
        );
    }

    public NotificationDraft from(IncidentUnassignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_UNASSIGNED",
                "Incident #" + event.incidentNo() + " reassigned",
                "Incident #" + event.incidentNo() + " has been reassigned to another agent."
        );
    }

    public NotificationDraft from(IncidentAutoClosedAgentEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_CLOSED",
                "Incident #" + event.incidentNo() + " has been automatically closed",
                "Incident #" + event.incidentNo() + " was automatically closed by the system after the resolution window elapsed."
        );
    }

    public NotificationDraft from(IncidentAutoClosedClientEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_CLOSED_CLIENT",
                "Incident #" + event.incidentNo() + " has been closed",
                "Your Incident #" + event.incidentNo() + " has been automatically closed after the resolution period elapsed."
        );
    }

    public NotificationDraft from(IncidentAutoAssignedClientEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_AUTO_ASSIGNED_CLIENT",
                "Incident #" + event.incidentNo() + " is being handled",
                "An agent has been assigned to your Incident #" + event.incidentNo() + " and will be in touch shortly."
        );
    }

    public NotificationDraft from(IncidentClientReassignedEvent event) {
        return new NotificationDraft(
                event.recipientUserId(),
                event.incidentId(),
                "INCIDENT_REASSIGNED_CLIENT",
                "Incident #" + event.incidentNo() + " has a new agent",
                "A new agent has been assigned to Incident #" + event.incidentNo() + "."
        );
    }

    private String buildStatusMessage(int incidentNo, String previousStatus, String newStatus, String reason) {
        String base = "Incident #" + incidentNo + " has moved from " + previousStatus + " to " + newStatus + ".";
        if (reason != null && !reason.isBlank()) {
            base += " Reason: " + reason;
        }
        return base;
    }
}
