package com.amalitech.hilfe.slack;

import com.amalitech.hilfe.config.EncryptionProperties;
import com.amalitech.hilfe.slack.service.TokenEncryptionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenEncryptionServiceTest {

    private static final String VALID_KEY = "super-secret-key-that-is-long-enough!";

    private TokenEncryptionService service(String key) {
        return new TokenEncryptionService(new EncryptionProperties(key));
    }

    @Test
    void constructor_throwsWhenKeyNull() {
        assertThatThrownBy(() -> service(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENCRYPTION_KEY");
    }

    @Test
    void constructor_throwsWhenKeyBlank() {
        assertThatThrownBy(() -> service("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ENCRYPTION_KEY");
    }

    @Test
    void isEnabled_alwaysReturnsTrue() {
        assertThat(service(VALID_KEY).isEnabled()).isTrue();
    }

    @Test
    void encrypt_decrypt_roundtrip() {
        var svc = service(VALID_KEY);
        String plaintext = "slack-bot-token-xoxb-123456";
        String encrypted = svc.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(svc.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDistinctCiphertextsEachCall() {
        var svc = service(VALID_KEY);
        String c1 = svc.encrypt("same-value");
        String c2 = svc.encrypt("same-value");
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertThat(service(VALID_KEY).encrypt(null)).isNull();
    }

    @Test
    void encrypt_blank_returnsBlank() {
        String blank = "   ";
        assertThat(service(VALID_KEY).encrypt(blank)).isEqualTo(blank);
    }

    @Test
    void decrypt_null_returnsNull() {
        assertThat(service(VALID_KEY).decrypt(null)).isNull();
    }

    @Test
    void decrypt_blank_returnsBlank() {
        String blank = "   ";
        assertThat(service(VALID_KEY).decrypt(blank)).isEqualTo(blank);
    }

    @Test
    void decrypt_corruptCiphertext_returnsInput() {
        String corrupt = "not-valid-encrypted-data";
        assertThat(service(VALID_KEY).decrypt(corrupt)).isEqualTo(corrupt);
    }
}
