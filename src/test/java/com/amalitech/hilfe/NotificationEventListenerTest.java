package com.amalitech.hilfe;

import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.notifications.content.NotificationContentFactory;
import com.amalitech.hilfe.notifications.content.NotificationDraft;
import com.amalitech.hilfe.notifications.delivery.NotificationBroadcaster;
import com.amalitech.hilfe.notifications.events.IncidentAssignedEvent;
import com.amalitech.hilfe.notifications.events.IncidentSlaAtRiskEvent;
import com.amalitech.hilfe.notifications.listener.NotificationEventListener;
import com.amalitech.hilfe.notifications.persistence.NotificationPersistenceService;
import com.amalitech.hilfe.slack.service.SlackNotificationBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock NotificationContentFactory contentFactory;
    @Mock NotificationPersistenceService persistenceService;
    @Mock NotificationBroadcaster broadcaster;

    NotificationEventListener listener;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        ObjectProvider<SlackNotificationBroadcaster> slackProvider = mock(ObjectProvider.class);
        when(slackProvider.getIfAvailable()).thenReturn(null);
        listener = new NotificationEventListener(contentFactory, persistenceService, broadcaster, slackProvider);
    }

    @Test
    void assignedEvent_persistsAndBroadcasts() {
        IncidentAssignedEvent event = new IncidentAssignedEvent("user-1", "inc-1", 5, "System");
        NotificationDraft draft = new NotificationDraft("user-1", "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message");
        Notification saved = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .incidentId("inc-1")
                .type("INCIDENT_ASSIGNED")
                .title("Assigned")
                .message("Assigned message")
                .build();

        when(contentFactory.from(event)).thenReturn(draft);
        when(persistenceService.save(draft)).thenReturn(saved);

        listener.onIncidentAssigned(event);

        verify(persistenceService).save(draft);
        verify(broadcaster).broadcast(saved);
    }

    @Test
    void assignedEvent_withNullRecipient_skipsPersistenceAndBroadcast() {
        IncidentAssignedEvent event = new IncidentAssignedEvent(null, "inc-1", 5, "System");
        NotificationDraft draft = new NotificationDraft(null, "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message");

        when(contentFactory.from(event)).thenReturn(draft);

        listener.onIncidentAssigned(event);

        verify(contentFactory).from(event);
        verifyNoInteractions(persistenceService, broadcaster);
    }

    @Test
    void assignedEvent_persistenceFailure_isSwallowed() {
        IncidentAssignedEvent event = new IncidentAssignedEvent("user-1", "inc-1", 5, "System");
        NotificationDraft draft = new NotificationDraft("user-1", "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message");

        when(contentFactory.from(event)).thenReturn(draft);
        when(persistenceService.save(draft)).thenThrow(new RuntimeException("db failed"));

        listener.onIncidentAssigned(event);

        verify(persistenceService).save(draft);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void assignedEvent_broadcastFailure_isSwallowedAfterPersistence() {
        IncidentAssignedEvent event = new IncidentAssignedEvent("user-1", "inc-1", 5, "System");
        NotificationDraft draft = new NotificationDraft("user-1", "inc-1", "INCIDENT_ASSIGNED", "Assigned", "Assigned message");
        Notification saved = Notification.builder().id("notif-1").userId("user-1").incidentId("inc-1").build();

        when(contentFactory.from(event)).thenReturn(draft);
        when(persistenceService.save(draft)).thenReturn(saved);
        doThrow(new RuntimeException("ws failed")).when(broadcaster).broadcast(saved);

        listener.onIncidentAssigned(event);

        verify(persistenceService).save(draft);
        verify(broadcaster).broadcast(saved);
    }

    @Test
    void slaAtRiskEvent_persistsAndBroadcasts() {
        IncidentSlaAtRiskEvent event = new IncidentSlaAtRiskEvent("user-1", "inc-1", 5, "RESPONSE", 8);
        NotificationDraft draft = new NotificationDraft("user-1", "inc-1", "INCIDENT_SLA_AT_RISK", "At risk", "At risk message");
        Notification saved = Notification.builder().id("notif-2").userId("user-1").incidentId("inc-1").type("INCIDENT_SLA_AT_RISK").build();

        when(contentFactory.from(event)).thenReturn(draft);
        when(persistenceService.save(draft)).thenReturn(saved);

        listener.onIncidentSlaAtRisk(event);

        verify(persistenceService).save(draft);
        verify(broadcaster).broadcast(saved);
    }
}
