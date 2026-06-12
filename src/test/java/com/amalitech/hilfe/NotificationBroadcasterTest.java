package com.amalitech.hilfe;

import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.notifications.delivery.NotificationBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationBroadcasterTest {

    @Mock SimpMessagingTemplate messagingTemplate;

    @InjectMocks NotificationBroadcaster broadcaster;

    @Test
    void broadcast_sendsNotificationResponseToUserTopic() {
        Notification notification = Notification.builder()
                .id("notif-1")
                .userId("user-1")
                .incidentId("inc-1")
                .type("INCIDENT_ASSIGNED")
                .title("Assigned")
                .message("Assigned message")
                .build();

        broadcaster.broadcast(notification);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/users/user-1/notifications"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).hasFieldOrPropertyWithValue("id", "notif-1");
        assertThat(payloadCaptor.getValue()).hasFieldOrPropertyWithValue("incidentId", "inc-1");
        assertThat(payloadCaptor.getValue()).hasFieldOrPropertyWithValue("type", "INCIDENT_ASSIGNED");
    }
}
