package com.amalitech.hilfe.notifications.delivery;

import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.models.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(Notification notification) {
        messagingTemplate.convertAndSend(
                "/topic/users/" + notification.getUserId() + "/notifications",
                NotificationResponse.from(notification)
        );
    }
}
