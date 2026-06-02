package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.NotificationResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.repositories.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendStatusChangeNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo,
            String previousStatus,
            String newStatus,
            String reason
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " status updated";
            String message = buildMessage(incidentNo, previousStatus, newStatus, reason);

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_STATUS_CHANGED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send status change notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAssignmentNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " assigned to you";
            String message = "Incident #" + incidentNo + " has been assigned to you.";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_ASSIGNED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send assignment notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendEscalationNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " requires attention";
            String message = "Incident #" + incidentNo + " could not be automatically assigned. Please review and assign it manually.";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_ESCALATED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send escalation notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    public List<NotificationResponse> getNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ArmsAuthException("Notification not found", 404));
        if (!notification.getUserId().equals(userId)) {
            throw new ArmsAuthException("Notification not found", 404);
        }
        notification.setRead(true);
        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    private String buildMessage(int incidentNo, String previousStatus, String newStatus, String reason) {
        String base = "Incident #" + incidentNo + " has moved from " + previousStatus + " to " + newStatus + ".";
        if (reason != null && !reason.isBlank()) {
            base += " Reason: " + reason;
        }
        return base;
    }
}
