package com.amalitech.hilfe.slack.controller;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.SlackConstants;
import com.amalitech.hilfe.slack.security.SlackRateLimiter;
import com.amalitech.hilfe.slack.security.SlackSignatureValidator;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.amalitech.hilfe.slack.service.SlackEventHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/slack")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
@Tag(name = "Slack Events", description = "Endpoints for Slack event subscriptions, commands, and interactions")
public class SlackController {

    private final SlackSignatureValidator signatureValidator;
    private final SlackRateLimiter rateLimiter;
    private final SlackEventHandler eventHandler;
    private final SlackAuditLogService auditLogService;
    private final SlackProperties slackProperties;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("java:S6863") // Slack requires HTTP 200 even on errors, or it retries the request
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Slack Events API endpoint", description = "Receives events from Slack (app_home_opened, message.im, etc.)")
    public ResponseEntity<String> handleEvents(
            @RequestBody String rawBody,
            @RequestHeader("X-Slack-Signature") String signature,
            @RequestHeader("X-Slack-Request-Timestamp") String timestamp
    ) {
        if (!signatureValidator.isValid(signature, timestamp, rawBody)) {
            log.warn("Invalid Slack signature on /events");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SlackConstants.INVALID_SIGNATURE);
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String type = payload.path("type").asText();

            // Handle URL verification challenge (Slack handshake)
            if ("url_verification".equals(type)) {
                String challenge = payload.path("challenge").asText();
                log.info("Slack URL verification challenge received");
                return ResponseEntity.ok(challenge);
            }

            // Handle event callbacks
            if ("event_callback".equals(type)) {
                JsonNode event = payload.path("event");
                String eventType = event.path("type").asText();
                String userId = event.path("user").asText();

                if (!rateLimiter.tryAcquire(userId)) {
                    log.warn("Rate limit exceeded for user {} on event {}", userId, eventType);
                    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Rate limit exceeded");
                }

                log.debug("Processing Slack event: {} for user {}", eventType, userId);
                eventHandler.handleEvent(eventType, event);
            }

            return ResponseEntity.ok("");
        } catch (Exception e) {
            log.error("Error processing Slack event", e);
            return ResponseEntity.ok(""); // Always return 200 to Slack
        }
    }

    @SuppressWarnings("java:S6863") // Slack requires HTTP 200 even on errors, or it retries the request
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Slack slash commands endpoint", description = "Handles /hilfe slash commands")
    public ResponseEntity<String> handleCommands(
            @RequestBody String rawBody,
            @RequestHeader("X-Slack-Signature") String signature,
            @RequestHeader("X-Slack-Request-Timestamp") String timestamp
    ) {
        if (!signatureValidator.isValid(signature, timestamp, rawBody)) {
            log.warn("Invalid Slack signature on /commands");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SlackConstants.INVALID_SIGNATURE);
        }

        try {
            Map<String, String> params = parseFormData(rawBody);
            String command = params.get("command");
            String text = params.getOrDefault("text", "").trim();
            String userId = params.get("user_id");
            String triggerId = params.get("trigger_id");
            String teamId = params.get("team_id");

            if (!rateLimiter.tryAcquire(userId)) {
                return ResponseEntity.ok(buildSlackMessage(
                        "Rate limit exceeded. Please wait a moment and try again.",
                        true
                ));
            }

            log.info("Slack command: {} {} from user {}", command, text, userId);

            String response = eventHandler.handleCommand(text, userId, triggerId, teamId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing Slack command", e);
            return ResponseEntity.ok(buildSlackMessage(
                    "An error occurred while processing your command. Please try again.",
                    true
            ));
        }
    }

    @SuppressWarnings("java:S6863") // Slack requires HTTP 200 even on errors, or it retries the request
    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Slack interactivity endpoint", description = "Handles button clicks, modal submissions, dropdown selections")
    public ResponseEntity<String> handleInteractions(
            HttpServletRequest request,
            @RequestHeader("X-Slack-Signature") String signature,
            @RequestHeader("X-Slack-Request-Timestamp") String timestamp
    ) {
        String rawBody;
        try {
            rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read Slack interactions request body", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request body");
        }

        if (!signatureValidator.isValid(signature, timestamp, rawBody)) {
            log.warn("Invalid Slack signature on /interactions");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(SlackConstants.INVALID_SIGNATURE);
        }

        try {
            // Interactions come as payload=<url-encoded-json>
            String payloadJson = extractPayload(rawBody);
            JsonNode payload = objectMapper.readTree(payloadJson);

            String type = payload.path("type").asText();
            String userId = payload.path("user").path("id").asText();

            if (!rateLimiter.tryAcquire(userId)) {
                return ResponseEntity.ok(buildSlackMessage(
                        "Rate limit exceeded. Please wait a moment.",
                        true
                ));
            }

            log.debug("Processing Slack interaction: {} from user {}", type, userId);

            String response = eventHandler.handleInteraction(type, payload);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing Slack interaction", e);
            return ResponseEntity.ok("");
        }
    }

    private Map<String, String> parseFormData(String body) {
        Map<String, String> params = new java.util.HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = idx < pair.length() - 1
                        ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8)
                        : "";
                params.put(key, value);
            }
        }
        return params;
    }

    private String extractPayload(String body) {
        if (body.startsWith("payload=")) {
            return URLDecoder.decode(body.substring(8), StandardCharsets.UTF_8);
        }
        return body;
    }

    private String buildSlackMessage(String text, boolean ephemeral) {
        return """
                {
                    "response_type": "%s",
                    "text": "%s"
                }
                """.formatted(ephemeral ? "ephemeral" : "in_channel", text);
    }
}
