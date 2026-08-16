package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Session;
import com.amalitech.hilfe.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final SessionRepository sessionRepository;

    /**
     * Stores a hash of the raw ARMS token in the Session table to mark it as revoked (logout).
     * ARMS tokens have no jti claim, so the token itself is hashed to form the row id.
     * Safe to call concurrently — a duplicate hash (PK conflict) is silently ignored.
     */
    @Transactional
    public void revoke(String rawArmsToken, String userId, Instant expiresAt) {
        String hash = hash(rawArmsToken);
        try {
            Session session = Session.builder()
                    .id(hash)
                    .sid(hash)
                    .data(userId)
                    .expiresAt(expiresAt)
                    .build();
            sessionRepository.save(session);
            log.debug("Revoked ARMS token for userId={}", userId);
        } catch (DataIntegrityViolationException e) {
            log.debug("ARMS token already revoked for userId={}, ignoring duplicate", userId);
        }
    }

    /**
     * Returns true if the token is null/blank (nothing to trust) or its hash is found in the
     * revocation store.
     */
    @Transactional(readOnly = true)
    public boolean isRevoked(String rawArmsToken) {
        if (rawArmsToken == null || rawArmsToken.isBlank()) {
            return true;
        }
        return sessionRepository.existsById(hash(rawArmsToken));
    }

    /**
     * Removes expired rows nightly to prevent unbounded table growth.
     * Runs at 02:00 UTC every day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = sessionRepository.deleteByExpiresAtBefore(Instant.now());
        log.info("Purged {} expired revoked token records", deleted);
    }

    private static String hash(String rawArmsToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawArmsToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
