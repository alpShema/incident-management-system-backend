package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.models.Notification;
import com.amalitech.hilfe.models.SlackUserMapping;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.amalitech.hilfe.slack.service.SlackNotificationBroadcaster;
import com.amalitech.hilfe.slack.service.SlackNotificationPreferenceService;
import com.amalitech.hilfe.slack.service.SlackOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlackNotificationBroadcasterTest {

    @Mock private SlackClient slackClient;
    @Mock private SlackOAuthService oauthService;
    @Mock private SlackNotificationPreferenceService preferenceService;
    @Mock private SlackAuditLogService auditLogService;
    @Mock private SlackProperties slackProperties;

    @InjectMocks
    private SlackNotificationBroadcaster broadcaster;

    private Notification notification;
    private SlackUserMapping mapping;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .id("notif-1")
                .userId("hilfe-user-1")
                .type("INCIDENT_ASSIGNED")
                .title("Incident Assigned")
                .message("You have been assigned INC-101")
                .incidentId("inc-1")
                .build();

        mapping = SlackUserMapping.builder()
                .slackUserId("U_SLACK_001")
                .hilfeUserId("hilfe-user-1")
                .build();
    }

    @Test
    void broadcast_whenNotificationsDisabled_skipsEverything() {
        when(slackProperties.notificationsEnabled()).thenReturn(false);

        broadcaster.broadcast(notification);

        verifyNoInteractions(oauthService, slackClient, preferenceService);
    }

    @Test
    void broadcast_whenUserNotConnectedToSlack_skipsMessage() {
        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.empty());

        broadcaster.broadcast(notification);

        verifyNoInteractions(slackClient);
    }

    @Test
    void broadcast_whenPreferenceDisabledForType_skipsMessage() {
        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.of(mapping));
        when(preferenceService.isEnabled("hilfe-user-1", "INCIDENT_ASSIGNED")).thenReturn(false);

        broadcaster.broadcast(notification);

        verifyNoInteractions(slackClient);
    }

    @Test
    void broadcast_allConditionsMet_sendsMessageAndLogsAudit() {
        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.of(mapping));
        when(preferenceService.isEnabled("hilfe-user-1", "INCIDENT_ASSIGNED")).thenReturn(true);

        broadcaster.broadcast(notification);

        verify(slackClient).chatPostMessage(eq("U_SLACK_001"),
                argThat(msg -> msg.contains("Incident Assigned") && msg.contains("INC-101")));
        verify(auditLogService).log(eq("NOTIFICATION_SENT"), eq("U_SLACK_001"),
                eq("hilfe-user-1"), eq("NOTIFICATION"), eq("notif-1"), any());
    }

    @Test
    void broadcast_slackClientThrows_logsFailureAuditWithoutPropagating() {
        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.of(mapping));
        when(preferenceService.isEnabled("hilfe-user-1", "INCIDENT_ASSIGNED")).thenReturn(true);
        doThrow(new RuntimeException("Slack API unavailable"))
                .when(slackClient).chatPostMessage(anyString(), anyString());

        broadcaster.broadcast(notification);

        verify(auditLogService).log(eq("NOTIFICATION_SEND_FAILED"), eq("U_SLACK_001"),
                eq("hilfe-user-1"), eq("NOTIFICATION"), eq("notif-1"), any());
    }

    @Test
    void broadcast_noIncidentId_messageExcludesViewLink() {
        notification = Notification.builder()
                .id("notif-2").userId("hilfe-user-1")
                .type("INCIDENT_ASSIGNED").title("Notice").message("Something happened")
                .incidentId(null).build();

        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.of(mapping));
        when(preferenceService.isEnabled("hilfe-user-1", "INCIDENT_ASSIGNED")).thenReturn(true);

        broadcaster.broadcast(notification);

        verify(slackClient).chatPostMessage(eq("U_SLACK_001"),
                argThat(msg -> !msg.contains("View in HILFE")));
    }

    @ParameterizedTest
    @CsvSource({
            "INCIDENT_ASSIGNED,           :inbox_tray:",
            "INCIDENT_ESCALATED,          :rotating_light:",
            "INCIDENT_STATUS_CHANGED,     :arrows_counterclockwise:",
            "INCIDENT_PENDING,            :hourglass_flowing_sand:",
            "INCIDENT_REOPENED,           :arrows_counterclockwise:",
            "INCIDENT_PRIORITY_CHANGED,   :small_orange_diamond:",
            "INCIDENT_UNASSIGNED,         :outbox_tray:",
            "INCIDENT_AUTO_CLOSED,        :white_check_mark:",
            "INCIDENT_AUTO_CLOSED_CLIENT, :white_check_mark:",
            "INCIDENT_AUTO_ASSIGNED_CLIENT,:handshake:",
            "INCIDENT_REASSIGNED_CLIENT,  :arrows_counterclockwise:",
            "INCIDENT_SLA_AT_RISK,        :warning:",
            "INCIDENT_SLA_BREACHED,       :warning:"
    })
    void broadcast_notificationType_usesExpectedEmoji(String type, String expectedEmoji) {
        String trimmedType = type.trim();
        String trimmedEmoji = expectedEmoji.trim();

        notification = Notification.builder()
                .id("notif-emoji").userId("hilfe-user-1")
                .type(trimmedType).title("Test").message("Test message")
                .incidentId("inc-1").build();

        when(slackProperties.notificationsEnabled()).thenReturn(true);
        when(oauthService.findByHilfeUserId("hilfe-user-1")).thenReturn(Optional.of(mapping));
        when(preferenceService.isEnabled("hilfe-user-1", trimmedType)).thenReturn(true);

        broadcaster.broadcast(notification);

        verify(slackClient).chatPostMessage(eq("U_SLACK_001"),
                argThat(msg -> msg.contains(trimmedEmoji)));
    }
}
