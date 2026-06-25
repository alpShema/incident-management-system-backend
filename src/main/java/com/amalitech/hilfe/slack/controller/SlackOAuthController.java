package com.amalitech.hilfe.slack.controller;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.SlackConstants;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.amalitech.hilfe.slack.security.SlackRateLimiter;
import com.amalitech.hilfe.slack.security.SlackSignatureValidator;
import com.amalitech.hilfe.slack.service.AppHomeService;
import com.amalitech.hilfe.slack.service.SlackAuditLogService;
import com.amalitech.hilfe.slack.service.SlackOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/slack/oauth")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
@Tag(name = "Slack OAuth", description = "OAuth flow for connecting Slack users to HILFE accounts")
public class SlackOAuthController {

    private static final String SLACK_USER_ID_PARAM = "slack_user_id=";
    private static final String ERROR_KEY = "error";
    private static final String WELCOME_MESSAGE = """
            🎉 *You're now connected to HILFE!*

            Your Slack account is linked to your HILFE account. You can now:
            • Create incidents with `/hilfe new`
            • View your tickets with `/hilfe my`
            • Receive real-time DM notifications for all incident updates

            Open the *Home* tab to manage your notification preferences.""";

    private final SlackOAuthService oauthService;
    private final SlackAuditLogService auditLogService;
    private final SlackClient slackClient;
    private final AppHomeService appHomeService;
    private final SlackRateLimiter rateLimiter;
    private final SlackSignatureValidator signatureValidator;
    private final SlackProperties slackProperties;

