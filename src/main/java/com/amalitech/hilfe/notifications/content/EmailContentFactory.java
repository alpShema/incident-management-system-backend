package com.amalitech.hilfe.notifications.content;

import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.notifications.events.*;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code {{subject}}}/{@code {{content}}}/{@code {{cta_text}}} tokens for the
 * base-email-template.html shell, per notification event type. Mirrors {@link NotificationContentFactory},
 * but needs the resolved {@link IncidentResponse} (title, topic, priority, status, assignee) since the
 * lightweight notification events only carry incidentNo, not the richer fields every email design shows.
 */
@Component
public class EmailContentFactory {

    private static final String CTA_TEXT = "View Incident";

    private static String ref(IncidentResponse incident) {
        return "#" + incident.incidentNo() + " — " + incident.title();
    }

    private static String priorityName(IncidentResponse incident) {
        return incident.priority() != null ? incident.priority().name() : "N/A";
    }

    private static String topicName(IncidentResponse incident) {
        return incident.incidentTopic() != null ? incident.incidentTopic().name() : "General";
    }

    private static String assigneeName(IncidentResponse incident) {
        return incident.assignedTo() != null ? incident.assignedTo().fullName() : "Unassigned";
    }

    private static String greeting(String recipientName) {
        return "Hello " + ((recipientName != null && !recipientName.isBlank()) ? recipientName : "there") + ",";
    }

    private static String formatMinutes(long minutes) {
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    private static String body(String recipientName, String... lines) {
        StringBuilder sb = new StringBuilder("<p>").append(greeting(recipientName)).append("</p><p>");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("<br>");
            sb.append(lines[i]);
        }
        return sb.append("</p>").toString();
    }

