package com.amalitech.hilfe.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AesGcmCipherTest {

    private static final String PASSPHRASE = "super-secret-field-key-that-is-long-enough!";

    private AesGcmCipher cipher() {
        return AesGcmCipher.fromPassphrase(PASSPHRASE);
    }

    @Test
    void encrypt_decrypt_roundtrip() {
        AesGcmCipher cipher = cipher();
        String plaintext = "Server room is overheating";

        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).startsWith(AesGcmCipher.VERSION_PREFIX).isNotEqualTo(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDistinctCiphertextsForSamePlaintext() {
        AesGcmCipher cipher = cipher();
        String c1 = cipher.encrypt("same-value");
        String c2 = cipher.encrypt("same-value");
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void isEncrypted_recognizesVersionPrefix() {
        AesGcmCipher cipher = cipher();
        assertThat(AesGcmCipher.isEncrypted(cipher.encrypt("hello"))).isTrue();
        assertThat(AesGcmCipher.isEncrypted("plain legacy text")).isFalse();
        assertThat(AesGcmCipher.isEncrypted(null)).isFalse();
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        AesGcmCipher cipher = cipher();
        String encrypted = cipher.encrypt("do not tamper with me");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "abcd";

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_wrongKey_throws() {
        String encrypted = cipher().encrypt("secret incident details");
        AesGcmCipher wrongKeyCipher = AesGcmCipher.fromPassphrase("a-completely-different-passphrase!!");

        assertThatThrownBy(() -> wrongKeyCipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_nonEncryptedValue_throws() {
        AesGcmCipher cipher = cipher();
        assertThatThrownBy(() -> cipher.decrypt("not-a-v1-payload"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