    @GetMapping("/authorize")
    @Operation(summary = "OAuth authorization page", description = "Renders the authorization page that redirects to ARMS SSO")
    public ResponseEntity<String> authorize(
            @RequestParam("state") String state
    ) {
        if (!oauthService.isValidState(state)) {
            log.warn("Invalid or expired OAuth state on authorize page: {}", state);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildErrorPage("Invalid or expired session. Please try connecting again from Slack."));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildAuthorizePage(state));
    }

    @GetMapping("/start")
    @Operation(summary = "Start OAuth flow", description = "Initiates the OAuth flow for a Slack user")
    public ResponseEntity<Map<String, String>> startOAuth(
            @RequestParam("slack_user_id") String slackUserId,
            @RequestParam("team_id") String teamId
    ) {
        if (!rateLimiter.tryAcquire(slackUserId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(ERROR_KEY, "Rate limit exceeded. Please try again later."));
        }

        String state = oauthService.generateOAuthState(slackUserId, teamId);
        String oauthUrl = oauthService.buildOAuthUrl(state);

        log.info("OAuth flow started for Slack user: {}", slackUserId);

        return ResponseEntity.ok(Map.of(
                "oauth_url", oauthUrl,
                "state", state
        ));
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback", description = "Handles the OAuth callback after ARMS SSO authentication")
    public ResponseEntity<String> handleCallback(
            @RequestParam("arms_token") String armsToken,
            @RequestParam("state") String state
    ) {
        var oauthState = oauthService.validateAndConsumeState(state);
        if (oauthState.isEmpty()) {
            log.warn("Invalid or expired OAuth state: {}", state);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildErrorPage("Invalid or expired session. Please try connecting again."));
        }

        try {
            var mapping = oauthService.connectUser(
                    armsToken,
                    oauthState.get().slackUserId(),
                    oauthState.get().slackTeamId()
            );

            String slackUserId = oauthState.get().slackUserId();
            log.info("OAuth successful for Slack user {} -> HILFE user {}",
                    slackUserId, mapping.getHilfeUserId());

            sendPostConnectionNotifications(slackUserId);

            String successUrl = slackProperties.frontendSuccessUrl();
            if (successUrl == null || successUrl.isBlank()) {
                log.error("SLACK_FRONTEND_SUCCESS_URL is not configured — cannot redirect after OAuth");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.TEXT_HTML)
                        .body(buildErrorPage("Configuration error: success redirect URL is not set."));
            }

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, successUrl)
                    .build();
        } catch (Exception e) {
            log.error("OAuth callback failed for state {}", state, e);

            auditLogService.log(
                    "AUTH_FAILED",
                    oauthState.get().slackUserId(),  // slackUserId may not be set yet if connectUser threw
                    null,
                    "OAUTH",
                    null,
                    Map.of(ERROR_KEY, e.getMessage())
            );

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildErrorPage("Authentication failed: " + e.getMessage()));
        }
    }

    @PostMapping("/disconnect")
    @Operation(summary = "Disconnect Slack account", description = "Disconnects a Slack user from their HILFE account")
    public ResponseEntity<Map<String, Object>> disconnect(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-Slack-Signature", required = false) String signature,
            @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp
    ) {
        if (signature == null || timestamp == null || !signatureValidator.isValid(signature, timestamp, rawBody)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(ERROR_KEY, SlackConstants.INVALID_SIGNATURE));
        }

        // Parse the slack_user_id from the body (form-encoded or JSON)
        String slackUserId = extractSlackUserId(rawBody);
        if (slackUserId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of(ERROR_KEY, "Missing slack_user_id"));
        }

        if (!rateLimiter.tryAcquire(slackUserId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(ERROR_KEY, "Rate limit exceeded"));
        }

        oauthService.disconnectUser(slackUserId);
        log.info("Slack user {} disconnected", slackUserId);

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Successfully disconnected from HILFE"
        ));
    }

    private void sendPostConnectionNotifications(String slackUserId) {
        try {
            slackClient.chatPostMessage(slackUserId, WELCOME_MESSAGE);
        } catch (Exception e) {
            log.warn("Failed to send welcome message to Slack user {}", slackUserId, e);
        }
        try {
            appHomeService.publishAppHome(slackUserId);
        } catch (Exception e) {
            log.warn("Failed to publish app home for Slack user {} after OAuth", slackUserId, e);
        }
    }

    private String extractSlackUserId(String body) {
        // Handle both JSON and form-encoded
        if (body.contains(SLACK_USER_ID_PARAM)) {
            String[] params = body.split("&");
            for (String param : params) {
                if (param.startsWith(SLACK_USER_ID_PARAM)) {
                    return param.substring(SLACK_USER_ID_PARAM.length());
                }
            }
        }
        // Try JSON parsing
        if (body.contains("\"slack_user_id\"")) {
            int start = body.indexOf("\"slack_user_id\"") + 16;
            int colonPos = body.indexOf(":", start);
            int quoteStart = body.indexOf("\"", colonPos) + 1;
            int quoteEnd = body.indexOf("\"", quoteStart);
            if (quoteEnd > quoteStart) {
                return body.substring(quoteStart, quoteEnd);
            }
        }
        return null;
    }

    private String buildAuthorizePage(String state) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Connect HILFE to Slack</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background: #f5f5f5; }
                        .card { background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); text-align: center; max-width: 420px; width: 100%; }
                        h1 { color: #4a154b; margin-bottom: 16px; }
                        p { color: #666; margin-bottom: 24px; line-height: 1.5; }
                        label { display: block; text-align: left; color: #444; margin-bottom: 8px; font-weight: 500; }
                        input[type="text"] { width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; box-sizing: border-box; margin-bottom: 20px; font-size: 14px; }
                        .btn { display: inline-block; background: #4a154b; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 500; border: none; cursor: pointer; font-size: 16px; }
                        .btn:hover { background: #611f69; }
                        .note { font-size: 12px; color: #888; margin-top: 20px; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>Connect HILFE to Slack</h1>
                        <p>Click the button below to authorize Slack to access your HILFE account. You will be redirected to the ARMS SSO login page.</p>
                        <form action="/slack/oauth/callback" method="GET">
                            <input type="hidden" name="state" value="%s">
                            <label for="arms_token">ARMS Token (for testing)</label>
                            <input type="text" id="arms_token" name="arms_token" placeholder="Paste your ARMS token here" required>
                            <button type="submit" class="btn">Connect to HILFE</button>
                        </form>
                        <p class="note">This is a test authorization page. In production, this redirects to ARMS SSO.</p>
                    </div>
                </body>
                </html>
                """.formatted(state);
    }

    private String buildErrorPage(String message) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Connection Failed</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; background: #f5f5f5; }
                        .card { background: white; padding: 40px; border-radius: 12px; box-shadow: 0 4px 24px rgba(0,0,0,0.1); text-align: center; max-width: 400px; }
                        h1 { color: #e01e5a; margin-bottom: 16px; }
                        p { color: #666; margin-bottom: 24px; }
                        .error { background: #fef2f2; color: #dc2626; padding: 12px; border-radius: 8px; margin-bottom: 24px; }
                        .btn { display: inline-block; background: #4a154b; color: white; padding: 12px 24px; border-radius: 8px; text-decoration: none; font-weight: 500; }
                    </style>
                </head>
                <body>
                    <div class="card">
                        <h1>Connection Failed</h1>
                        <div class="error">%s</div>
                        <p>Please close this window and try again from Slack.</p>
                        <a href="slack://open" class="btn">Return to Slack</a>
                    </div>
                </body>
                </html>
                """.formatted(message);
    }
}
