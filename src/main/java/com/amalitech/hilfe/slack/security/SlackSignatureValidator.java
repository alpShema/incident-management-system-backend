package com.amalitech.hilfe.slack.security;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.exception.SlackClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Slf4j
public class SlackSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String VERSION = "v0";
    private static final long MAX_TIMESTAMP_DIFF_SECONDS = 300; // 5 minutes

    private final SlackProperties slackProperties;
    private final Clock clock;

    @Autowired
    public SlackSignatureValidator(SlackProperties slackProperties) {
        this(slackProperties, Clock.systemUTC());
    }

    public SlackSignatureValidator(SlackProperties slackProperties, Clock clock) {
        this.slackProperties = slackProperties;
        this.clock = clock;
    }

    public boolean isValid(String signature, String timestamp, String body) {
        if (!slackProperties.enabled()) {
            log.warn("Slack signature validation skipped - Slack is disabled");
            return false;
        }

        if (signature == null || timestamp == null || body == null) {
            log.warn("Slack signature validation failed: missing parameters");
            return false;
        }

        if (!isTimestampValid(timestamp)) {
            log.warn("Slack signature validation failed: timestamp too old or in future");
            return false;
        }

        String expectedSignature = computeSignature(timestamp, body);
        boolean valid = secureCompare(signature, expectedSignature);

        if (!valid) {
            String baseString = VERSION + ":" + timestamp + ":" + body;
            log.warn("Slack signature validation failed: signature mismatch. received={}, expected={}, bodyLength={}, timestamp={}",
                    signature, expectedSignature, body.length(), timestamp);
            log.debug("Slack signature validation base string: {}", baseString.substring(0, Math.min(baseString.length(), 1000)));
        }

        return valid;
    }

    private boolean isTimestampValid(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = Instant.now(clock).getEpochSecond();
            long diff = Math.abs(currentTime - requestTime);
            return diff <= MAX_TIMESTAMP_DIFF_SECONDS;
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format: {}", timestamp);
            return false;
        }
    }

    private String computeSignature(String timestamp, String body) {
        String baseString = VERSION + ":" + timestamp + ":" + body;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(
                    slackProperties.signingSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256
            );
            mac.init(secretKey);
            byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            return VERSION + "=" + bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute HMAC signature", e);
            throw new SlackClientException("Failed to compute Slack signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static boolean secureCompare(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
