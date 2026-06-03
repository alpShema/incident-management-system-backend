package com.amalitech.hilfe.notifications.persistence;

import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.notifications.content.NotificationDraft;
import com.amalitech.hilfe.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;

    public Notification save(NotificationDraft draft) {
        Notification notification = Notification.builder()
                .userId(draft.userId())
                .incidentId(draft.incidentId())
                .type(draft.type())
                .title(draft.title())
                .message(draft.message())
                .build();
        return notificationRepository.save(notification);
    }
}
