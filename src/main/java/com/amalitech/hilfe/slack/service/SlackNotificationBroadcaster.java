package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.slack.client.SlackClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class SlackNotificationBroadcaster {

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final SlackNotificationPreferenceService preferenceService;
    private final SlackAuditLogService auditLogService;
    private final SlackProperties slackProperties;

    private static final String HILFE_WEB_URL = "https://hilfe.amalitech.net";
    private static final String EMOJI_CYCLE = ":arrows_counterclockwise:";

    @Async
    public void broadcast(Notification notification) {
        if (!slackProperties.notificationsEnabled()) {
            log.debug("Slack notifications disabled");
            return;
        }

        Optional<SlackUserMapping> mapping = oauthService.findByHilfeUserId(notification.getUserId());
        if (mapping.isEmpty()) {
            log.debug("User {} not connected to Slack, skipping notification", notification.getUserId());
            return;
        }

        if (!preferenceService.isEnabled(notification.getUserId(), notification.getType())) {
            log.debug("Notification type {} disabled for user {}", notification.getType(), notification.getUserId());
            return;
        }

        String message = buildSlackMessage(notification);

        try {
            slackClient.chatPostMessage(mapping.get().getSlackUserId(), message);

            auditLogService.log(
                    "NOTIFICATION_SENT",
                    mapping.get().getSlackUserId(),
                    notification.getUserId(),
                    "NOTIFICATION",
                    notification.getId(),
                    Map.of("type", notification.getType())
            );

            log.debug("Slack notification sent to user {} for type {}",
                    notification.getUserId(), notification.getType());

        } catch (Exception e) {
            log.error("Failed to send Slack notification to user {}", notification.getUserId(), e);

            auditLogService.log(
                    "NOTIFICATION_SEND_FAILED",
                    mapping.get().getSlackUserId(),
                    notification.getUserId(),
                    "NOTIFICATION",
                    notification.getId(),
                    Map.of("type", notification.getType(), "error", e.getMessage())
            );
        }
    }

    private String buildSlackMessage(Notification notification) {
        String emoji = getNotificationEmoji(notification.getType());
        String link = notification.getIncidentId() != null
                ? "\n<" + HILFE_WEB_URL + "/incidents/" + notification.getIncidentId() + "|View in HILFE>"
                : "";

        return emoji + " *" + notification.getTitle() + "*\n" +
                notification.getMessage() + link;
    }

    private String getNotificationEmoji(String type) {
        return switch (type) {
            case "INCIDENT_ASSIGNED"             -> ":inbox_tray:";
            case "INCIDENT_ESCALATED"            -> ":rotating_light:";
            case "INCIDENT_STATUS_CHANGED"       -> EMOJI_CYCLE;
            case "INCIDENT_PENDING"              -> ":hourglass_flowing_sand:";
            case "INCIDENT_REOPENED"             -> EMOJI_CYCLE;
            case "INCIDENT_PRIORITY_CHANGED"     -> ":small_orange_diamond:";
            case "INCIDENT_UNASSIGNED"           -> ":outbox_tray:";
            case "INCIDENT_AUTO_CLOSED"          -> ":white_check_mark:";
            case "INCIDENT_AUTO_CLOSED_CLIENT"   -> ":white_check_mark:";
            case "INCIDENT_AUTO_ASSIGNED_CLIENT" -> ":handshake:";
            case "INCIDENT_REASSIGNED_CLIENT"    -> EMOJI_CYCLE;
            case "INCIDENT_SLA_AT_RISK"          -> ":warning:";
            case "INCIDENT_SLA_BREACHED"         -> ":warning:";
            default                              -> ":bell:";
        };
    }
}
