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

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReopenedNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " has been reopened";
            String message = "Incident #" + incidentNo + " has been reopened and is now In Progress. Please review and take action.";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_REOPENED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send reopened notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendPendingNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo,
            String reason
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " is pending";
            String message = "Incident #" + incidentNo + " has been placed in Pending status."
                    + (reason != null && !reason.isBlank() ? " Reason: " + reason : "");

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_PENDING")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send pending notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendUnassignedNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " reassigned";
            String message = "Incident #" + incidentNo + " has been reassigned to another agent.";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_UNASSIGNED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send unassigned notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAutoAssignedClientNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " is being handled";
            String message = "An agent has been assigned to your Incident #" + incidentNo + " and will be in touch shortly.";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_AUTO_ASSIGNED_CLIENT")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send auto-assignment client notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendSeverityChangedNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo,
            String previousSeverity,
            String newSeverity
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " priority updated";
            String message = "The priority of Incident #" + incidentNo + " has been changed from "
                    + previousSeverity + " to " + newSeverity + ".";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_PRIORITY_CHANGED")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send severity change notification to user {} for incident {}", recipientUserId, incidentId, ex);
        }
    }

    @Async("applicationTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendClientReassignedNotification(
            String recipientUserId,
            String incidentId,
            int incidentNo
    ) {
        if (recipientUserId == null) return;
        try {
            String title = "Incident #" + incidentNo + " has a new agent";
            String message = "A new agent has been assigned to Incident #" + incidentNo + ".";

            Notification notification = Notification.builder()
                    .userId(recipientUserId)
                    .incidentId(incidentId)
                    .type("INCIDENT_REASSIGNED_CLIENT")
                    .title(title)
                    .message(message)
                    .build();

            Notification saved = notificationRepository.save(notification);
            messagingTemplate.convertAndSend(
                    "/topic/users/" + recipientUserId + "/notifications",
                    NotificationResponse.from(saved)
            );
        } catch (Exception ex) {
            log.error("Failed to send client reassignment notification to user {} for incident {}", recipientUserId, incidentId, ex);
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
