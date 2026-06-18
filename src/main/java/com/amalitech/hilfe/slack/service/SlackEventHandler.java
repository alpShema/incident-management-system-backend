package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class SlackEventHandler {

    private static final String CONNECT_FIRST_MSG = "Please connect your account first with `/hilfe connect`.";
    private static final String HELP_RESPONSE = """
            {
                "response_type": "ephemeral",
                "blocks": [
                    {
                        "type": "header",
                        "text": {
                            "type": "plain_text",
                            "text": "HILFE Bot Commands"
                        }
                    },
                    {
                        "type": "section",
                        "text": {
                            "type": "mrkdwn",
                            "text": "*Available commands:*\\n\\n`/hilfe connect` - Connect your HILFE account\\n`/hilfe disconnect` - Disconnect your account\\n`/hilfe new` - Create a new incident\\n`/hilfe my` - View your incidents\\n`/hilfe assigned` - View assigned incidents (agents only)\\n`/hilfe settings` - Notification settings\\n`/hilfe help` - Show this help message"
                        }
                    },
                    {
                        "type": "context",
                        "elements": [
                            {
                                "type": "mrkdwn",
                                "text": "You can also use the *Home* tab for quick access to all features."
                            }
                        ]
                    }
                ]
            }
            """;

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final SlackAuditLogService auditLogService;
    private final AppHomeService appHomeService;
    private final IncidentModalService incidentModalService;
    private final SlackNotificationPreferenceService preferenceService;
    private final SlackProperties slackProperties;

    public void handleEvent(String eventType, JsonNode event) {
        String userId = event.path("user").asText();

        switch (eventType) {
            case "app_home_opened" -> handleAppHomeOpened(userId);
            case "message" -> handleDirectMessage(event);
            default -> log.debug("Unhandled event type: {}", eventType);
        }
    }

    public String handleCommand(String text, String userId,
                                 String triggerId, String teamId) {
        // Command is /hilfe, text is the subcommand
        String subcommand = text.isEmpty() ? "help" : text.split("\\s+")[0].toLowerCase();

        return switch (subcommand) {
            case "connect" -> handleConnectCommand(userId, teamId);
            case "disconnect" -> handleDisconnectCommand(userId);
            case "settings" -> handleSettingsCommand(userId);
            case "new", "create" -> handleNewIncidentCommand(userId, triggerId);
            case "my", "list" -> handleMyIncidentsCommand(userId, triggerId);
            case "assigned" -> handleAssignedIncidentsCommand(userId, triggerId);
            default -> HELP_RESPONSE;
        };
    }

    public String handleInteraction(String type, JsonNode payload) {
        return switch (type) {
            case "block_actions" -> handleBlockActions(payload);
            case "view_submission" -> handleViewSubmission(payload);
            case "view_closed" -> handleViewClosed(payload);
            default -> {
                log.debug("Unhandled interaction type: {}", type);
                yield "";
            }
        };
    }

    private void handleAppHomeOpened(String userId) {
        try {
            appHomeService.publishAppHome(userId);
        } catch (Exception e) {
            log.error("Failed to publish app home for user {}", userId, e);
        }
    }

    private void handleDirectMessage(JsonNode event) {
        String userId = event.path("user").asText();
        String text = event.path("text").asText();
        String channelId = event.path("channel").asText();

        // Ignore bot messages
        if (event.has("bot_id")) {
            return;
        }

        log.debug("DM received from {}: {}", userId, text);

        // Simple auto-response pointing to slash commands
        if (!oauthService.isConnected(userId)) {
            slackClient.chatPostMessage(channelId,
                    "Hi! To use the HILFE bot, please connect your account first using `/hilfe connect`.");
        } else {
            slackClient.chatPostMessage(channelId,
                    "Hi! Use `/hilfe help` to see available commands, or click *Add New Incident* in the Home tab.");
        }
    }

    private String handleConnectCommand(String userId, String teamId) {
        if (oauthService.isConnected(userId)) {
            return buildEphemeralResponse(
                    "You're already connected to HILFE! Use `/hilfe disconnect` if you want to reconnect.");
        }

        String state = oauthService.generateOAuthState(userId, teamId);
        String oauthUrl = oauthService.buildOAuthUrl(state);

        auditLogService.log("CONNECT_INITIATED", userId, null, "OAUTH", null, Map.of("teamId", teamId));

        return """
                {
                    "response_type": "ephemeral",
                    "blocks": [
                        {
                            "type": "section",
                            "text": {
                                "type": "mrkdwn",
                                "text": "Click the button below to connect your HILFE account:"
                            }
                        },
                        {
                            "type": "actions",
                            "elements": [
                                {
                                    "type": "button",
                                    "text": {
                                        "type": "plain_text",
                                        "text": "Connect to HILFE"
                                    },
                                    "url": "%s",
                                    "style": "primary"
                                }
                            ]
                        }
                    ]
                }
                """.formatted(oauthUrl);
    }

    private String handleDisconnectCommand(String userId) {
        if (!oauthService.isConnected(userId)) {
            return buildEphemeralResponse("You're not connected to HILFE. Use `/hilfe connect` to connect.");
        }

        oauthService.disconnectUser(userId);
        appHomeService.publishAppHome(userId);

        return buildEphemeralResponse("Successfully disconnected from HILFE. Use `/hilfe connect` to reconnect.");
    }

    private String handleSettingsCommand(String userId) {
        if (!oauthService.isConnected(userId)) {
            return buildEphemeralResponse(CONNECT_FIRST_MSG);
        }

        return buildEphemeralResponse(
                "Open the *Home* tab to toggle your Slack notification preferences.");
    }

    private void handleNotificationToggle(String slackUserId, String actionId) {
        String type = actionId.substring("toggle_notif_".length());
        oauthService.findBySlackUserId(slackUserId).ifPresent(mapping -> {
            String hilfeUserId = mapping.getHilfeUserId();
            boolean current = preferenceService.isEnabled(hilfeUserId, type);
            preferenceService.updatePreference(hilfeUserId, type, !current);
            appHomeService.publishAppHome(slackUserId);
        });
    }

    private String handleNewIncidentCommand(String userId, String triggerId) {
        if (!oauthService.isConnected(userId)) {
            return buildEphemeralResponse(CONNECT_FIRST_MSG);
        }

        try {
            incidentModalService.openCreateIncidentModal(userId, triggerId);
            return "";
        } catch (Exception e) {
            log.error("Failed to open incident modal for user {}", userId, e);
            return buildEphemeralResponse("Failed to open incident form. Please try again.");
        }
    }

    private String handleMyIncidentsCommand(String userId, String triggerId) {
        if (!oauthService.isConnected(userId)) {
            return buildEphemeralResponse(CONNECT_FIRST_MSG);
        }

        try {
            incidentModalService.openMyIncidentsModal(userId, triggerId);
            return "";
        } catch (Exception e) {
            log.error("Failed to open incidents modal for user {}", userId, e);
            return buildEphemeralResponse("Failed to load incidents. Please try again.");
        }
    }

    private String handleAssignedIncidentsCommand(String userId, String triggerId) {
        if (!oauthService.isConnected(userId)) {
            return buildEphemeralResponse(CONNECT_FIRST_MSG);
        }

        if (!slackProperties.agentFeaturesEnabled()) {
            return buildEphemeralResponse("This feature is not enabled.");
        }

        try {
            incidentModalService.openAssignedIncidentsModal(userId, triggerId);
            return "";
        } catch (Exception e) {
            log.error("Failed to open assigned incidents modal for user {}", userId, e);
            return buildEphemeralResponse("Failed to load assigned incidents. Please try again.");
        }
    }

    private String handleBlockActions(JsonNode payload) {
        JsonNode actions = payload.path("actions");
        if (actions.isEmpty()) return "";

        JsonNode action = actions.get(0);
        String actionId = action.path("action_id").asText();
        String userId = payload.path("user").path("id").asText();
        String triggerId = payload.path("trigger_id").asText();

        log.debug("Block action: {} from user {}", actionId, userId);

        return switch (actionId) {
            case "connect_from_home" -> {
                String teamId = payload.path("team").path("id").asText();
                String state = oauthService.generateOAuthState(userId, teamId);
                String oauthUrl = oauthService.buildOAuthUrl(state);
                slackClient.chatPostMessage(userId,
                        "Click the button below to connect your HILFE account:",
                        """
                        [{"type":"actions","elements":[{"type":"button","text":{"type":"plain_text","text":"🔗  Connect to HILFE","emoji":true},"url":"%s","style":"primary","action_id":"open_connect_url"}]}]
                        """.formatted(oauthUrl).strip());
                yield "";
            }
            case "create_incident" -> {
                incidentModalService.openCreateIncidentModal(userId, triggerId);
                yield "";
            }
            case "view_my_incidents" -> {
                incidentModalService.openMyIncidentsModal(userId, triggerId);
                yield "";
            }
            case "view_assigned_incidents" -> {
                incidentModalService.openAssignedIncidentsModal(userId, triggerId);
                yield "";
            }
            case "category_select" -> {
                try {
                    incidentModalService.handleCategorySelection(payload);
                } catch (Exception e) {
                    log.error("Failed to handle category selection", e);
                }
                yield "";
            }
            default -> {
                if (actionId.startsWith("view_incident_")) {
                    yield "";
                }
                if (actionId.startsWith("toggle_notif_")) {
                    handleNotificationToggle(userId, actionId);
                    yield "";
                }
                log.debug("Unhandled action: {}", actionId);
                yield "";
            }
        };
    }

    private String handleViewSubmission(JsonNode payload) {
        String callbackId = payload.path("view").path("callback_id").asText();
        String userId = payload.path("user").path("id").asText();

        log.debug("View submission: {} from user {}", callbackId, userId);

        return switch (callbackId) {
            case "create_incident" -> incidentModalService.handleCreateIncidentSubmission(payload);
            case "notification_settings" -> "";
            default -> {
                log.debug("Unhandled callback_id: {}", callbackId);
                yield "";
            }
        };
    }

    private String handleViewClosed(JsonNode payload) {
        // Just log for now
        String callbackId = payload.path("view").path("callback_id").asText();
        log.debug("View closed: {}", callbackId);
        return "";
    }

    private String buildEphemeralResponse(String text) {
        return """
                {
                    "response_type": "ephemeral",
                    "text": "%s"
                }
                """.formatted(text.replace("\"", "\\\""));
    }
}
