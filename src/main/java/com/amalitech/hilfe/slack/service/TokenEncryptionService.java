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

    public TokenEncryptionService(EncryptionProperties properties) {
        if (properties.key() == null || properties.key().isBlank()) {
            throw new IllegalStateException(
                    "ENCRYPTION_KEY must be set — refusing to start without Slack token encryption. " +
                    "Set the ENCRYPTION_KEY environment variable to a random secret of at least 32 characters.");
        }
        this.encryptor = Encryptors.text(properties.key(), "deadbeef");
        log.info("Token encryption enabled");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return plainText;
        }
        return encryptor.encrypt(plainText);
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isBlank()) {
            return encryptedText;
        }
        try {
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            log.error("Failed to decrypt token", e);
            return encryptedText;
        }
    }

    public boolean isEnabled() {
        return true;
    }
}
