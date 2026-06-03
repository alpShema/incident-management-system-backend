package com.amalitech.hilfe;

import com.amalitech.hilfe.notifications.content.NotificationContentFactory;
import com.amalitech.hilfe.notifications.content.NotificationDraft;
import com.amalitech.hilfe.notifications.events.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentFactoryTest {

    private final NotificationContentFactory factory = new NotificationContentFactory();

    @Test
    void buildsAssignedNotificationContent() {
        NotificationDraft draft = factory.from(new IncidentAssignedEvent("user-1", "inc-1", 12));

        assertThat(draft.userId()).isEqualTo("user-1");
        assertThat(draft.incidentId()).isEqualTo("inc-1");
        assertThat(draft.type()).isEqualTo("INCIDENT_ASSIGNED");
        assertThat(draft.title()).isEqualTo("Incident #12 assigned to you");
        assertThat(draft.message()).isEqualTo("Incident #12 has been assigned to you.");
    }

    @Test
    void buildsPendingNotificationContent() {
        NotificationDraft draft = factory.from(new IncidentPendingEvent("user-1", "inc-1", 12, "Waiting for parts"));

        assertThat(draft.type()).isEqualTo("INCIDENT_PENDING");
        assertThat(draft.title()).isEqualTo("Incident #12 is pending");
        assertThat(draft.message()).isEqualTo("Incident #12 has been placed in Pending status. Reason: Waiting for parts");
    }

    @Test
    void buildsStatusChangedNotificationContent() {
        NotificationDraft draft = factory.from(new IncidentStatusChangedEvent("user-1", "inc-1", 12, "Open", "Resolved", "Fixed"));

        assertThat(draft.type()).isEqualTo("INCIDENT_STATUS_CHANGED");
        assertThat(draft.title()).isEqualTo("Incident #12 status updated");
        assertThat(draft.message()).isEqualTo("Incident #12 has moved from Open to Resolved. Reason: Fixed");
    }

    @Test
    void buildsSeverityChangedNotificationContent() {
        NotificationDraft draft = factory.from(new IncidentSeverityChangedEvent("user-1", "inc-1", 12, "Low", "High"));

        assertThat(draft.type()).isEqualTo("INCIDENT_PRIORITY_CHANGED");
        assertThat(draft.title()).isEqualTo("Incident #12 priority updated");
        assertThat(draft.message()).isEqualTo("The priority of Incident #12 has been changed from Low to High.");
    }

    @Test
    void buildsRemainingNotificationContentTypes() {
        assertThat(factory.from(new IncidentEscalatedEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_ESCALATED");
        assertThat(factory.from(new IncidentReopenedEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_REOPENED");
        assertThat(factory.from(new IncidentUnassignedEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_UNASSIGNED");
        assertThat(factory.from(new IncidentAutoClosedAgentEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_AUTO_CLOSED");
        assertThat(factory.from(new IncidentAutoClosedClientEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_AUTO_CLOSED_CLIENT");
        assertThat(factory.from(new IncidentAutoAssignedClientEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_AUTO_ASSIGNED_CLIENT");
        assertThat(factory.from(new IncidentClientReassignedEvent("user-1", "inc-1", 12)).type()).isEqualTo("INCIDENT_REASSIGNED_CLIENT");
    }
}
