package com.amalitech.hilfe;

import com.amalitech.hilfe.notifications.content.NotificationContentFactory;
import com.amalitech.hilfe.notifications.content.NotificationDraft;
import com.amalitech.hilfe.notifications.events.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentFactoryTest {

    private final NotificationContentFactory factory = new NotificationContentFactory();

    @Test
    void buildsAssignedNotificationContent_withActorName() {
        NotificationDraft draft = factory.from(new IncidentAssignedEvent("user-1", "inc-1", 12, "Isabella Wong"));

        assertThat(draft.userId()).isEqualTo("user-1");
        assertThat(draft.incidentId()).isEqualTo("inc-1");
        assertThat(draft.type()).isEqualTo("INCIDENT_ASSIGNED");
        assertThat(draft.title()).isEqualTo("Incident #12 assigned to you");
        assertThat(draft.message()).isEqualTo("Isabella Wong assigned Incident #12 to you.");
    }

    @Test
    void buildsAssignedNotificationContent_systemActor() {
        NotificationDraft draft = factory.from(new IncidentAssignedEvent("user-1", "inc-1", 5, "System"));

        assertThat(draft.message()).isEqualTo("System assigned Incident #5 to you.");
    }

    @Test
    void buildsUnassignedNotificationContent_withActorAndNewAssigneeName() {
        NotificationDraft draft = factory.from(new IncidentUnassignedEvent("prev-agent", "inc-1", 2, "Isabella Wong", "Elena Costa"));

        assertThat(draft.type()).isEqualTo("INCIDENT_UNASSIGNED");
        assertThat(draft.message()).isEqualTo("Isabella Wong reassigned Incident #2 from you to Elena Costa.");
    }

    @Test
    void buildsClientReassignedNotificationContent_withActorAndNewAssigneeName() {
        NotificationDraft draft = factory.from(new IncidentClientReassignedEvent("client-1", "inc-1", 2, "Isabella Wong", "Elena Costa"));

        assertThat(draft.type()).isEqualTo("INCIDENT_REASSIGNED_CLIENT");
        assertThat(draft.message()).isEqualTo("Isabella Wong reassigned Incident #2 to Elena Costa.");
    }

    @Test
    void buildsPendingNotificationContent_withActorAndReason() {
        NotificationDraft draft = factory.from(new IncidentPendingEvent("user-1", "inc-1", 12, "Waiting for parts", "John Smith"));

        assertThat(draft.type()).isEqualTo("INCIDENT_PENDING");
        assertThat(draft.title()).isEqualTo("Incident #12 is pending");
        assertThat(draft.message()).isEqualTo("John Smith placed Incident #12 in Pending status. Reason: Waiting for parts");
    }

    @Test
    void buildsPendingNotificationContent_noReason_omitsReasonClause() {
        NotificationDraft draft = factory.from(new IncidentPendingEvent("user-1", "inc-1", 12, null, "John Smith"));

        assertThat(draft.message()).isEqualTo("John Smith placed Incident #12 in Pending status.");
    }

    @Test
    void buildsStatusChangedNotificationContent_withActorName() {
        NotificationDraft draft = factory.from(new IncidentStatusChangedEvent("user-1", "inc-1", 12, "Open", "Resolved", "Fixed", "Mia Nakamura"));

        assertThat(draft.type()).isEqualTo("INCIDENT_STATUS_CHANGED");
        assertThat(draft.title()).isEqualTo("Incident #12 status updated");
        assertThat(draft.message()).isEqualTo("Mia Nakamura transitioned Incident #12 from Open to Resolved. Reason: Fixed");
    }

    @Test
    void buildsStatusChangedNotificationContent_noReason() {
        NotificationDraft draft = factory.from(new IncidentStatusChangedEvent("user-1", "inc-1", 3, "Open", "In Progress", null, "Mia Nakamura"));

        assertThat(draft.message()).isEqualTo("Mia Nakamura transitioned Incident #3 from Open to In Progress.");
    }

    @Test
    void buildsSeverityChangedNotificationContent_withActorName() {
        NotificationDraft draft = factory.from(new IncidentSeverityChangedEvent("user-1", "inc-1", 12, "Low", "High", "Elena Costa"));

        assertThat(draft.type()).isEqualTo("INCIDENT_PRIORITY_CHANGED");
        assertThat(draft.title()).isEqualTo("Incident #12 priority updated");
        assertThat(draft.message()).isEqualTo("Elena Costa changed the priority of Incident #12 from Low to High.");
    }

    @Test
    void buildsReopenedNotificationContent_withActorName() {
        NotificationDraft draft = factory.from(new IncidentReopenedEvent("agent-1", "inc-1", 9, "Mia Nakamura"));

        assertThat(draft.type()).isEqualTo("INCIDENT_REOPENED");
        assertThat(draft.message()).startsWith("Mia Nakamura reopened Incident #9");
    }

    @Test
    void buildsSystemTriggeredNotificationContent_noActorParam() {
        assertThat(factory.from(new IncidentEscalatedEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_ESCALATED");
        assertThat(factory.from(new IncidentAutoClosedAgentEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_AUTO_CLOSED");
        assertThat(factory.from(new IncidentAutoClosedClientEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_AUTO_CLOSED_CLIENT");
        assertThat(factory.from(new IncidentAutoAssignedClientEvent("user-1", "inc-1", 12, "Jane Doe")).type()).isEqualTo("INCIDENT_AUTO_ASSIGNED_CLIENT");
    }

    @Test
    void buildsEscalatedNotification_containsSystemActor() {
        NotificationDraft draft = factory.from(new IncidentEscalatedEvent("user-1", "inc-1", 4));
        assertThat(draft.message()).startsWith("System was unable to automatically assign Incident #4");
    }

    @Test
    void buildsAutoClosedAgentNotification_containsSystemActor() {
        NotificationDraft draft = factory.from(new IncidentAutoClosedAgentEvent("user-1", "inc-1", 7));
        assertThat(draft.message()).isEqualTo("System automatically closed Incident #7 after the resolution window elapsed.");
    }

    @Test
    void buildsAutoClosedClientNotification_containsSystemActor() {
        NotificationDraft draft = factory.from(new IncidentAutoClosedClientEvent("user-1", "inc-1", 7));
        assertThat(draft.message()).isEqualTo("System automatically closed Incident #7 after the resolution period elapsed.");
    }

    @Test
    void buildsNotification_nullActorName_fallsBackToDeactivatedUser() {
        NotificationDraft draft = factory.from(new IncidentReopenedEvent("agent-1", "inc-1", 9, null));
        assertThat(draft.message()).startsWith("Deactivated User reopened Incident #9");
    }
}
