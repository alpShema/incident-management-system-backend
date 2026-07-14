package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.slack.security.SlackSignatureValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SlackSignatureValidatorTest {

    private static final String SIGNING_SECRET = "test-slack-signing-secret";
    private static final Instant FIXED_NOW = Instant.parse("2026-06-30T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private SlackSignatureValidator validator;
    private SlackProperties enabledProps;

    @BeforeEach
    void setUp() {
        enabledProps = new SlackProperties(
                true, SIGNING_SECRET, "token", "client-id", "client-secret",
                "https://api.test/callback", "https://connect.test", null, null, "app-id",
                true, true, new SlackProperties.RateLimit(60, 10));
        validator = new SlackSignatureValidator(enabledProps, FIXED_CLOCK);
    }

    private String computeSignature(String timestamp, String body) throws Exception {
        String base = "v0:" + timestamp + ":" + body;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(base.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder("v0=");
        for (byte b : hash) {
            String h = Integer.toHexString(0xff & b);
            if (h.length() == 1) hex.append('0');
            hex.append(h);
        }
        return hex.toString();
    }

    @Test
    void isValid_correctSignature_returnsTrue() throws Exception {
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        String body = "payload=test&action=click";
        assertThat(validator.isValid(computeSignature(timestamp, body), timestamp, body)).isTrue();
    }

    @Test
    void isValid_wrongSignature_returnsFalse() {
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        assertThat(validator.isValid("v0=wrongsignature", timestamp, "body")).isFalse();
    }

    @Test
    void isValid_timestampTooOld_returnsFalse() throws Exception {
        String old = String.valueOf(FIXED_NOW.getEpochSecond() - 400);
        String body = "payload=old";
        assertThat(validator.isValid(computeSignature(old, body), old, body)).isFalse();
    }

    @Test
    void isValid_futureTimestamp_returnsFalse() throws Exception {
        String future = String.valueOf(FIXED_NOW.getEpochSecond() + 400);
        String body = "payload=future";
        assertThat(validator.isValid(computeSignature(future, body), future, body)).isFalse();
    }

    @Test
    void isValid_nullSignature_returnsFalse() {
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        assertThat(validator.isValid(null, timestamp, "body")).isFalse();
    }

    @Test
    void isValid_nullTimestamp_returnsFalse() {
        assertThat(validator.isValid("v0=abc", null, "body")).isFalse();
    }

    @Test
    void isValid_nullBody_returnsFalse() {
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        assertThat(validator.isValid("v0=abc", timestamp, null)).isFalse();
    }

    @Test
    void isValid_nonNumericTimestamp_returnsFalse() {
        assertThat(validator.isValid("v0=abc", "not-a-number", "body")).isFalse();
    }

    @Test
    void isValid_whenSlackDisabled_returnsFalse() throws Exception {
        SlackProperties disabled = new SlackProperties(
                false, SIGNING_SECRET, "token", "id", "secret",
                "https://redirect", "https://connect", null, null, "app-id",
                true, true, new SlackProperties.RateLimit(60, 10));
        var disabledValidator = new SlackSignatureValidator(disabled, FIXED_CLOCK);
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        String body = "payload=test";
        assertThat(disabledValidator.isValid(computeSignature(timestamp, body), timestamp, body)).isFalse();
    }
}
