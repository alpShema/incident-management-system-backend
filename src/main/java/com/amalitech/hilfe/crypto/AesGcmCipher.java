package com.amalitech.hilfe.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for individual field values. Deliberately dependency-free (no Spring) so
 * it can run both from {@link FieldEncryptionService} and from the Flyway {@code BaseJavaMigration}
 * that re-encrypts pre-existing rows, which executes before the Spring context exists.
 * <p>
 * Every encrypted value is stored as {@code "v1:" + base64(iv || ciphertext+tag)}. The prefix lets
 * callers distinguish ciphertext from not-yet-migrated legacy plaintext via {@link #isEncrypted}.
 */
public final class AesGcmCipher {

    public static final String VERSION_PREFIX = "v1:";

    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    // Only needs to be fixed and non-secret (unlike the GCM IV below, which must be random per
    // value) -- its job is making key derivation deterministic across app restarts and the data
    // migration, not hiding the passphrase. Deliberately distinct from TokenEncryptionService's
    // "deadbeef" salt so the two encryption paths never share derived key material.
    private static final byte[] KDF_SALT = "hilfe-field-encryption-v1".getBytes(StandardCharsets.UTF_8);
    private static final int KDF_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    private AesGcmCipher(SecretKeySpec key) {
        this.key = key;
    }

    public static AesGcmCipher fromPassphrase(String passphrase) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), KDF_SALT, KDF_ITERATIONS, KEY_LENGTH_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new AesGcmCipher(new SecretKeySpec(keyBytes, "AES"));
        } catch (GeneralSecurityException e) {
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
