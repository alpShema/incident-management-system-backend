package com.amalitech.hilfe.crypto;

import com.amalitech.hilfe.config.EncryptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class FieldEncryptionService {

    private static final int MIN_KEY_LENGTH = 32;

    private final AesGcmCipher cipher;

    public FieldEncryptionService(EncryptionProperties properties) {
        String fieldKey = properties.fieldKey();
        if (fieldKey == null || fieldKey.isBlank() || fieldKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "FIELD_ENCRYPTION_KEY must be set — refusing to start without ticket-content encryption. " +
                    "Set the FIELD_ENCRYPTION_KEY environment variable to a random secret of at least " +
                    MIN_KEY_LENGTH + " characters.");
        }
        this.cipher = AesGcmCipher.fromPassphrase(fieldKey);
        log.info("Field-level ticket content encryption enabled");
    }

    public String encrypt(String plaintext) {
        return plaintext == null ? null : cipher.encrypt(plaintext);
    }

    // Un-prefixed values are legacy rows the V80 data migration hasn't reached yet (or, on a
    // freshly-seeded local DB, rows inserted before this feature existed) -- passed through as-is
    // rather than treated as corrupt, so a partially-migrated deploy never breaks reads. Only a
    // v1:-prefixed value that fails to decrypt (wrong key, tampered ciphertext) is a real error.
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!AesGcmCipher.isEncrypted(stored)) {
            log.warn("Encountered un-migrated plaintext value while decrypting a ticket-content field");
            return stored;
        }
        return cipher.decrypt(stored);
    }
}
