package com.amalitech.hilfe.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for individual field values. Deliberately dependency-free (no Spring) so
 * it can run both from {@link FieldEncryptionService} and from the Flyway Java migration that
 * re-encrypts pre-existing rows, which executes before the Spring context exists.
 * <p>
 * Every encrypted value is stored as {@code "v1:" + base64(iv || ciphertext+tag)}. The prefix lets
 * callers distinguish ciphertext from not-yet-migrated legacy plaintext via {@link #isEncrypted}.
 */
public final class AesGcmCipher {

    public static final String VERSION_PREFIX = "v1:";

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    private AesGcmCipher(SecretKeySpec key) {
        this.key = key;
    }

    // No PBKDF2/salt here: the passphrase itself is required (by FieldEncryptionService) to be a
    // random secret of at least 32 characters, not a human-memorized password -- so there's no
    // low-entropy input to defend against dictionary/rainbow-table attacks with a salt or
    // iteration count. A plain SHA-256 hash maps that passphrase onto a 256-bit AES key.
    public static AesGcmCipher fromPassphrase(String passphrase) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] keyBytes = digest.digest(passphrase.getBytes(StandardCharsets.UTF_8));
            return new AesGcmCipher(new SecretKeySpec(keyBytes, "AES"));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to derive field encryption key", e);
        }
    }

    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(VERSION_PREFIX);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /** Decrypts a {@code v1:}-prefixed payload. Callers must check {@link #isEncrypted} first. */
    public String decrypt(String stored) {
        if (!isEncrypted(stored)) {
            throw new IllegalArgumentException("Value is not a recognized encrypted payload");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(VERSION_PREFIX.length()));
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload, 0, GCM_IV_LENGTH_BYTES));
            byte[] plaintext = cipher.doFinal(payload, GCM_IV_LENGTH_BYTES, payload.length - GCM_IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Field decryption failed -- wrong key or tampered data", e);
        }
    }
}
