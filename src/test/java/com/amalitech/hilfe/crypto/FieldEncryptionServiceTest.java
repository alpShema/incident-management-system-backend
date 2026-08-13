package com.amalitech.hilfe.crypto;

import com.amalitech.hilfe.config.EncryptionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class FieldEncryptionServiceTest {

    private static final String VALID_KEY = "super-secret-field-key-that-is-long-enough!";

    private FieldEncryptionService service(String fieldKey) {
        return new FieldEncryptionService(new EncryptionProperties(null, fieldKey));
    }

    @Test
    void constructor_throwsWhenKeyNull() {
        assertThatThrownBy(() -> service(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIELD_ENCRYPTION_KEY");
    }

    @Test
    void constructor_throwsWhenKeyBlank() {
        assertThatThrownBy(() -> service("   "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIELD_ENCRYPTION_KEY");
    }

    @Test
    void constructor_throwsWhenKeyTooShort() {
        assertThatThrownBy(() -> service("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIELD_ENCRYPTION_KEY");
    }

    @Test
    void encrypt_decrypt_roundtrip() {
        var svc = service(VALID_KEY);
        String plaintext = "Incident title with sensitive detail";
        String encrypted = svc.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(svc.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertThat(service(VALID_KEY).encrypt(null)).isNull();
    }

    @Test
    void decrypt_null_returnsNull() {
        assertThat(service(VALID_KEY).decrypt(null)).isNull();
    }

    @Test
    void decrypt_legacyPlaintext_passesThroughUnchanged() {
        String legacyPlaintext = "written before encryption existed";
        assertThat(service(VALID_KEY).decrypt(legacyPlaintext)).isEqualTo(legacyPlaintext);
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        var svc = service(VALID_KEY);
        String encrypted = svc.encrypt("some ticket content");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "abcd";

        assertThatThrownBy(() -> svc.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
