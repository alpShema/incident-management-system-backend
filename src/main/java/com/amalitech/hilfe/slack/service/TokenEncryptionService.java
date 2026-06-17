package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TokenEncryptionService {

    private final TextEncryptor encryptor;
    private final boolean enabled;

    public TokenEncryptionService(EncryptionProperties properties) {
        if (properties.key() != null && !properties.key().isBlank()) {
            this.encryptor = Encryptors.text(properties.key(), "deadbeef");
            this.enabled = true;
            log.info("Token encryption enabled");
        } else {
            this.encryptor = null;
            this.enabled = false;
            log.warn("Token encryption disabled - ENCRYPTION_KEY not set");
        }
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        if (!enabled) {
            log.warn("Encryption requested but not enabled - storing in plain text");
            return plainText;
        }
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }
        if (!enabled) {
            return encryptedText;
        }
        try {
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            log.error("Failed to decrypt token - may be stored in plain text", e);
            return encryptedText;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
