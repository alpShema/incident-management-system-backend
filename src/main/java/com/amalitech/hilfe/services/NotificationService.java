package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String NOT_FOUND_MSG = "Notification not found";

    private final NotificationRepository notificationRepository;

    public PageResponse<NotificationResponse> getNotifications(String userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                        .map(NotificationResponse::from)
        );
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ArmsAuthException(NOT_FOUND_MSG, 404));
        if (!notification.getUserId().equals(userId)) {
            throw new ArmsAuthException(NOT_FOUND_MSG, 404);
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    @Transactional
    public void clearAll(String userId) {
        notificationRepository.deleteAllByUserId(userId);
    }
}
