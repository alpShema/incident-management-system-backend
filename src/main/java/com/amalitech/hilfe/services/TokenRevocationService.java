package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Session;
import com.amalitech.hilfe.repositories.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final SessionRepository sessionRepository;

    /**
     * Stores the JTI in the Session table to mark the refresh token as revoked.
     * Safe to call concurrently — a duplicate JTI (PK conflict) is silently ignored.
     */
    @Transactional
    public void revoke(String jti, String userId, Instant expiresAt) {
        try {
            Session session = Session.builder()
                    .id(jti)
                    .sid(jti)
                    .data(userId)
                    .expiresAt(expiresAt)
                    .build();
            sessionRepository.save(session);
            log.debug("Revoked refresh token jti={} for userId={}", jti, userId);
        } catch (DataIntegrityViolationException e) {
            log.debug("Refresh token jti={} already revoked, ignoring duplicate", jti);
        }
    }

    /**
     * Returns true if the JTI is null (legacy token with no JTI) or found in the revocation store.
     */
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return true;
        }
        return sessionRepository.existsById(jti);
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
}
