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

            Open the *Home* tab of the HILFE Slack app (click *HILFE* in your sidebar) to manage your notification preferences.""";

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
            return errorRedirect("session_expired");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildAuthorizePage(state));
    }

    @GetMapping("/callback")
    @Operation(summary = "OAuth callback", description = "Handles the OAuth callback after ARMS SSO authentication")
    public ResponseEntity<String> handleCallback(
            @RequestParam("arms_token") String armsToken,
            @RequestParam("state") String state
    ) {
        log.info("OAuth callback received: state={}", state);

        var oauthState = oauthService.validateAndConsumeState(state);
        if (oauthState.isEmpty()) {
            log.warn("OAuth callback: invalid or expired state={}", state);
            return errorRedirect("session_expired");
        }

        log.info("OAuth state valid: slackUserId={} teamId={}",
                oauthState.get().slackUserId(), oauthState.get().slackTeamId());

        try {
            var mapping = oauthService.connectUser(
                    armsToken,
                    oauthState.get().slackUserId(),
                    oauthState.get().slackTeamId()
            );

            String slackUserId = oauthState.get().slackUserId();
            log.info("OAuth connectUser succeeded: slackUserId={} hilfeUserId={}",
                    slackUserId, mapping.getHilfeUserId());

            sendPostConnectionNotifications(slackUserId);

            String successUrl = slackProperties.frontendSuccessUrl();
            log.info("OAuth redirect target: '{}'", successUrl);

            if (successUrl == null || successUrl.isBlank()) {
                log.error("SLACK_FRONTEND_SUCCESS_URL is not configured — cannot redirect");
                return errorRedirect("config_error");
            }

            log.info("OAuth complete — redirecting slackUserId={} to {}", slackUserId, successUrl);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, successUrl)
                    .build();
        } catch (Exception e) {
            log.error("OAuth callback failed: state={}", state, e);

            auditLogService.log(
                    "AUTH_FAILED",
                    oauthState.get().slackUserId(),  // slackUserId may not be set yet if connectUser threw
                    null,
                    "OAUTH",
                    null,
                    Map.of(ERROR_KEY, e.getMessage())
            );

            return errorRedirect("auth_failed");
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

    private ResponseEntity<String> errorRedirect(String reasonCode) {
        String errorUrl = slackProperties.frontendErrorUrl();
        if (errorUrl == null || errorUrl.isBlank()) {
            log.error("SLACK_FRONTEND_ERROR_URL is not configured — cannot redirect. Reason: {}", reasonCode);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Connection failed: " + reasonCode);
        }
        String separator = errorUrl.contains("?") ? "&" : "?";
        String location = errorUrl + separator + "reason=" + reasonCode;
        log.info("Redirecting to frontend error page: {}", location);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }
}