    // event carries no fields beyond incidentNo (already on incident) — kept only so overload
    // resolution can dispatch EmailNotificationListener's uniform from(event, incident, name) calls.
    @SuppressWarnings("java:S1172")
    public EmailContent from(IncidentAssignedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "New incident assigned — " + ref(incident),
                "A new incident has been assigned to you",
                body(recipientName,
                        "A new incident has been assigned to you.",
                        ref(incident),
                        "Topic: " + topicName(incident),
                        "Priority: " + priorityName(incident),
                        "Please review the details and respond. You can open the incident directly in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentUnassignedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Incident reassigned — " + ref(incident),
                "An incident has been reassigned to another agent",
                body(recipientName,
                        "An incident that was assigned to you has been reassigned to another agent.",
                        ref(incident),
                        "Now assigned to: " + event.newAssigneeName(),
                        "There's nothing further you need to do on this incident. You can view it in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentClientReassignedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Your incident was reassigned — " + ref(incident),
                "Your incident has a new agent",
                body(recipientName,
                        "Your incident has been reassigned to a new agent, who will continue working on it from here.",
                        ref(incident),
                        "Now assigned to: " + event.newAssigneeName(),
                        "There's nothing you need to do. You can follow the progress of your incident in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentAutoAssignedClientEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Your incident was assigned — " + ref(incident),
                "An agent has picked up your incident",
                body(recipientName,
                        "Good news, your incident has been assigned to an agent who will continue working on it.",
                        ref(incident),
                        "Assigned to: " + event.assigneeName(),
                        "You'll be notified as your incident progresses. You can view it in HILFE below."),
                CTA_TEXT
        );
    }

    // event carries no fields beyond incidentNo (already on incident) — kept only so overload
    // resolution can dispatch EmailNotificationListener's uniform from(event, incident, name) calls.
    @SuppressWarnings("java:S1172")
    public EmailContent from(IncidentEscalatedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "No agent available — " + ref(incident),
                "An incident needs manual assignment",
                body(recipientName,
                        "An incident has been raised but there's no available agent to handle it. It needs to be assigned to an available agent.",
                        ref(incident),
                        "Priority: " + priorityName(incident),
                        "You can view the incident in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentStatusChangedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Incident status updated — " + ref(incident),
                "The status of your incident has changed",
                body(recipientName,
                        "The status of your incident has been updated.",
                        ref(incident),
                        "Status: " + event.previousStatus() + " → " + event.newStatus(),
                        "You can view the incident and its full history in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentPendingEvent event, IncidentResponse incident, String recipientName) {
        String reason = (event.reason() != null && !event.reason().isBlank()) ? event.reason() : "Awaiting more information";
        return new EmailContent(
                "Incident on hold — " + ref(incident),
                "Your incident status has been updated to Pending",
                body(recipientName,
                        "The status of your incident has been updated to Pending.",
                        ref(incident),
                        "Reason: " + reason,
                        "Once this is resolved, your agent will continue working on it. You can respond or view in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentReopenedEvent event, IncidentResponse incident, String recipientName) {
        String reason = (incident.statusReason() != null && !incident.statusReason().isBlank())
                ? incident.statusReason() : "Issue still persists";
        String reopenedBy = (event.actorName() != null && !event.actorName().isBlank()) ? event.actorName() : "the client";
        return new EmailContent(
                "Incident reopened — " + ref(incident),
                "A resolved incident has been reopened",
                body(recipientName,
                        "A resolved incident has been reopened by the client and is back on your plate.",
                        ref(incident),
                        "Reopened by: " + reopenedBy,
                        "Reason: " + reason,
                        "Please take another look and follow up. You can open it in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentSeverityChangedEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Priority changed — " + ref(incident),
                "The priority of an incident has changed",
                body(recipientName,
                        "The priority of an incident has been changed.",
                        ref(incident),
                        "Priority: " + event.previousSeverity() + " → " + event.newSeverity(),
                        "You can view the incident in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(NewIncidentMessageEvent event, IncidentResponse incident, String recipientName) {
        String preview = (event.preview() != null && !event.preview().isBlank()) ? event.preview() : "[attachment]";
        return new EmailContent(
                "New message — " + ref(incident),
                "You have a new reply on an incident",
                body(recipientName,
                        "You have a new reply on an incident.",
                        ref(incident),
                        "Priority: " + priorityName(incident),
                        event.senderName() + ": \"" + preview + "\"",
                        "You can read the full conversation and reply in HILFE below."),
                CTA_TEXT
        );
    }

    // event carries no fields beyond incidentNo (already on incident) — kept only so overload
    // resolution can dispatch EmailNotificationListener's uniform from(event, incident, name) calls.
    @SuppressWarnings("java:S1172")
    public EmailContent from(IncidentAutoClosedClientEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Incident closed — " + ref(incident),
                "Your resolved incident was closed automatically",
                body(recipientName,
                        "Your resolved incident has been closed automatically, as the reopen window has now passed.",
                        ref(incident),
                        "If you have further issues, you're welcome to raise a new incident. You can view the closed incident in HILFE below."),
                CTA_TEXT
        );
    }

    // event carries no fields beyond incidentNo (already on incident) — kept only so overload
    // resolution can dispatch EmailNotificationListener's uniform from(event, incident, name) calls.
    @SuppressWarnings("java:S1172")
    public EmailContent from(IncidentAutoClosedAgentEvent event, IncidentResponse incident, String recipientName) {
        return new EmailContent(
                "Incident closed — " + ref(incident),
                "A resolved incident has been closed automatically",
                body(recipientName,
                        "A resolved incident has been closed automatically, as the reopen window has now passed.",
                        ref(incident),
                        "You can view the incident in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentSlaAtRiskEvent event, IncidentResponse incident, String recipientName) {
        boolean isResponse = "RESPONSE".equalsIgnoreCase(event.slaType());
        String metric = isResponse ? "response" : "resolution";
        String action = isResponse ? "respond" : "work to resolve it";
        return new EmailContent(
                (isResponse ? "Response" : "Resolution") + " time warning — " + ref(incident),
                "An incident is approaching its " + metric + " deadline",
                body(recipientName,
                        "An incident is approaching its " + metric + " deadline and is now at risk of breaching its SLA (Service Level Agreement).",
                        ref(incident),
                        "Priority: " + priorityName(incident),
                        (isResponse ? "Response" : "Resolution") + " due: in " + formatMinutes(event.minutesRemaining()),
                        "Please " + action + " soon to keep it on track. Open it in HILFE below."),
                CTA_TEXT
        );
    }

    public EmailContent from(IncidentSlaBreachedEvent event, IncidentResponse incident, String recipientName) {
        boolean isResponse = "RESPONSE".equalsIgnoreCase(event.slaType());
        String metric = isResponse ? "response" : "resolution";
        String action = isResponse ? "respond as soon as possible" : "prioritise resolving this";
        return new EmailContent(
                (isResponse ? "Response" : "Resolution") + " deadline missed — " + ref(incident),
                "An incident has breached its " + metric + " SLA",
                body(recipientName,
                        "The " + metric + " deadline for an incident has passed, and its " + metric + " SLA (Service Level Agreement) has been breached.",
                        ref(incident),
                        "Priority: " + priorityName(incident),
                        (isResponse ? "Response" : "Resolution") + " was due: " + formatMinutes(event.minutesOverdue()) + " ago",
                        "Please " + action + ". The breach has been recorded for reporting. Open it in HILFE below."),
                CTA_TEXT
        );
    }
}
